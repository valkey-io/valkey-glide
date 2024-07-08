import asyncio

from glide import (
    ClosingError,
    GlideClient,
    GlideClientConfiguration,
    Logger,
    LogLevel,
    NodeAddress,
    RequestError,
)


async def create_client(host: str = "localhost", port: int = 6379) -> GlideClient:
    # Replicas can be added to the addresses list
    addresses = [NodeAddress(host, port)]
    # Check `GlideClientConfiguration` for additional options.
    config = GlideClientConfiguration(
        addresses,
        # Enable this field if the servers are configured with TLS.
        # use_tls=True
    )
    return await GlideClient.create(config)


async def app_logic(client: GlideClient):
    # Send SET and GET
    set_response = await client.set("foo", "bar")
    print(f"Set response is = {set_response!r}")
    get_response = await client.get("foo")
    assert isinstance(get_response, bytes)
    print(f"Get response is = {get_response.decode()!r}")
    # Send PING to the primary node
    pong = await client.ping()
    print(f"PONG response is = {pong}")


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
