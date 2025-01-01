import redis
import sys
from glide import GlideSync

# Example usage
if __name__ == "__main__":
    redis_client = redis.Redis(decode_responses=False)
    glide_client = GlideSync()
    print(glide_client.strlen("foo"))
    key="foo"
    value = "0"*4
    print(f"value size={sys.getsizeof(value)}")
    import timeit
    def glide_test_fn():
        glide_client.set(key, value)
        glide_client.mget([key, key, key, key, key, key, key, key, key])

    def redis_test_fn():
        redis_client.set(key, value)
        glide_client.mget([key, key, key, key, key, key, key, key, key])

    # Benchmark the function
    num_of_requests = 1000
    redispy_execution_time = timeit.timeit(redis_test_fn, number=num_of_requests)
    glide_execution_time = timeit.timeit(glide_test_fn, number=num_of_requests)
    print(f"Glide Execution time: {glide_execution_time:.6f} seconds, avg TPS: {(num_of_requests/glide_execution_time):.0f}")
    print(f"RedisPy Execution time: {redispy_execution_time:.6f} seconds, avg TPS: {(num_of_requests/redispy_execution_time):.0f}")
