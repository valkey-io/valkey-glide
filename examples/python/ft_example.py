import asyncio
from typing import List, Tuple

from glide import (
    ClosingError,
    ConnectionError,
    GlideClient,
    GlideClientConfiguration,
    Logger,
    LogLevel,
    NodeAddress,
    RequestError,
    TimeoutError
)

from python.glade.async_commands.server_modules import ft
from glide.async_commands.server_modules.ft_options.ft_search_options import (
    FtSearchOptions,
)

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
        # Enable this field if the servers are configured with TLS.
        # use_tls=True
    )
    return await GlideClient.create(config)


async def app_logic(client: GlideClusterClient):
    """
    Executes the main logic of the application, performing basic operations
    such as FT.CREATE and FT.SEARCH using the provided GlideClient.

    Args:
        client (GlideClusterClient): An instance of GlideClient.
    """
    # Create a vector
    index = prefix + str(uuid.uuid4())
    create_response = await ft.create(client, index,
                    schema=[
                        NumericField("$.a", "a"),
                        NumericField("$.b", "b"),
                    ],
                options=FtCreateOptions(DataType.JSON),
            )
    Logger.log(LogLevel.INFO, "app", f"Create response is = {create_response!r}")   # 'OK'

    # Create a json key.
    assert (
        await GlideJson.set(glide_client, json_key1, "$", json.dumps(json_value1))
        == OK
    )
    assert (
        await GlideJson.set(glide_client, json_key2, "$", json.dumps(json_value2))
        == OK
    )
    
    time.sleep(self.sleep_wait_time)
    
    # Search for the vector
    search_response = await ft.search(glide_client, index, "*", options=ft_search_options)
    ft_search_options = FtSearchOptions(
        return_fields=[
            ReturnField(field_identifier="a", alias="a_new"),
            ReturnField(field_identifier="b", alias="b_new"),
        ]
    )

    Logger.log(LogLevel.INFO, "app", f"Search response is = {search_response!r}")


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
        except TimeoutError as e:
            # A request timed out. You may choose to retry the execution based on your application's logic
            Logger.log(LogLevel.ERROR, "glide", f"TimeoutError encountered: {e}")
            raise e
        except ConnectionError as e:
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
