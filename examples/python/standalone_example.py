import asyncio
from typing import List, Tuple

from glide import (
    GlideClient,
    GlideClientConfiguration,
    Logger,
    LogLevel,
    NodeAddress,
    RequestError,
    ClosingError,
    ConnectionError as GlideConnectionError,
    TimeoutError as GlideTimeoutError
)

from glide import GlideClientConfiguration

async def create_client(
    nodes_list: List[Tuple[str, int]] = [("localhost", 6379)]
) -> GlideClient:
    """
    Creates and returns a GlideClient instance.

    This function initializes a GlideClient with the provided list of nodes.
    The nodes_list may contain either only primary node or a mix of primary
    and replica nodes. The GlideClient use these nodes to connect to
    the Standalone setup servers.

    Args:
        nodes_list (List[Tuple[str, int]]): A list of tuples where each tuple
            contains a host (str) and port (int). Defaults to [("localhost", 6379)].

    Returns:
        GlideClient: An instance of GlideClient connected to the specified nodes.
    """
    addresses = []
    for host, port in nodes_list:
        addresses.append(NodeAddress(host, port))

    # Check `GlideClientConfiguration` for additional options.
    config = GlideClientConfiguration(
        addresses,
        # Set request timeout - recommended to configure based on your use case.
        request_timeout=500,
        # Enable this field if the servers are configured with TLS.
        # use_tls=True
    )
    return await GlideClient.create(config)


async def app_logic(client: GlideClient):
    """
    Executes the main logic of the application, performing basic operations
    such as SET, GET, and PING using the provided GlideClient.

    Args:
        client (GlideClient): An instance of GlideClient.
    """
    # Send SET and GET
    set_response = await client.set("foo", "bar")
    Logger.log(LogLevel.INFO, "app", f"Set response is = {set_response!r}")

    get_response = await client.get("foo")
    assert isinstance(get_response, bytes)
    Logger.log(LogLevel.INFO, "app", f"Get response is = {get_response.decode()!r}")

    # Send PING to the primary node
    pong = await client.ping()
    Logger.log(LogLevel.INFO, "app", f"PING response is = {pong!r}")


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
                    f"Encountered an error while closing the client: {e}",
                )


def main():
    # In this example, we will utilize the client's logger for all log messages
    Logger.set_logger_config(LogLevel.INFO)
    # Optional - set the logger to write to a file
    # Logger.set_logger_config(LogLevel.INFO, file)
    asyncio.run(exec_app_logic())


if __name__ == "__main__":
    main()
