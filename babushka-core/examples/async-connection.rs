use futures::{future, prelude::*};
use redis::RedisError;

use babushka::connections::async_connections::SimpleAsyncConnection;

async fn test_cmd(con: &mut SimpleAsyncConnection, i: i32) -> Result<(), RedisError> {

    let key = format!("key{}", i);
    let key2 = format!("key{}_2", i);
    let value = format!("foo{}", i);

    redis::cmd("SET")
        .arg(&key[..])
        .arg(&value)
        .query_async(con)
        .await?;

    redis::cmd("SET")
        .arg(&[&key2, "bar"])
        .query_async(con)
        .await?;

    redis::cmd("MGET")
        .arg(&[&key, &key2])
        .query_async(con)
        .map(|result| {
            assert_eq!(Ok((value, b"bar".to_vec())), result);
        })
        .await;

    Ok(())
}

#[tokio::main]
async fn main() {
    let con = SimpleAsyncConnection::open("redis://127.0.0.1/").await.unwrap();

    let cmds = (0..100).map( |i| 
        {
            let mut con1 = con.clone();
            async move { test_cmd(&mut con1, i).await }
        });

    let result = future::try_join_all(cmds).await.unwrap();

    assert_eq!(100, result.len());
}
