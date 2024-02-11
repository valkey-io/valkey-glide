## 0.2.0 (2024-02-11)

#### Changes
* Python, Node: Added ZCARD command ([#877](https://github.com/aws/glide-for-redis/pull/885), [#885](https://github.com/aws/glide-for-redis/pull/885))
* Python, Node: Added ZADD and ZADDINCR commands ([#814](https://github.com/aws/glide-for-redis/pull/814), [#830](https://github.com/aws/glide-for-redis/pull/830))
* Python, Node: Added ZREM command ([#834](https://github.com/aws/glide-for-redis/pull/834), [#831](https://github.com/aws/glide-for-redis/pull/831))
* Python, Node: Added ZSCORE command ([#885](https://github.com/aws/glide-for-redis/pull/885), [#871](https://github.com/aws/glide-for-redis/pull/871))
* Use jemalloc as default allocator. ([#847](https://github.com/aws/glide-for-redis/pull/847))
* Python: Added RPOPCOUNT and LPOPCOUNT to transaction ([#874](https://github.com/aws/glide-for-redis/pull/874))
* Standalone client: Improve connection errors. ([#854](https://github.com/aws/glide-for-redis/pull/854))
* Python: When recieving LPOP/RPOP with count, convert result to Array. ([#811](https://github.com/aws/glide-for-redis/pull/811))

#### Features
* Python, Node: Added support in Lua Scripts ([#775](https://github.com/aws/glide-for-redis/pull/775), [#860](https://github.com/aws/glide-for-redis/pull/860))
* Node: Allow chaining function calls on transaction. ([#902](https://github.com/aws/glide-for-redis/pull/902)) 

#### Fixes
* Core: Fixed `Connection Refused` error not to close the client ([#872](https://github.com/aws/glide-for-redis/pull/872))
* Socket listener: fix identifier for closed reader error. ([#853](https://github.com/aws/glide-for-redis/pull/853))
* Node: Fix issues with type import & exports ([#767](https://github.com/aws/glide-for-redis/pull/767))
* Core: Added handling to "?" and NULL hostnames in CLUSTER SLOTS ([#104](https://github.com/amazon-contributing/redis-rs/pull/104))
* Core: Cluster connection now reconnects after full disconnect. ([#100](https://github.com/amazon-contributing/redis-rs/pull/100))

## 0.1.0 (2024-01-17)

Preview release of **GLIDE for Redis** a Polyglot Redis client.

See the [README](README.md) for additional information.
