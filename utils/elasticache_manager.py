#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import argparse
import logging
import os
import random
import socket
import string
import time

import boto3  # type: ignore[import-not-found]
from botocore.config import Config  # type: ignore[import-not-found]

LOG_LEVELS = {
    "critical": logging.CRITICAL,
    "error": logging.ERROR,
    "warn": logging.WARNING,
    "warning": logging.WARNING,
    "info": logging.INFO,
    "debug": logging.DEBUG,
}

DEFAULT_REGION = "us-east-1"
DEFAULT_INSTANCE_TYPE = "cache.t4g.small"
DEFAULT_ENGINE_VERSION = "9.0"
DEFAULT_SUBNET_GROUP = "client-testing"
DEFAULT_SECURITY_GROUP = os.environ.get("EC_SECURITY_GROUP", "")
DEFAULT_DESCRIPTION = "Valkey GLIDE integration test cluster"
POLL_INTERVAL_SECONDS = 10
MAX_WAIT_SECONDS = 35 * 60  # 35 minutes

ENGINE_LOG_REQUEST = {
    "LogType": "engine-log",
    "DestinationType": "cloudwatch-logs",
    "DestinationDetails": {
        "CloudWatchLogsDetails": {"LogGroup": "client-testing-engine-logs"}
    },
    "LogFormat": "text",
}

SLOW_LOG_REQUEST = {
    "LogType": "slow-log",
    "DestinationType": "cloudwatch-logs",
    "DestinationDetails": {
        "CloudWatchLogsDetails": {"LogGroup": "client-testing-slow-logs"}
    },
    "LogFormat": "text",
}


def generate_random_str(length: int) -> str:
    chars = string.ascii_lowercase + string.digits
    return "".join(random.choice(chars) for _ in range(length))


def init_client(region: str):
    config = Config(
        region_name=region,
        retries={"max_attempts": 10, "mode": "standard"},
    )
    return boto3.client("elasticache", config=config)


def get_engine(version: str) -> str:
    """Return 'valkey' for version >= 7.2, else 'redis'."""
    try:
        parts = [int(x) for x in version.split(".")[:2]]
        major, minor = parts[0], parts[1] if len(parts) > 1 else 0
        if (major, minor) >= (7, 2):
            return "valkey"
    except (ValueError, IndexError):
        pass
    return "redis"


def ensure_cluster_databases_parameter_group(
    client, engine_version: str, num_databases: int = 16
) -> str:
    """Create or reuse a parameter group with cluster-databases set.
    Only applicable for cluster-mode on Valkey 9.0+.
    Returns the parameter group name."""
    # Determine the base parameter group family for Valkey 9.0
    try:
        major = int(engine_version.split(".")[0])
    except (ValueError, IndexError):
        major = 9
    if major >= 9:
        family = f"valkey{major}"
    else:
        family = f"valkey{major}"

    pg_name = f"glide-cluster-databases-{family}"

    # Try to describe existing group first
    try:
        client.describe_cache_parameter_groups(CacheParameterGroupName=pg_name)
        logging.info(
            f"[elasticache_manager] Reusing existing parameter group '{pg_name}'"
        )
    except client.exceptions.CacheParameterGroupNotFoundFault:
        # Create it
        logging.info(
            f"[elasticache_manager] Creating parameter group '{pg_name}' with cluster-databases={num_databases}"
        )
        client.create_cache_parameter_group(
            CacheParameterGroupName=pg_name,
            CacheParameterGroupFamily=family,
            Description=f"GLIDE test parameter group: cluster-databases={num_databases}",
        )
        client.modify_cache_parameter_group(
            CacheParameterGroupName=pg_name,
            ParameterNameValues=[
                {"ParameterName": "cluster-databases", "ParameterValue": str(num_databases)},
            ],
        )
        logging.info(
            f"[elasticache_manager] Parameter group '{pg_name}' created with cluster-databases={num_databases}"
        )

    return pg_name


def start_cluster(
    name: str,
    cluster_mode: bool,
    num_replicas: int,
    num_shards: int,
    tls: bool,
    multi_az: bool,
    instance_type: str,
    engine_version: str,
    subnet_group: str,
    security_group: str,
    region: str,
) -> None:
    """Create one ElastiCache replication group and wait until available.
    Prints CLUSTER_NAME= and CLUSTER_ENDPOINT= to stdout on success."""
    if not security_group:
        raise ValueError(
            "[elasticache_manager] --security-group or EC_SECURITY_GROUP env var is required"
        )
    client = init_client(region)

    request = {
        "ReplicationGroupId": name,
        "ReplicationGroupDescription": DEFAULT_DESCRIPTION,
        "TransitEncryptionEnabled": tls,
        "MultiAZEnabled": multi_az,
        "CacheNodeType": instance_type,
        "Engine": get_engine(engine_version),
        "EngineVersion": engine_version,
        "CacheSubnetGroupName": subnet_group,
        "SecurityGroupIds": [security_group],
        "LogDeliveryConfigurations": [ENGINE_LOG_REQUEST, SLOW_LOG_REQUEST],
    }

    if cluster_mode:
        request["NumNodeGroups"] = num_shards
        request["ReplicasPerNodeGroup"] = num_replicas
        # On Valkey 9.0+, use a parameter group with cluster-databases=16
        # so that SELECT and multi-database features work in cluster mode.
        try:
            major = int(engine_version.split(".")[0])
        except (ValueError, IndexError):
            major = 0
        if major >= 9:
            pg_name = ensure_cluster_databases_parameter_group(client, engine_version)
            request["CacheParameterGroupName"] = pg_name
    else:
        request["NumCacheClusters"] = num_replicas + 1

    logging.info(
        f"[elasticache_manager] Creating replication group '{name}' (cluster_mode={cluster_mode}, version={engine_version})"
    )
    client.create_replication_group(**request)

    # Print cluster name immediately so callers can track it for cleanup
    # even if provisioning fails during polling or connectivity checks.
    print(f"CLUSTER_NAME={name}")

    # Poll, extract endpoint, and verify connectivity.
    # Wrap everything in a try/except so ANY failure triggers cleanup.
    try:
        deadline = time.time() + MAX_WAIT_SECONDS
        while time.time() < deadline:
            resp = client.describe_replication_groups(ReplicationGroupId=name)
            groups = resp.get("ReplicationGroups", [])
            if not groups:
                raise RuntimeError(
                    f"[elasticache_manager] Replication group '{name}' not found after creation"
                )
            status = groups[0].get("Status", "")
            logging.info(f"[elasticache_manager] '{name}' status: {status}")
            if status == "available":
                break
            time.sleep(POLL_INTERVAL_SECONDS)
        else:
            raise TimeoutError(
                f"[elasticache_manager] Timed out waiting for '{name}' to become available"
            )

        group = groups[0]
        if cluster_mode:
            endpoint = group.get("ConfigurationEndpoint", {})
        else:
            node_groups = group.get("NodeGroups", [])
            endpoint = node_groups[0].get("PrimaryEndpoint", {}) if node_groups else {}

        host = endpoint.get("Address", "")
        port = endpoint.get("Port", 6379)
        if not host:
            raise RuntimeError(
                f"[elasticache_manager] Could not determine endpoint for '{name}'"
            )

        endpoint_str = f"{host}:{port}"

        # Verify TCP connectivity before declaring success.
        # ElastiCache may report 'available' before the port is actually reachable.
        logging.info(
            f"[elasticache_manager] Verifying TCP connectivity to {host}:{port}..."
        )
        tcp_deadline = time.time() + 120  # 2 minutes to become reachable
        tcp_ok = False
        while time.time() < tcp_deadline:
            try:
                with socket.create_connection((host, port), timeout=5):
                    tcp_ok = True
                    break
            except (socket.timeout, ConnectionRefusedError, OSError):
                logging.info(
                    f"[elasticache_manager] Port {port} not yet reachable, retrying..."
                )
                time.sleep(5)
        if not tcp_ok:
            raise RuntimeError(
                f"[elasticache_manager] '{name}' is available per API but TCP connection to "
                f"{host}:{port} timed out after 2 minutes. Check VPC/subnet/security group configuration."
            )
        logging.info(
            f"[elasticache_manager] TCP connectivity to {host}:{port} confirmed."
        )
    except Exception:
        logging.error(
            f"[elasticache_manager] Provisioning failed for '{name}', attempting cleanup..."
        )
        stop_cluster(name, region)
        raise

    print(f"CLUSTER_ENDPOINT={endpoint_str}")
    logging.info(f"[elasticache_manager] '{name}' is available at {endpoint_str}")


def stop_cluster(name: str, region: str) -> None:
    """Delete one ElastiCache replication group.
    Treats already-deleted groups as success. Re-raises unexpected errors."""
    client = init_client(region)
    logging.info(f"[elasticache_manager] Deleting replication group '{name}'")
    try:
        client.delete_replication_group(
            ReplicationGroupId=name,
            RetainPrimaryCluster=False,
        )
        logging.info(f"[elasticache_manager] Delete request sent for '{name}'")
    except client.exceptions.ReplicationGroupNotFoundFault:
        logging.info(
            f"[elasticache_manager] '{name}' not found - already deleted or never created."
        )
    except Exception as e:
        logging.error(f"[elasticache_manager] Failed to delete '{name}': {e}")
        raise


def main():
    parser = argparse.ArgumentParser(description="ElastiCache manager tool")
    parser.add_argument(
        "-log",
        "--loglevel",
        dest="log",
        default="info",
        help="Logging level (default: %(default)s)",
    )
    subparsers = parser.add_subparsers(dest="action", help="Tool actions")

    # -- start --
    parser_start = subparsers.add_parser(
        "start", help="Create a new ElastiCache replication group"
    )
    parser_start.add_argument(
        "--cluster-mode",
        action="store_true",
        default=False,
        help="Enable cluster mode (CME). If not set, cluster-mode disabled (CMD) is created.",
    )
    parser_start.add_argument(
        "--name",
        default=None,
        help="Name prefix for the replication group. Falls back to NAME env var. A random suffix is appended.",
    )
    parser_start.add_argument(
        "-r",
        "--num-replicas",
        type=int,
        default=1,
        help="Number of replicas (CMD) or replicas per shard (CME) (default: %(default)s)",
    )
    parser_start.add_argument(
        "-n",
        "--num-shards",
        type=int,
        default=2,
        help="Number of shards, CME only (default: %(default)s)",
    )
    parser_start.add_argument(
        "--tls",
        action="store_true",
        default=False,
        help="Enable TLS (default: %(default)s)",
    )
    parser_start.add_argument(
        "--multi-az",
        action="store_true",
        default=False,
        help="Enable Multi-AZ (default: %(default)s)",
    )
    parser_start.add_argument(
        "--instance-type",
        default=DEFAULT_INSTANCE_TYPE,
        help="Cache node type (default: %(default)s)",
    )
    parser_start.add_argument(
        "--engine-version",
        default=DEFAULT_ENGINE_VERSION,
        help="Engine version (default: %(default)s)",
    )
    parser_start.add_argument(
        "--subnet-group",
        default=os.environ.get("EC_SUBNET_GROUP", DEFAULT_SUBNET_GROUP),
        help="Subnet group name (default: %(default)s)",
    )
    parser_start.add_argument(
        "--security-group",
        default=os.environ.get("EC_SECURITY_GROUP", DEFAULT_SECURITY_GROUP),
        help="Security group ID (default: %(default)s)",
    )
    parser_start.add_argument(
        "--region",
        default=os.environ.get("AWS_REGION", DEFAULT_REGION),
        help="AWS region (default: %(default)s)",
    )

    # -- stop --
    parser_stop = subparsers.add_parser(
        "stop", help="Delete an ElastiCache replication group"
    )
    parser_stop.add_argument(
        "--cluster-name",
        required=True,
        help="Name of the replication group to delete",
    )
    parser_stop.add_argument(
        "--region",
        default=os.environ.get("AWS_REGION", DEFAULT_REGION),
        help="AWS region (default: %(default)s)",
    )

    args = parser.parse_args()

    level = LOG_LEVELS.get(args.log.lower())
    if level is None:
        parser.error(f"Invalid log level: {args.log}")
    logging.basicConfig(level=level, format="%(message)s")

    if args.action == "start":
        prefix = args.name or os.environ.get("NAME")
        if not prefix:
            parser.error("--name or NAME env var is required")
        name = f"{prefix}-{generate_random_str(10)}".lower()
        start_cluster(
            name=name,
            cluster_mode=args.cluster_mode,
            num_replicas=args.num_replicas,
            num_shards=args.num_shards,
            tls=args.tls,
            multi_az=args.multi_az,
            instance_type=args.instance_type,
            engine_version=args.engine_version,
            subnet_group=args.subnet_group,
            security_group=args.security_group,
            region=args.region,
        )

    elif args.action == "stop":
        stop_cluster(name=args.cluster_name, region=args.region)

    else:
        parser.print_help()


if __name__ == "__main__":
    main()
