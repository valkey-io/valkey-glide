import asyncio
from typing import List, Tuple

from glide import (
    AllNodes,
    ClosingError,
    GlideClusterClient,
    GlideClusterClientConfiguration,
    InfoSection,
    Logger,
    LogLevel,
    NodeAddress,
    RequestError,
)


async def create_client(
    nodes_list: List[Tuple[str, int]] = [("localhost", 47243)]
) -> GlideClusterClient:
    """
    Creates and returns a GlideClusterClient instance.

    This function initializes a GlideClusterClient with the provided list of nodes.
    In cluster mode, add the address of any node; the client will automatically
    discover all nodes in the cluster.


    Args:
        nodes_list (List[Tuple[str, int]]): A list of tuples where each tuple
            contains a host (str) and port (int). Defaults to [("localhost", 6379)].

    Returns:
        GlideClusterClient: An instance of GlideClusterClient connected to the specified nodes.
    """
    addresses = [NodeAddress(host, port) for host, port in nodes_list]
    # Check `GlideClusterClientConfiguration` for additional options.
    config = GlideClusterClientConfiguration(
        addresses=addresses,
        client_name="test_cluster_client",
        # Enable this field if the servers are configured with TLS.
        # use_tls=True
    )
    return await GlideClusterClient.create(config)


async def app_logic(client: GlideClusterClient):
    """
    Executes the main logic of the application, performing basic operations
    such as SET, GET, PING, and INFO REPLICATION using the provided GlideClusterClient.

    Args:
        client (GlideClusterClient): An instance of GlideClusterClient.
    """
    # Send SET and GET
    set_response = await client.set("foo", "bar")
    Logger.log(LogLevel.INFO, "app", f"Set response is = {set_response!r}")

    get_response = await client.get("foo")
    assert isinstance(get_response, bytes)
    Logger.log(LogLevel.INFO, "app", f"Get response is = {get_response.decode()!r}")

    # Send PING to all primaries (according to Redis's PING request_policy)
    pong = await client.ping()
    Logger.log(LogLevel.INFO, "app", f"PING response is = {pong}")

    # Send INFO REPLICATION with routing option to all nodes
    info_repl_resps = await client.info([InfoSection.REPLICATION], AllNodes())
    Logger.log(
        LogLevel.INFO,
        "app",
        f"INFO REPLICATION responses from all nodes are=\n{info_repl_resps!r}",
    )


async def exec_app_logic():
    """
    Executes the application logic with exception handling.
    """
    while True:
        try:
            client = await create_client()
            return await app_logic(client)
        except asyncio.CancelledError:
            raise
        except ClosingError as e:
            # If the error message contains "NOAUTH", raise the exception
            # because it indicates a critical authentication issue.
            if "NOAUTH" in str(e):
                Logger.log(
                    LogLevel.ERROR,
                    "glide",
                    f"Authentication error encountered: {e}",
                )
                raise e
            Logger.log(
                LogLevel.WARN,
                "glide",
                f"Client has closed and needs to be re-created: {e}",
            )
        except RequestError as e:
            Logger.log(LogLevel.ERROR, "glide", f"RequestError encountered: {e}")
            raise e
        except Exception as e:
            Logger.log(LogLevel.ERROR, "glide", f"Unexpected error: {e}")
            raise e
        finally:
            try:
                await client.close()
            except Exception as e:
                Logger.log(
                    LogLevel.WARN,
                    "glide",
                    f"Error encountered while closing the client: {e}",
                )


def main():
    Logger.set_logger_config(LogLevel.INFO)
    # Optional - set the logger to write to a file
    # Logger.set_logger_config(LogLevel.INFO, file)
    asyncio.run(exec_app_logic())


if __name__ == "__main__":
    main()
