import asyncio
from typing import List, Tuple, Optional

from glide.async_commands.server_modules import glide_json as json
from glide.async_commands.server_modules import ft
from glide.constants import OK, FtSearchResponse, TEncodable

import uuid

from glide import (
    AllNodes,
    ClosingError,
    ConnectionError as GlideConnectionError,
    GlideClusterClient,
    GlideClusterClientConfiguration,
    InfoSection,
    Logger,
    LogLevel,
    NodeAddress,
    RequestError,
    TimeoutError as GlideTimeoutError,
)

from glide.async_commands.server_modules.ft_options.ft_create_options import (
    DataType,
    DistanceMetricType,
    Field,
    FtCreateOptions,
    NumericField,
    TagField,
    TextField,
    VectorAlgorithm,
    VectorField,
    VectorFieldAttributesHnsw,
    VectorType,
)

from glide.async_commands.server_modules.ft_options.ft_search_options import (
    FtSearchOptions,
    ReturnField,
)

async def create_client(
    nodes_list: Optional[List[Tuple[str, int]]] = None
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
        # Enable this field if the servers are configured with TLS.
        # use_tls=True
    )
    return await GlideClusterClient.create(config)

async def app_logic(client: GlideClusterClient):
    """
    Executes the main logic of the application, performing basic operations
    such as FT.CREATE and FT.SEARCH using the provided GlideClient.

    Args:
        client (GlideClusterClient): An instance of GlideClient.
    """
    # Create a vector
    prefix = "{json}:"
    index = prefix + str(uuid.uuid4())
    json_key1 = prefix + "1"
    json_key2 = prefix + "2"
    json_value1 = {"a": 11111, "b": 2, "c": 3}
    json_value2 = {"a": 22222, "b": 2, "c": 3}
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
        await json.set(client, json_key1, "$", json.dumps(json_value1))
        == OK
    )
    assert (
        await json.set(client, json_key2, "$", json.dumps(json_value2))
        == OK
    )

    # Search for the vector
    search_response = await ft.search(client, index, "*", options=ft_search_options)
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
