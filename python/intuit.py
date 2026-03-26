from time import time

from glide_sync import (
    GlideClusterClient,
    GlideClusterClientConfiguration,
    NodeAddress,
    ReadFrom,
    AdvancedGlideClusterClientConfiguration,
    TlsAdvancedConfiguration,
)
from glide_sync import Logger, LogLevel

addresses = [
    NodeAddress(
        host="crr-cluster.glide.cross.region",
        port=6379,
    )
]

Logger.init(level=LogLevel.DEBUG)
client_config = GlideClusterClientConfiguration(
    addresses,
    read_from=ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY,
    client_az="us-east-1b",
    use_tls=True,
    request_timeout=60,
    advanced_config=AdvancedGlideClusterClientConfiguration(
        tls_config=TlsAdvancedConfiguration(use_insecure_tls=True),
        refresh_topology_from_initial_nodes=True,
    ),
)

client = GlideClusterClient.create(client_config)

while True:
    client.set("test-key", "test-value")
    client.get("test-key")


print("Successfully connected to Intuit Glide cluster and performed operations.")
