#### Changes

* Python Node: Allow routing Cluster requests by address. ([#1021](https://github.com/aws/glide-for-redis/pull/1021))
* Python: Added HSETNX command. ([#954](https://github.com/aws/glide-for-redis/pull/954))
* Python: Added SISMEMBER command ([#971](https://github.com/aws/glide-for-redis/pull/971))
* Python, Node: Added TYPE command ([#945](https://github.com/aws/glide-for-redis/pull/945), [#980](https://github.com/aws/glide-for-redis/pull/980))
* Python, Node: Added HLEN command ([#944](https://github.com/aws/glide-for-redis/pull/944), [#981](https://github.com/aws/glide-for-redis/pull/981))
* Python, Node: Added ZCOUNT command ([#878](https://github.com/aws/glide-for-redis/pull/878)) ([#909](https://github.com/aws/glide-for-redis/pull/909))
* Python: Added ECHO command ([#953](https://github.com/aws/glide-for-redis/pull/953))
* Python, Node: Added ZPOPMIN command ([#975](https://github.com/aws/glide-for-redis/pull/975), [#1008](https://github.com/aws/glide-for-redis/pull/1008))
* Node: Added STRLEN command ([#993](https://github.com/aws/glide-for-redis/pull/993))
* Node: Added LINDEX command ([#999](https://github.com/aws/glide-for-redis/pull/999))
* Python, Node: Added ZPOPMAX command ([#996](https://github.com/aws/glide-for-redis/pull/996), [#1009](https://github.com/aws/glide-for-redis/pull/1009))
* Python: Added ZRANGE command ([#906](https://github.com/aws/glide-for-redis/pull/906))
* Python, Node: Added PTTL command ([#1036](https://github.com/aws/glide-for-redis/pull/1036), [#1082](https://github.com/aws/glide-for-redis/pull/1082))

#### Features

* Python: Allow chaining function calls on transaction. ([#987](https://github.com/aws/glide-for-redis/pull/987))

## 0.2.0 (2024-02-11)

#### Changes
* Python, Node: Added ZCARD command ([#871](https://github.com/aws/glide-for-redis/pull/871), [#885](https://github.com/aws/glide-for-redis/pull/885))
* Python, Node: Added ZADD and ZADDINCR commands ([#814](https://github.com/aws/glide-for-redis/pull/814), [#830](https://github.com/aws/glide-for-redis/pull/830))
* Python, Node: Added ZREM command ([#834](https://github.com/aws/glide-for-redis/pull/834), [#831](https://github.com/aws/glide-for-redis/pull/831))
* Python, Node: Added ZSCORE command ([#877](https://github.com/aws/glide-for-redis/pull/877), [#889](https://github.com/aws/glide-for-redis/pull/889))
* Use jemalloc as default allocator. ([#847](https://github.com/aws/glide-for-redis/pull/847))
* Python, Node: Added RPOPCOUNT and LPOPCOUNT to transaction ([#874](https://github.com/aws/glide-for-redis/pull/874))
* Standalone client: Improve connection errors. ([#854](https://github.com/aws/glide-for-redis/pull/854))
* Python, Node: When recieving LPOP/RPOP with count, convert result to Array. ([#811](https://github.com/aws/glide-for-redis/pull/811))

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
