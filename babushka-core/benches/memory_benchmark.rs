use babushka::{
    client::Client,
    connection_request::{AddressInfo, ConnectionRequest, TlsMode},
};
use iai_callgrind::{black_box, main};
use redis::{aio::ConnectionLike, cmd, Value};
use tokio::runtime::Builder;

fn create_connection_request() -> ConnectionRequest {
    let host = "localhost";
    let mut request = ConnectionRequest::new();
    request.tls_mode = TlsMode::NoTls.into();
    let mut address_info = AddressInfo::new();
    address_info.host = host.into();
    address_info.port = 6379;
    request.addresses.push(address_info);
    request
}

fn runner<Fut>(f: impl FnOnce(Client) -> Fut)
where
    Fut: futures::Future<Output = ()>,
{
    let runtime = Builder::new_current_thread().enable_all().build().unwrap();
    runtime.block_on(async {
        let client = Client::new(create_connection_request()).await.unwrap();
        f(client).await;
    });
}

// Don't forget the `#[inline(never)]`
#[inline(never)]
fn just_setup() {
    runner(|_| async {});
}

// Don't forget the `#[inline(never)]`
#[inline(never)]
fn send_message() {
    runner(|mut client| async move {
        client
            .req_packed_command(&black_box(cmd("PING")))
            .await
            .unwrap();
    });
}

// Don't forget the `#[inline(never)]`
#[inline(never)]
fn send_and_receive_messages() {
    runner(|mut client| async move {
        let mut command = cmd("SET");
        command.arg("foo").arg("bar");
        client
            .req_packed_command(&black_box(command))
            .await
            .unwrap();
        let mut command = cmd("SET");
        command.arg("baz").arg("foo");
        client
            .req_packed_command(&black_box(command))
            .await
            .unwrap();
        let mut command = cmd("MGET");
        command.arg("baz").arg("foo");
        let result = client
            .req_packed_command(&black_box(command))
            .await
            .unwrap();
        assert!(
            result
                == Value::Bulk(vec![
                    Value::Data(b"foo".to_vec()),
                    Value::Data(b"bar".to_vec())
                ])
        )
    });
}

main!(just_setup, send_message, send_and_receive_messages);
