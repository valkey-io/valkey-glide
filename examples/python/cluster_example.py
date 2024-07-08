import asyncio

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
    host: str = "localhost", port: int = 6379
) -> GlideClusterClient:
    # When in Redis is cluster mode, add address of any nodes, and the client will find all nodes in the cluster.
    addresses = [NodeAddress(host, port)]
    # Check `GlideClusterClientConfiguration` for additional options.
    config = GlideClusterClientConfiguration(
        addresses=addresses,
        client_name="test_cluster_client",
        # if the cluster nodes use TLS, you'll need to enable it. Otherwise the connection attempt will time out silently.
        # use_tls=True
    )
    return await GlideClusterClient.create(config)


async def app_logic(client: GlideClusterClient):
    # Send SET and GET
    set_response = await client.set("foo", "bar")
    print(f"Set response is = {set_response!r}")
    get_response = await client.get("foo")
    assert isinstance(get_response, bytes)
    print(f"Get response is = {get_response.decode()!r}")
    # Send PING to all primaries (according to Redis's PING request_policy)
    pong = await client.ping()
    print(f"PONG response is = {pong}")
    # Send INFO REPLICATION with routing option to all nodes
    info_repl_resps = await client.info([InfoSection.REPLICATION], AllNodes())
    print(f"INFO REPLICATION responses from all nodes are=\n{info_repl_resps!r}")


async def exec_app_logic():
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
                raise e
            print(f"Client has closed and needs to be re-created: {e}")
        except RequestError as e:
            print(f"RequestError encountered: {e}")
            raise e
        except Exception as e:
            print(f"Unexpected error: {e}")
            raise e
        finally:
            try:
                await client.close()
            except Exception:
                pass
            # Optionally, handle or log closure errors


def main():
    Logger.set_logger_config(LogLevel.INFO)
    # Optional - set the logger to write to a file
    # Logger.set_logger_config(LogLevel.INFO, file)
    asyncio.run(exec_app_logic())


if __name__ == "__main__":
    main()
