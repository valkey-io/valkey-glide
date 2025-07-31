import asyncio
import json
from typing import List, Optional, Tuple, cast

from glide import ClosingError
from glide import ConnectionError as GlideConnectionError
from glide import (
    GlideClusterClient,
    GlideClusterClientConfiguration,
    Logger,
    LogLevel,
    NodeAddress,
    RequestError,
)
from glide import TimeoutError as GlideTimeoutError
from glide import glide_json


async def create_client(
    nodes_list: Optional[List[Tuple[str, int]]] = None,
) -> GlideClusterClient:
    """
    Creates and returns a GlideClusterClient instance.

    This function initializes a GlideClusterClient with the provided list of nodes.
    The nodes_list may contain the address of one or more cluster nodes, and the
    client will automatically discover all nodes in the cluster.

    Args:
        nodes_list (List[Tuple[str, int]]): A list of tuples where each tuple
            contains a host (str) and port (int). Defaults to [("localhost", 6379)].

    Returns:
        GlideClusterClient: An instance of GlideClusterClient connected to the discovered nodes.
    """
    if nodes_list is None:
        nodes_list = [("localhost", 6379)]
    addresses = [NodeAddress(host, port) for host, port in nodes_list]
    # Check `GlideClusterClientConfiguration` for additional options.
    config = GlideClusterClientConfiguration(
        addresses=addresses,
        client_name="test_cluster_client",
        # Set request timeout - recommended to configure based on your use case.
        request_timeout=500,
        # Enable this field if the servers are configured with TLS.
        # use_tls=True
    )
    return await GlideClusterClient.create(config)


async def app_logic(client: GlideClusterClient):
    """
    Executes the main logic of the application, performing basic operations
    such as JSON.SET and JSON.GET using the provided GlideClient.

    Args:
        client (GlideClusterClient): An instance of GlideClient.
    """

    json_value = {"a": 1.0, "b": 2}
    json_str = json.dumps(json_value)
    # Send SET and GET
    set_response = await glide_json.set(client, "key", "$", json_str)
    Logger.log(LogLevel.INFO, "app", f"Set response is = {set_response!r}")  # 'OK'

    get_response = cast(bytes, await glide_json.get(client, "key", "$"))
    Logger.log(
        LogLevel.INFO, "app", f"Get response is = {get_response.decode()!r}"
    )  # "[{\"a\":1.0,\"b\":2}]"


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
            else:
                Logger.log(
                    LogLevel.WARN,
                    "glide",
                    f"Client has closed and needs to be re-created: {e}",
                )
            raise e
        except GlideTimeoutError as e:
            # A request timed out. You may choose to retry the execution based on your application's logic
            Logger.log(LogLevel.ERROR, "glide", f"TimeoutError encountered: {e}")
            raise e
        except GlideConnectionError as e:
            # The client wasn't able to reestablish the connection within the given retries
            Logger.log(LogLevel.ERROR, "glide", f"ConnectionError encountered: {e}")
            raise e
        except RequestError as e:
            # Other error reported during a request, such as a server response error
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
    # In this example, we will utilize the client's logger for all log messages
    Logger.set_logger_config(LogLevel.INFO)
    # Optional - set the logger to write to a file
    # Logger.set_logger_config(LogLevel.INFO, file)
    asyncio.run(exec_app_logic())


if __name__ == "__main__":
    main()
