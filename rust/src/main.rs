use futures::future;
use redis;
use std::{os::raw::c_char, ffi::{c_void, CString, CStr}, mem::ManuallyDrop};
use redis::{AsyncCommands, RedisError};
use redis::aio::MultiplexedConnection;
use tokio::runtime::Runtime;
use tokio::runtime::Builder;
use futures::{future, prelude::*};
use redis::{aio::MultiplexedConnection, RedisResult};

async fn test_cmd(con: &MultiplexedConnection, i: i32) -> RedisResult<()> {
    let mut con = con.clone();

    let key = format!("key{}", i);
    let key2 = format!("key{}_2", i);
    let value = format!("foo{}", i);

    redis::cmd("SET")
        .arg(&key[..])
        .arg(&value)
        .query_async(&mut con)
        .await?;

    redis::cmd("SET")
        .arg(&[&key2, "bar"])
        .query_async(&mut con)
        .await?;

    redis::cmd("MGET")
        .arg(&[&key, &key2])
        .query_async(&mut con)
        .map(|result| {
            assert_eq!(Ok((value, b"bar".to_vec())), result);
            Ok(())
        })
        .await
}

#[tokio::main]
async fn main() {
    let client = redis::Client::open("redis://127.0.0.1/").unwrap();

    let con = client.get_multiplexed_tokio_connection().await.unwrap();

    let cmds = (0..100).map(|i| test_cmd(&con, i));
    let result = future::try_join_all(cmds).await.unwrap();

    assert_eq!(100, result.len());
}
pub struct MyConnection {
    #[allow(dead_code)]
    client: redis::Client,
    connection: MultiplexedConnection,
    runtime: Runtime
}

fn create_connection_internal() -> MyConnection {
        let client = redis::Client::open("redis://localhost:6379").unwrap();
        let runtime = Builder::new_multi_thread()
            .worker_threads(1)
            .enable_io()        
            .thread_name("my-custom-name")
            .build()
            .unwrap();
        let _runtime_handle = runtime.enter();
        let connection = runtime.block_on(client.get_multiplexed_async_connection()).unwrap();
    MyConnection {
            client, connection, runtime
    }
}

#[no_mangle]
pub extern "C" fn create_connection() -> *const c_void {
    let connection_ptr = Box::new(create_connection_internal());
    Box::into_raw(connection_ptr) as *const c_void
}

#[no_mangle]
pub extern "C" fn close_connection(connection_ptr: *const c_void) {
    let connection_ptr = unsafe {Box::from_raw(connection_ptr as * mut MyConnection)};
    let _runtime_handle = connection_ptr.runtime.enter();
    // println!("try block");
    drop(connection_ptr);
    // println!("block done");
}

fn set_internal<F>(key: *const c_char, value: *const c_char, connection_ptr:*mut MyConnection, callback: F) where F: FnOnce() + Send + 'static{
    let key_cstring = unsafe {CStr::from_ptr(key as *mut c_char)};

    let value_cstring = unsafe {CStr::from_ptr(value as *mut c_char)};
    unsafe {
        let mut connection_clone = (*connection_ptr).connection.clone();
        //let _runtime_handle = (*connection_ptr).runtime.enter();
        (*connection_ptr).runtime.spawn(async move {
            let key_bytes = key_cstring.to_bytes();
            let value_bytes = value_cstring.to_bytes();
            let _:() = connection_clone.set(key_bytes, value_bytes).await.unwrap();
            callback();
        });
    }
}

#[no_mangle]
pub extern "C" fn set(key: *const c_char, value: *const c_char, connection_ptr: *const c_void, index: usize, callback: unsafe extern "C" fn(usize, *const c_char) -> ()) {
    let connection = connection_ptr as * mut MyConnection;
    unsafe {
        set_internal(key, value, connection, move|| {callback(index, std::ptr::null())});
    }
}

fn get_internal<F>(key: *const c_char, connection_ptr:*mut MyConnection, callback: F) where F: FnOnce(*const i8) + Send + 'static{
    let key_cstring = unsafe {CStr::from_ptr(key as *mut c_char)};
    unsafe {
        let mut connection_clone = (*connection_ptr).connection.clone();
        //let _runtime_handle = (*connection_ptr).runtime.enter();
        (*connection_ptr).runtime.spawn(async move {    
            // println!("get internal started");
            let key_bytes = key_cstring.to_bytes();
            
            let result: Result<String, RedisError> = connection_clone.get(key_bytes).await;
            let cstr_result = match result {
                Ok(res) => CString::new(res),
                _ => {
                    CString::new("")
                }
            };
            match cstr_result {
                Ok(res) => {
                    let result_c = res;
                    let ptr = result_c.as_ptr();
                    // println!("get done");
                    callback(ptr);
                },
                _ => callback(std::ptr::null())
            }
            // println!("get callback called");
        });
    }
}

#[no_mangle]
pub extern "C" fn get(key: *const c_char, connection_ptr: *const c_void, index: usize, callback: unsafe extern "C" fn(usize, *const c_char) -> ()) {
    // println!("get started");
    let connection_ptr = connection_ptr as * mut MyConnection;
    get_internal(key, connection_ptr, move |result| { unsafe { callback(index, result) }});
}

#[cfg(test)]
mod tests {
    use std::sync::{atomic::{AtomicUsize, Ordering}, Arc, Mutex};

    use futures::executor::block_on;
    use stopwatch::Stopwatch;
    use tokio::sync::oneshot;

    #[warn(unused_imports)]
    use super::*;

    use rand::{Rng, prelude::{ThreadRng, StdRng}, SeedableRng};

    // #[test]
    // fn it_works() {
    //     let con = create_connection();
    //     let key = CString::new("key").unwrap();
    //     let value = CString::new("value").unwrap();
    //     set(key.as_ptr(), value.as_ptr(), con, move || {
    //         unsafe {
    //             let result = CStr::from_ptr(get(key.as_ptr(), con) as *mut c_char);
    //             assert_eq!(result, value);
    //             close_connection(con);
    //         }
    //     });
    // }

    //#[test]
    fn internal_works() {
        let connection = create_connection() as usize;
        let runtime = Builder::new_multi_thread()
            .enable_io()        
            .thread_name("external")
            .build()
            .unwrap();
        let _runtime_handle = runtime.enter();
        let handle = runtime.spawn(async move {
            let key = CString::new("key").unwrap();
            let value = CString::new("value").unwrap();

            let (sender, receiver) = oneshot::channel();
            let callback = move || {sender.send(()).unwrap();};
            set_internal(key.as_ptr(), value.as_ptr(), connection as *mut MyConnection, callback);
            receiver.await.unwrap();

            let (sender, receiver) = oneshot::channel();
            let callback = move |result: *const i8| {unsafe { sender.send(CStr::from_ptr(result).to_owned()).unwrap();}};
            get_internal(key.as_ptr(), connection as *mut MyConnection, callback);

            let result = receiver.await.unwrap();
            assert_eq!(result, value);

            let value = CString::new("value2").unwrap();

            let (sender, receiver) = oneshot::channel();
            let callback = move || {sender.send(()).unwrap();};
            set_internal(key.as_ptr(), value.as_ptr(), connection as *mut MyConnection, callback);
            receiver.await.unwrap();

            let (sender, receiver) = oneshot::channel();
            let callback = move |result: *const i8| {unsafe { sender.send(CStr::from_ptr(result).to_owned()).unwrap();}};
            get_internal(key.as_ptr(), connection as *mut MyConnection, callback);

            let result = receiver.await.unwrap();
            assert_eq!(result, value);
        });
        block_on(handle).unwrap();
        drop(_runtime_handle);

        close_connection(connection as *const c_void);
    }

    //#[test]
    fn internal_empty_works() {
        let connection = create_connection() as usize;
        let runtime = Builder::new_multi_thread()
            .enable_io()        
            .thread_name("external")
            .build()
            .unwrap();
        let _runtime_handle = runtime.enter();
        let handle = runtime.spawn(async move {
            let key = CString::new("key_that_should_be_empty").unwrap();

            let (sender, receiver) = oneshot::channel();
            let callback = move |result: *const i8| {
                assert_eq!(CString::new("").unwrap(), unsafe { CStr::from_ptr(result).to_owned()});
                sender.send(()).unwrap();
            };
            get_internal(key.as_ptr(), connection as *mut MyConnection, callback);

            receiver.await.unwrap();
        });
        block_on(handle).unwrap();
        drop(_runtime_handle);

        close_connection(connection as *const c_void);
    }

    // #[test]
    // fn empty_works() {
    //     let con = create_connection();
    //     let key = CString::new("keynew").unwrap();
    //     unsafe {
    //         get(key.as_ptr(), con, move |output| {
    //             let result = CStr::from_ptr(output as *mut c_char);
    //             assert_eq!(CString::new("").unwrap(), result);
    //             close_connection(con);
    //         });
    //     }
    // }

    fn sample_gaussian(rng_mutex: &Mutex<StdRng>) -> i32        {
        let mut rng = rng_mutex.lock().unwrap();
        // The method requires sampling from a uniform random of (0,1]
        // but Random.NextDouble() returns a sample of [0,1).
        let x1 = 1.0 - rng.gen::<f64>();
        let x2 = 1.0 - rng.gen::<f64>();

        let y1 = (-2.0 * x1.ln()).sqrt() * (std::f64::consts::TAU * x2).cos();
        return ((y1 * 400.0) + 1024.0) as i32;
    }

    fn benchmark_internal(concurrent_commands: usize, num_commands: usize) {
        let rng_external = Arc::new(Mutex::new(StdRng::from_rng(ThreadRng::default()).unwrap()));
        let connection = create_connection() as usize;
        let counter = Arc::new(AtomicUsize::new(0));
        let mut stopwatch = Stopwatch::start_new();
        let mut handles = vec![];
        let runtime = Builder::new_multi_thread()
            .enable_io()        
            .thread_name("external")
            .build()
            .unwrap();
        let _runtime_handle = runtime.enter();
        for _ in 0..concurrent_commands {
            let inner_counter = counter.clone();
            let rng = rng_external.clone();
            handles.push(runtime.spawn(async move {
                loop {
                    let key: i32 = rng.lock().unwrap().gen_range(1..3750000);
                    let key_str = CString::new(key.to_string()).unwrap();
                    let result = inner_counter.fetch_add(1, Ordering::Relaxed);
                    if result >= num_commands {
                        return;
                    }
                    let (sender, receiver) = oneshot::channel();
                    if result % 1 == 0 {
                        let value = CString::new(sample_gaussian(&rng).to_string()).unwrap();
                        let callback = move || {sender.send(ManuallyDrop::new(CString::new("").unwrap()).as_ptr() as usize).unwrap();};
                        set_internal(key_str.as_ptr(), value.as_ptr(), connection as *mut MyConnection, callback);
                    } else {
                        let callback = move |result: *const i8| {sender.send(result as usize).unwrap();};
                        get_internal(key_str.as_ptr(), connection as *mut MyConnection, callback);
                    }
                    receiver.await.unwrap();
                }
            }));
        }
        block_on(futures::future::join_all(handles));
        stopwatch.stop();
        close_connection(connection as *const c_void);
        println!("Results for {} concurrent actions and {} actions:", concurrent_commands, num_commands);
        println!("PTS: {} for elapsed time: {}", num_commands * 1000 / stopwatch.elapsed_ms() as usize, stopwatch.elapsed_ms());
    }

    #[test]
    fn benchmark() {
        println!("starting testing!");
        //benchmark_internal(1, 150000);
        // benchmark_internal(10, 1500000);
        // benchmark_internal(100, 3000000);
        // benchmark_internal(1000, 3000000);
        benchmark_internal(10000, 3000000);
    }
}


// #[tokio::main]
// async fn main() -> RedisResult<()> {
//     let client = redis::Client::open("redis://127.0.0.1/")?;
//     let mut con = client.get_connection().await?;
//     con.set("key", "foo").await?;
//     let result : String = con.get("foo").await?;
//     print!("result is {:?}", result);
//     Ok(())
// }