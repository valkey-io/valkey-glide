use futures::future;
use redis::{self, RedisError};
use redis::aio::MultiplexedConnection;
use redis::{RedisResult};
use rand::{thread_rng, Rng};
use rand::distributions::Alphanumeric;
use stopwatch::Stopwatch;

async fn test_cmd(con: &MultiplexedConnection, i: i64, num_cmds: i64, data_size: usize) -> RedisResult<()> {
    let mut con = con.clone();
    let key = format!("Key{}", rand::thread_rng().gen_range(1..3750000));
    let value: String = thread_rng()
        .sample_iter(&Alphanumeric)
        .take(data_size)
        .map(char::from)
        .collect();
    for i in 0..num_cmds {
        if i % 1 == 0 {
            //print!("running set {} {}\n", key, value);
            redis::cmd("SET")
            .arg(&key[..])
            .arg(&value)
            .query_async(&mut con)
            .await?;
        } else {
            //print!("running get {}\n", key);
            redis::cmd("GET")
            .arg(&key[..])
            .query_async(&mut con)
            .await?;
        }
    }
    Ok(())
}

async fn run_tests(concurrent_cmds: i64, num_cmds: i64, data_size: usize) -> Vec<Result<(), RedisError>>{
    let client = redis::Client::open("redis://127.0.0.1/").unwrap();

    let con = client.get_multiplexed_tokio_connection().await.unwrap();
    let cmds_per_future = num_cmds / concurrent_cmds;
    let cmds = (0..concurrent_cmds).map(|i| test_cmd(&con, i, cmds_per_future, data_size));
    let mut stopwatch = Stopwatch::start_new();
    let result = future::join_all(cmds).await;
    stopwatch.stop();
    println!("Results for {} concurrent actions and {} actions:", concurrent_cmds, num_cmds);
    println!("TPS: {} for elapsed time: {}", num_cmds * 1000 / stopwatch.elapsed_ms(), stopwatch.elapsed_ms());
    return result;
}

#[tokio::main]
async fn main() {
    run_tests(100, 10000000, 1000).await;
}
