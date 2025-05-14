# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


import time
from datetime import date, datetime, timedelta, timezone
from typing import List, Optional, Union, cast

import pytest

from glide import RequestError, TimeoutError
from glide.async_commands.batch import (
    BaseBatch,
    Batch,
    ClusterBatch,
    ClusterTransaction,
    Transaction,
)
from glide.async_commands.bitmap import (
    BitFieldGet,
    BitFieldSet,
    BitmapIndexType,
    BitOffset,
    BitOffsetMultiplier,
    BitwiseOperation,
    OffsetOptions,
    SignedEncoding,
    UnsignedEncoding,
)
from glide.async_commands.command_args import Limit, ListDirection, OrderBy
from glide.async_commands.core import (
    ExpiryGetEx,
    ExpiryTypeGetEx,
    FlushMode,
    FunctionRestorePolicy,
    InfoSection,
    InsertPosition,
)
from glide.async_commands.sorted_set import (
    AggregationType,
    GeoSearchByBox,
    GeoSearchByRadius,
    GeospatialData,
    GeoUnit,
    InfBound,
    LexBoundary,
    RangeByIndex,
    ScoreBoundary,
    ScoreFilter,
)
from glide.async_commands.stream import (
    IdBound,
    MaxId,
    MinId,
    StreamAddOptions,
    StreamClaimOptions,
    StreamGroupOptions,
    StreamReadGroupOptions,
    TrimByMinId,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TResult, TSingleNodeRoute
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide.routes import AllNodes, SlotIdRoute, SlotKeyRoute, SlotType
from tests.conftest import create_client
from tests.utils.utils import (
    check_if_server_version_lt,
    convert_bytes_to_string_object,
    generate_lua_lib_code,
    get_random_string,
)


def generate_key(keyslot: Optional[str], is_atomic: bool) -> str:
    """Generate a key with the same slot if keyslot is provided; otherwise, generate a random key."""
    if is_atomic and keyslot:
        return f"{{{keyslot}}}: {get_random_string(10)}"
    return get_random_string(20)


def generate_key_same_slot(keyslot: str) -> str:
    """Generate a key with the same slot as the provided keyslot."""
    if keyslot.startswith("{") and "}" in keyslot:
        # Extract the tag between the first '{' and the first '}'.
        # This handles cases where keyslot already contains hash tags, in order to avoid nested hash tags.
        end_index = keyslot.index("}")
        tag = keyslot[1:end_index]
    else:
        tag = keyslot

    return f"{{{tag}}}: {get_random_string(10)}"


async def batch_test(
    batch: Union[Batch, ClusterBatch],
    glide_client: TGlideClient,
    keyslot: Optional[str] = None,
) -> List[TResult]:
    """
    Test all batch commands.
    For transactions, all keys are generated in the same slot to ensure atomic execution.
    For batchs, only keys used together in commands that require same-slot access
    (e.g.SDIFF, ZADD) will share a slot - other keys can be in different slots.
    """
    key = generate_key(keyslot, batch.is_atomic)
    key2 = generate_key_same_slot(key)
    key3 = generate_key_same_slot(key)
    key4 = generate_key_same_slot(key)
    key5 = generate_key(keyslot, batch.is_atomic)
    key6 = generate_key_same_slot(key5)
    key7 = generate_key(keyslot, batch.is_atomic)
    key8 = generate_key(keyslot, batch.is_atomic)
    key9 = generate_key(keyslot, batch.is_atomic)  # list
    key10 = generate_key(keyslot, batch.is_atomic)  # hyper log log
    key11 = generate_key(keyslot, batch.is_atomic)  # streams
    key12 = generate_key(keyslot, batch.is_atomic)  # geo
    key13 = generate_key_same_slot(key8)  # sorted set
    key14 = generate_key_same_slot(key8)  # sorted set
    key15 = generate_key_same_slot(key8)  # sorted set
    key16 = generate_key(keyslot, batch.is_atomic)  # sorted set
    key17 = generate_key(keyslot, batch.is_atomic)  # sort
    key18 = generate_key_same_slot(key17)  # sort
    key19 = generate_key(keyslot, batch.is_atomic)  # bitmap
    key20 = generate_key_same_slot(key19)  # bitmap
    key22 = generate_key(keyslot, batch.is_atomic)  # getex
    key23 = generate_key(keyslot, batch.is_atomic)  # string
    key24 = generate_key_same_slot(key23)  # string
    key25 = generate_key(keyslot, batch.is_atomic)  # list
    key26 = generate_key(keyslot, batch.is_atomic)  # sort
    key27 = generate_key_same_slot(key26)  # sort
    value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
    value_bytes = value.encode()
    value2 = get_random_string(5)
    value2_bytes = value2.encode()
    value3 = get_random_string(5)
    value3_bytes = value3.encode()
    lib_name = f"mylib1C{get_random_string(5)}"
    func_name = f"myfunc1c{get_random_string(5)}"
    code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
    args: List[TResult] = []

    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        batch.function_load(code)
        args.append(lib_name.encode())
        batch.function_load(code, True)
        args.append(lib_name.encode())
        if batch.is_atomic:
            batch.function_list(lib_name)
            args.append(
                [
                    {
                        b"library_name": lib_name.encode(),
                        b"engine": b"LUA",
                        b"functions": [
                            {
                                b"name": func_name.encode(),
                                b"description": None,
                                b"flags": {b"no-writes"},
                            }
                        ],
                    }
                ]
            )
            batch.function_list(lib_name, True)
            args.append(
                [
                    {
                        b"library_name": lib_name.encode(),
                        b"engine": b"LUA",
                        b"functions": [
                            {
                                b"name": func_name.encode(),
                                b"description": None,
                                b"flags": {b"no-writes"},
                            }
                        ],
                        b"library_code": code.encode(),
                    }
                ]
            )
        batch.fcall(func_name, [], arguments=["one", "two"])
        args.append(b"one")
        batch.fcall(func_name, [key], arguments=["one", "two"])
        args.append(b"one")
        batch.fcall_ro(func_name, [], arguments=["one", "two"])
        args.append(b"one")
        batch.fcall_ro(func_name, [key], arguments=["one", "two"])
        args.append(b"one")
        batch.function_delete(lib_name)
        args.append(OK)
        batch.function_flush()
        args.append(OK)
        batch.function_flush(FlushMode.ASYNC)
        args.append(OK)
        batch.function_flush(FlushMode.SYNC)
        args.append(OK)
        if batch.is_atomic:
            batch.function_stats()
            args.append(
                {
                    b"running_script": None,
                    b"engines": {
                        b"LUA": {
                            b"libraries_count": 0,
                            b"functions_count": 0,
                        }
                    },
                }
            )

    # To fix flake8's complexity analysis, we break down the argument appending into 6
    # helper functions.
    await helper1(
        batch,
        glide_client,
        key,
        key2,
        value,
        value_bytes,
        value2,
        value2_bytes,
        args,
    )

    await helper2(
        batch,
        glide_client,
        key,
        key2,
        key3,
        key4,
        value,
        value_bytes,
        value2,
        value2_bytes,
        args,
    )

    await helper3(
        batch,
        glide_client,
        key5,
        key6,
        key7,
        key9,
        value,
        value_bytes,
        value2,
        value2_bytes,
        value3,
        value3_bytes,
        args,
    )

    await helper4(
        batch, glide_client, key8, key10, key13, key14, key15, key19, key20, args
    )

    await helper5(batch, glide_client, key11, key12, key17, key18, key20, args)

    await helper6(
        batch,
        keyslot,
        glide_client,
        key,
        key7,
        key16,
        key22,
        key23,
        key24,
        key25,
        key26,
        key27,
        args,
    )

    return args


async def helper6(
    transaction,
    keyslot,
    glide_client,
    key,
    key7,
    key16,
    key22,
    key23,
    key24,
    key25,
    key26,
    key27,
    args,
):
    if not await check_if_server_version_lt(glide_client, "8.0.0"):
        keyslot = keyslot or key26
        transaction.hset(f"{{{keyslot}}}: 1", {"name": "Alice", "age": "30"})
        args.append(2)
        transaction.hset(f"{{{keyslot}}}: 2", {"name": "Bob", "age": "25"})
        args.append(2)
        transaction.lpush(key26, ["2", "1"])
        args.append(2)
        transaction.sort(
            key26,
            by_pattern=f"{{{keyslot}}}: *->age",
            get_patterns=[f"{{{keyslot}}}: *->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        args.append([b"Bob", b"Alice"])
        transaction.sort_store(
            key26,
            key27,
            by_pattern=f"{{{keyslot}}}: *->age",
            get_patterns=[f"{{{keyslot}}}: *->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        args.append(2)

    transaction.sadd(key7, ["one"])
    args.append(1)
    transaction.srandmember(key7)
    args.append(b"one")
    transaction.srandmember_count(key7, 1)
    args.append([b"one"])
    transaction.flushall(FlushMode.ASYNC)
    args.append(OK)
    transaction.flushall()
    args.append(OK)
    transaction.flushdb(FlushMode.ASYNC)
    args.append(OK)
    transaction.flushdb()
    args.append(OK)
    transaction.set(key, "foo")
    args.append(OK)
    transaction.random_key()
    args.append(key.encode())

    min_version = "6.0.6"
    if not await check_if_server_version_lt(glide_client, min_version):
        transaction.rpush(key25, ["a", "a", "b", "c", "a", "b"])
        args.append(6)
        transaction.lpos(key25, "a")
        args.append(0)
        transaction.lpos(key25, "a", 1, 0, 0)
        args.append([0, 1, 4])

    min_version = "6.2.0"
    if not await check_if_server_version_lt(glide_client, min_version):
        transaction.flushall(FlushMode.SYNC)
        args.append(OK)
        transaction.flushdb(FlushMode.SYNC)
        args.append(OK)

    min_version = "6.2.0"
    if not await check_if_server_version_lt(glide_client, min_version):
        transaction.set(key22, "value")
        args.append(OK)
        transaction.getex(key22)
        args.append(b"value")
        transaction.getex(key22, ExpiryGetEx(ExpiryTypeGetEx.SEC, 1))
        args.append(b"value")

    min_version = "7.0.0"
    if not await check_if_server_version_lt(glide_client, min_version):
        transaction.zadd(key16, {"a": 1, "b": 2, "c": 3, "d": 4})
        args.append(4)
        transaction.bzmpop([key16], ScoreFilter.MAX, 0.1)
        args.append([key16.encode(), {b"d": 4.0}])
        transaction.bzmpop([key16], ScoreFilter.MIN, 0.1, 2)
        args.append([key16.encode(), {b"a": 1.0, b"b": 2.0}])

        transaction.mset({key23: "abcd1234", key24: "bcdef1234"})
        args.append(OK)
        transaction.lcs(key23, key24)
        args.append(b"bcd1234")
        transaction.lcs_len(key23, key24)
        args.append(7)
        transaction.lcs_idx(key23, key24)
        args.append({b"matches": [[[4, 7], [5, 8]], [[1, 3], [0, 2]]], b"len": 7})
        transaction.lcs_idx(key23, key24, min_match_len=4)
        args.append({b"matches": [[[4, 7], [5, 8]]], b"len": 7})
        transaction.lcs_idx(key23, key24, with_match_len=True)
        args.append({b"matches": [[[4, 7], [5, 8], 4], [[1, 3], [0, 2], 3]], b"len": 7})

    transaction.pubsub_channels(pattern="*")
    args.append([])
    transaction.pubsub_numpat()
    args.append(0)
    transaction.pubsub_numsub()
    args.append({})


async def helper5(transaction, glide_client, key11, key12, key17, key18, key20, args):
    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        transaction.set(key20, "foobar")
        args.append(OK)
        transaction.bitcount(key20, OffsetOptions(5, 30, BitmapIndexType.BIT))
        args.append(17)
        transaction.bitpos(key20, 1, OffsetOptions(44, 50, BitmapIndexType.BIT))
        args.append(46)

    if not await check_if_server_version_lt(glide_client, "8.0.0"):
        transaction.set(key20, "foobar")
        args.append(OK)
        transaction.bitcount(key20, OffsetOptions(0))
        args.append(26)

    transaction.geoadd(
        key12,
        {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        },
    )
    args.append(2)
    transaction.geodist(key12, "Palermo", "Catania")
    args.append(166274.1516)
    transaction.geohash(key12, ["Palermo", "Catania", "Place"])
    args.append([b"sqc8b49rny0", b"sqdtr74hyu0", None])
    transaction.geopos(key12, ["Palermo", "Catania", "Place"])
    # The comparison allows for a small tolerance level due to potential precision errors in floating-point calculations
    # No worries, Python can handle it, therefore, this shouldn't fail
    args.append(
        [
            [13.36138933897018433, 38.11555639549629859],
            [15.08726745843887329, 37.50266842333162032],
            None,
        ]
    )

    transaction.geosearch(
        key12, "Catania", GeoSearchByRadius(200, GeoUnit.KILOMETERS), OrderBy.ASC
    )
    args.append([b"Catania", b"Palermo"])
    transaction.geosearchstore(
        key12,
        key12,
        GeospatialData(15, 37),
        GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
        store_dist=True,
    )
    args.append(2)

    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-1"))
    args.append(b"0-1")
    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-2"))
    args.append(b"0-2")
    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-3"))
    args.append(b"0-3")
    transaction.xlen(key11)
    args.append(3)
    transaction.xread({key11: "0-2"})
    args.append({key11.encode(): {b"0-3": [[b"foo", b"bar"]]}})
    transaction.xrange(key11, IdBound("0-1"), IdBound("0-1"))
    args.append({b"0-1": [[b"foo", b"bar"]]})
    transaction.xrevrange(key11, IdBound("0-1"), IdBound("0-1"))
    args.append({b"0-1": [[b"foo", b"bar"]]})
    transaction.xtrim(key11, TrimByMinId(threshold="0-2", exact=True))
    args.append(1)
    transaction.xinfo_groups(key11)
    args.append([])

    group_name1 = get_random_string(10)
    group_name2 = get_random_string(10)
    consumer = get_random_string(10)
    transaction.xgroup_create(key11, group_name1, "0-2")
    args.append(OK)
    transaction.xgroup_create(
        key11, group_name2, "0-0", StreamGroupOptions(make_stream=True)
    )
    args.append(OK)
    transaction.xinfo_consumers(key11, group_name1)
    args.append([])
    transaction.xgroup_create_consumer(key11, group_name1, consumer)
    args.append(True)
    transaction.xgroup_set_id(key11, group_name1, "0-2")
    args.append(OK)
    transaction.xreadgroup({key11: ">"}, group_name1, consumer)
    args.append({key11.encode(): {b"0-3": [[b"foo", b"bar"]]}})
    transaction.xreadgroup(
        {key11: "0-3"}, group_name1, consumer, StreamReadGroupOptions(count=2)
    )
    args.append({key11.encode(): {}})
    transaction.xclaim(key11, group_name1, consumer, 0, ["0-1"])
    args.append({})
    transaction.xclaim(
        key11, group_name1, consumer, 0, ["0-3"], StreamClaimOptions(is_force=True)
    )
    args.append({b"0-3": [[b"foo", b"bar"]]})
    transaction.xclaim_just_id(key11, group_name1, consumer, 0, ["0-3"])
    args.append([b"0-3"])
    transaction.xclaim_just_id(
        key11, group_name1, consumer, 0, ["0-4"], StreamClaimOptions(is_force=True)
    )
    args.append([])

    transaction.xpending(key11, group_name1)
    args.append([1, b"0-3", b"0-3", [[consumer.encode(), b"1"]]])

    min_version = "6.2.0"
    if not await check_if_server_version_lt(glide_client, min_version):
        transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
        transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")
        # if using Valkey 7.0.0 or above, responses also include a list of entry IDs that were removed from the Pending
        # Entries List because they no longer exist in the stream
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            args.append(
                [b"0-0", {b"0-3": [[b"foo", b"bar"]]}]
            )  # transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
            args.append(
                [b"0-0", [b"0-3"]]
            )  # transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")
        else:
            args.append(
                [b"0-0", {b"0-3": [[b"foo", b"bar"]]}, []]
            )  # transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
            args.append(
                [b"0-0", [b"0-3"], []]
            )  # transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")

    transaction.xack(key11, group_name1, ["0-3"])
    args.append(1)
    transaction.xpending_range(key11, group_name1, MinId(), MaxId(), 1)
    args.append([])
    transaction.xgroup_del_consumer(key11, group_name1, consumer)
    args.append(0)
    transaction.xgroup_destroy(key11, group_name1)
    args.append(True)
    transaction.xgroup_destroy(key11, group_name2)
    args.append(True)

    transaction.xdel(key11, ["0-3", "0-5"])
    args.append(1)

    transaction.lpush(key17, ["2", "1", "4", "3", "a"])
    args.append(5)
    transaction.sort(
        key17,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append([b"2", b"3", b"4", b"a"])
    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        transaction.sort_ro(
            key17,
            limit=Limit(1, 4),
            order=OrderBy.ASC,
            alpha=True,
        )
        args.append([b"2", b"3", b"4", b"a"])
    transaction.sort_store(
        key17,
        key18,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append(4)


async def helper4(
    transaction, glide_client, key8, key10, key13, key14, key15, key19, key20, args
):
    transaction.zadd(key8, {"one": 1, "two": 2, "three": 3, "four": 4})
    args.append(4.0)
    transaction.zrank(key8, "one")
    args.append(0)
    transaction.zrevrank(key8, "one")
    args.append(3)
    if not await check_if_server_version_lt(glide_client, "7.2.0"):
        transaction.zrank_withscore(key8, "one")
        args.append([0, 1])
        transaction.zrevrank_withscore(key8, "one")
        args.append([3, 1])
    transaction.zadd_incr(key8, "one", 3)
    args.append(4.0)
    transaction.zincrby(key8, 3, "one")
    args.append(7.0)
    transaction.zrem(key8, ["one"])
    args.append(1)
    transaction.zcard(key8)
    args.append(3)
    transaction.zcount(key8, ScoreBoundary(2, is_inclusive=True), InfBound.POS_INF)
    args.append(3)
    transaction.zlexcount(key8, LexBoundary("a", is_inclusive=True), InfBound.POS_INF)
    args.append(3)
    transaction.zscore(key8, "two")
    args.append(2.0)
    transaction.zrange(key8, RangeByIndex(0, -1))
    args.append([b"two", b"three", b"four"])
    transaction.zrange_withscores(key8, RangeByIndex(0, -1))
    args.append({b"two": 2.0, b"three": 3.0, b"four": 4.0})
    transaction.zmscore(key8, ["two", "three"])
    args.append([2.0, 3.0])
    transaction.zrangestore(key8, key8, RangeByIndex(0, -1))
    args.append(3)
    transaction.bzpopmin([key8], 0.5)
    args.append([key8.encode(), b"two", 2.0])
    transaction.bzpopmax([key8], 0.5)
    args.append([key8.encode(), b"four", 4.0])
    # key8 now only contains one member ("three")
    transaction.zrandmember(key8)
    args.append(b"three")
    transaction.zrandmember_count(key8, 1)
    args.append([b"three"])
    transaction.zrandmember_withscores(key8, 1)
    args.append([[b"three", 3.0]])
    transaction.zscan(key8, "0")
    args.append([b"0", [b"three", b"3"]])
    transaction.zscan(key8, "0", match="*", count=20)
    args.append([b"0", [b"three", b"3"]])
    if not await check_if_server_version_lt(glide_client, "8.0.0"):
        transaction.zscan(key8, "0", match="*", count=20, no_scores=True)
        args.append([b"0", [b"three"]])
    transaction.zpopmax(key8)
    args.append({b"three": 3.0})
    transaction.zpopmin(key8)
    args.append({})  # type: ignore
    transaction.zremrangebyscore(key8, InfBound.NEG_INF, InfBound.POS_INF)
    args.append(0)
    transaction.zremrangebylex(key8, InfBound.NEG_INF, InfBound.POS_INF)
    args.append(0)
    transaction.zremrangebyrank(key8, 0, 10)
    args.append(0)
    transaction.zdiffstore(key8, [key8, key8])
    args.append(0)
    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        transaction.zmpop([key8], ScoreFilter.MAX)
        args.append(None)
        transaction.zmpop([key8], ScoreFilter.MAX, 1)
        args.append(None)

    transaction.zadd(key13, {"one": 1.0, "two": 2.0})
    args.append(2)
    transaction.zdiff([key13, key8])
    args.append([b"one", b"two"])
    transaction.zdiff_withscores([key13, key8])
    args.append({b"one": 1.0, b"two": 2.0})
    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        transaction.zintercard([key13, key8])
        args.append(0)
        transaction.zintercard([key13, key8], 1)
        args.append(0)

    transaction.zadd(key14, {"one": 1, "two": 2})
    args.append(2)
    transaction.zadd(key15, {"one": 1.0, "two": 2.0, "three": 3.5})
    args.append(3)
    transaction.zinter([key14, key15])
    args.append([b"one", b"two"])
    transaction.zinter_withscores(cast(List[Union[str, bytes]], [key14, key15]))
    args.append({b"one": 2.0, b"two": 4.0})
    transaction.zinterstore(key8, cast(List[Union[str, bytes]], [key14, key15]))
    args.append(2)
    transaction.zunion([key14, key15])
    args.append([b"one", b"three", b"two"])
    transaction.zunion_withscores(cast(List[Union[str, bytes]], [key14, key15]))
    args.append({b"one": 2.0, b"two": 4.0, b"three": 3.5})
    transaction.zunionstore(
        key8, cast(List[Union[str, bytes]], [key14, key15]), AggregationType.MAX
    )
    args.append(3)

    transaction.pfadd(key10, ["a", "b", "c"])
    args.append(1)
    transaction.pfmerge(key10, [])
    args.append(OK)
    transaction.pfcount([key10])
    args.append(3)

    transaction.setbit(key19, 1, 1)
    args.append(0)
    transaction.getbit(key19, 1)
    args.append(1)

    transaction.set(key20, "foobar")
    args.append(OK)
    transaction.bitcount(key20)
    args.append(26)
    transaction.bitcount(key20, OffsetOptions(1, 1))
    args.append(6)
    transaction.bitpos(key20, 1)
    args.append(1)

    if not await check_if_server_version_lt(glide_client, "6.0.0"):
        transaction.bitfield_read_only(
            key20, [BitFieldGet(SignedEncoding(5), BitOffset(3))]
        )
        args.append([6])

    transaction.set(key19, "abcdef")
    args.append(OK)
    transaction.bitop(BitwiseOperation.AND, key19, [key19, key20])
    args.append(6)
    transaction.get(key19)
    args.append(b"`bc`ab")
    transaction.bitfield(
        key20, [BitFieldSet(UnsignedEncoding(10), BitOffsetMultiplier(3), 4)]
    )
    args.append([609])


async def helper3(
    transaction,
    glide_client,
    key5,
    key6,
    key7,
    key9,
    value,
    value_bytes,
    value2,
    value2_bytes,
    value3,
    value3_bytes,
    args,
):
    transaction.lpush(key5, [value, value, value2, value2])
    args.append(4)
    transaction.llen(key5)
    args.append(4)
    transaction.lindex(key5, 0)
    args.append(value2_bytes)
    transaction.lpop(key5)
    args.append(value2_bytes)
    transaction.lrem(key5, 1, value)
    args.append(1)
    transaction.ltrim(key5, 0, 1)
    args.append(OK)
    transaction.lrange(key5, 0, -1)
    args.append([value2_bytes, value_bytes])
    transaction.lmove(key5, key6, ListDirection.LEFT, ListDirection.LEFT)
    args.append(value2_bytes)
    transaction.blmove(key6, key5, ListDirection.LEFT, ListDirection.LEFT, 1)
    args.append(value2_bytes)
    transaction.lpop_count(key5, 2)
    args.append([value2_bytes, value_bytes])
    transaction.linsert(key5, InsertPosition.BEFORE, "non_existing_pivot", "element")
    args.append(0)
    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        transaction.lpush(key5, [value, value2])
        args.append(2)
        transaction.lmpop([key5], ListDirection.LEFT)
        args.append({key5.encode(): [value2_bytes]})
        transaction.blmpop([key5], ListDirection.LEFT, 0.1)
        args.append({key5.encode(): [value_bytes]})

    transaction.rpush(key6, [value, value2, value2])
    args.append(3)
    transaction.rpop(key6)
    args.append(value2_bytes)
    transaction.rpop_count(key6, 2)
    args.append([value2_bytes, value_bytes])

    transaction.rpushx(key9, ["_"])
    args.append(0)
    transaction.lpushx(key9, ["_"])
    args.append(0)
    transaction.lpush(key9, [value, value2, value3])
    args.append(3)
    transaction.blpop([key9], 1)
    args.append([key9.encode(), value3_bytes])
    transaction.brpop([key9], 1)
    args.append([key9.encode(), value_bytes])
    transaction.lset(key9, 0, value2)
    args.append(OK)

    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.smismember(key7, ["foo", "baz"])
    args.append([True, False])
    transaction.sdiffstore(key7, [key7])
    args.append(2)
    transaction.srem(key7, ["foo"])
    args.append(1)
    transaction.sscan(key7, "0")
    args.append([b"0", [b"bar"]])
    transaction.sscan(key7, "0", match="*", count=10)
    args.append([b"0", [b"bar"]])
    transaction.smembers(key7)
    args.append({b"bar"})
    transaction.scard(key7)
    args.append(1)
    transaction.sismember(key7, "bar")
    args.append(True)
    transaction.spop(key7)
    args.append(b"bar")
    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.sunionstore(key7, [key7, key7])
    args.append(2)
    transaction.sinter([key7, key7])
    args.append({b"foo", b"bar"})
    transaction.sunion([key7, key7])
    args.append({b"foo", b"bar"})
    transaction.sinterstore(key7, [key7, key7])
    args.append(2)
    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        transaction.sintercard([key7, key7])
        args.append(2)
        transaction.sintercard([key7, key7], 1)
        args.append(1)
    transaction.sdiff([key7, key7])
    args.append(set())
    transaction.spop_count(key7, 4)
    args.append({b"foo", b"bar"})
    transaction.smove(key7, key7, "non_existing_member")
    args.append(False)


async def helper2(
    transaction,
    glide_client,
    key,
    key2,
    key3,
    key4,
    value,
    value_bytes,
    value2,
    value2_bytes,
    args,
):
    transaction.incr(key3)
    args.append(1)
    transaction.incrby(key3, 2)
    args.append(3)

    transaction.decr(key3)
    args.append(2)
    transaction.decrby(key3, 2)
    args.append(0)

    transaction.incrbyfloat(key3, 0.5)
    args.append(0.5)

    transaction.unlink([key3])
    args.append(1)

    transaction.ping()
    args.append(b"PONG")

    transaction.config_set({"timeout": "1000"})
    args.append(OK)
    transaction.config_get(["timeout"])
    args.append({b"timeout": b"1000"})
    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        transaction.config_set({"timeout": "2000", "cluster-node-timeout": "16000"})
        args.append(OK)
        transaction.config_get(["timeout", "cluster-node-timeout"])
        args.append({b"timeout": b"2000", b"cluster-node-timeout": b"16000"})

    transaction.hset(key4, {key: value, key2: value2})
    args.append(2)
    transaction.hget(key4, key2)
    args.append(value2_bytes)
    transaction.hlen(key4)
    args.append(2)
    transaction.hvals(key4)
    args.append([value_bytes, value2_bytes])
    transaction.hkeys(key4)
    args.append([key.encode(), key2.encode()])
    transaction.hsetnx(key4, key, value)
    args.append(False)
    transaction.hincrby(key4, key3, 5)
    args.append(5)
    transaction.hincrbyfloat(key4, key3, 5.5)
    args.append(10.5)

    transaction.hexists(key4, key)
    args.append(True)
    transaction.hmget(key4, [key, "nonExistingField", key2])
    args.append([value_bytes, None, value2_bytes])
    transaction.hgetall(key4)
    key3_bytes = key3.encode()
    args.append(
        {
            key.encode(): value_bytes,
            key2.encode(): value2_bytes,
            key3_bytes: b"10.5",
        }
    )
    transaction.hdel(key4, [key, key2])
    args.append(2)
    transaction.hscan(key4, "0")
    args.append([b"0", [key3.encode(), b"10.5"]])
    transaction.hscan(key4, "0", match="*", count=10)
    args.append([b"0", [key3.encode(), b"10.5"]])
    if not await check_if_server_version_lt(glide_client, "8.0.0"):
        transaction.hscan(key4, "0", match="*", count=10, no_values=True)
        args.append([b"0", [key3.encode()]])
    transaction.hrandfield(key4)
    args.append(key3_bytes)
    transaction.hrandfield_count(key4, 1)
    args.append([key3_bytes])
    transaction.hrandfield_withvalues(key4, 1)
    args.append([[key3_bytes, b"10.5"]])
    transaction.hstrlen(key4, key3)
    args.append(4)

    transaction.client_getname()
    args.append(None)


async def helper1(
    transaction, glide_client, key, key2, value, value_bytes, value2, value2_bytes, args
):
    transaction.dbsize()
    args.append(0)

    transaction.set(key, value)
    args.append(OK)
    transaction.setrange(key, 0, value)
    args.append(len(value))
    transaction.get(key)
    args.append(value_bytes)
    transaction.get(key.encode())
    args.append(value_bytes)
    transaction.type(key)
    args.append(b"string")
    transaction.type(key.encode())
    args.append(b"string")
    transaction.echo(value)
    args.append(value_bytes)
    transaction.echo(value.encode())
    args.append(value_bytes)
    transaction.strlen(key)
    args.append(len(value))
    transaction.strlen(key.encode())
    args.append(len(value))
    transaction.append(key, value)
    args.append(len(value) * 2)

    transaction.persist(key)
    args.append(False)
    transaction.ttl(key)
    args.append(-1)
    if not await check_if_server_version_lt(glide_client, "7.0.0"):
        transaction.expiretime(key)
        args.append(-1)
        transaction.pexpiretime(key)
        args.append(-1)

    if not await check_if_server_version_lt(glide_client, "6.2.0"):
        transaction.copy(key, key2, replace=True)
        args.append(True)

    transaction.rename(key, key2)
    args.append(OK)

    transaction.exists([key2])
    args.append(1)
    transaction.touch([key2])
    args.append(1)

    transaction.delete([key2])
    args.append(1)
    transaction.get(key2)
    args.append(None)

    transaction.set(key, value)
    args.append(OK)
    transaction.getrange(key, 0, -1)
    args.append(value_bytes)
    transaction.getdel(key)
    args.append(value_bytes)
    transaction.getdel(key)
    args.append(None)

    transaction.mset({key: value, key2: value2})
    args.append(OK)
    transaction.msetnx({key: value, key2: value2})
    args.append(False)
    transaction.mget([key, key2])
    args.append([value_bytes, value2_bytes])

    transaction.renamenx(key, key2)
    args.append(False)


async def exec_batch(
    glide_client: TGlideClient,
    batch: BaseBatch,
    route: Optional[TSingleNodeRoute] = None,
    timeout: Optional[int] = None,
    raise_on_error: bool = False,
) -> Optional[List[TResult]]:
    if isinstance(glide_client, GlideClient):
        return await cast(GlideClient, glide_client).exec(
            cast(Batch, batch), raise_on_error, timeout
        )
    else:
        return await cast(GlideClusterClient, glide_client).exec(
            cast(ClusterBatch, batch),
            raise_on_error,
            route,
            timeout,
        )


@pytest.mark.anyio
class TestBatch:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_with_different_slots(
        self, glide_client: GlideClusterClient
    ):
        transaction = ClusterBatch(is_atomic=True)
        transaction.set("key1", "value1")
        transaction.set("key2", "value2")
        with pytest.raises(RequestError, match="CrossSlot"):
            await glide_client.exec(transaction, raise_on_error=True)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_custom_command(self, glide_client: TGlideClient):
        key = get_random_string(10)
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.custom_command(["HSET", key, "foo", "bar"])
        transaction.custom_command(["HGET", key, "foo"])
        result = await exec_batch(glide_client, transaction)
        assert result == [1, b"bar"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_custom_unsupported_command(
        self, glide_client: TGlideClient
    ):
        key = get_random_string(10)
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.custom_command(["WATCH", key])
        with pytest.raises(RequestError) as e:
            await exec_batch(glide_client, transaction)

        assert "not allowed" in str(e)  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_discard_command(self, glide_client: TGlideClient):
        key = get_random_string(10)
        await glide_client.set(key, "1")
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )

        transaction.custom_command(["INCR", key])
        transaction.custom_command(["DISCARD"])
        with pytest.raises(RequestError) as e:
            await exec_batch(glide_client, transaction)
        assert "EXEC without MULTI" in str(e)  # TODO : add an assert on EXEC ABORT
        value = await glide_client.get(key)
        assert value == b"1"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_exec_abort(self, glide_client: TGlideClient):
        key = get_random_string(10)
        transaction = BaseBatch(is_atomic=True)
        transaction.custom_command(["INCR", key, key, key])
        with pytest.raises(RequestError) as e:
            await exec_batch(glide_client, transaction)
        assert "wrong number of arguments" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_batch(self, glide_client: GlideClusterClient, is_atomic):
        assert await glide_client.custom_command(["FLUSHALL"]) == OK
        keyslot = get_random_string(3) if is_atomic else None
        batch = ClusterBatch(is_atomic=is_atomic)
        batch.info()
        publish_result_index = 1
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            batch.publish("test_message", keyslot or get_random_string(3), False)
        else:
            batch.publish("test_message", keyslot or get_random_string(3), True)

        expected = await batch_test(batch, glide_client, keyslot)

        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            batch.pubsub_shardchannels()
            expected.append(cast(TResult, []))
            batch.pubsub_shardnumsub()
            expected.append(cast(TResult, {}))

        result = await glide_client.exec(batch, raise_on_error=True)
        assert isinstance(result, list)

        info_response = result[0]
        if is_atomic:
            assert isinstance(info_response, bytes)
            assert b"# Memory" in info_response
        else:
            assert isinstance(info_response, dict)
            assert len(info_response) > 0
            for node_info in info_response.values():
                assert isinstance(node_info, bytes)
                assert b"# Memory" in node_info

        assert result[publish_result_index] == 0
        assert result[2:] == expected

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_return_null_on_watch_transaction_failures(
        self, glide_client: TGlideClient, request
    ):
        is_cluster = isinstance(glide_client, GlideClusterClient)
        client2 = await create_client(
            request,
            is_cluster,
        )
        keyslot = get_random_string(3)
        transaction = (
            ClusterBatch(is_atomic=True) if is_cluster else Batch(is_atomic=True)
        )
        transaction.get(keyslot)
        result1 = await glide_client.watch([keyslot])
        assert result1 == OK

        result2 = await client2.set(keyslot, "foo")
        assert result2 == OK

        result3 = await exec_batch(glide_client, transaction)
        assert result3 is None

        await client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("is_atomic", [True, False])
    async def test_batch_large_values(self, request, cluster_mode, protocol, is_atomic):
        glide_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, request_timeout=5000
        )
        length = 2**25  # 33mb
        key = "0" * length
        value = "0" * length
        batch = Batch(is_atomic=is_atomic)
        batch.set(key, value)
        batch.get(key)
        result = await exec_batch(glide_client, batch, raise_on_error=True)
        assert isinstance(result, list)
        assert result[0] == OK
        assert result[1] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_batch(self, glide_client: GlideClient, is_atomic: bool):
        assert await glide_client.custom_command(["FLUSHALL"]) == OK
        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(10))  # to get the same slot
        key1 = "{{{}}}:{}".format(
            keyslot, get_random_string(10)
        )  # to get the same slot
        value = get_random_string(5)
        transaction = Batch(is_atomic=is_atomic)
        transaction.info()
        transaction.select(1)
        transaction.move(key, 0)
        transaction.set(key, value)
        transaction.get(key)
        transaction.hset("user:1", {"name": "Alice", "age": "30"})
        transaction.hset("user:2", {"name": "Bob", "age": "25"})
        transaction.lpush(key1, ["2", "1"])
        transaction.sort(
            key1,
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        transaction.sort_store(
            key1,
            "newSortedKey",
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        transaction.select(0)
        transaction.get(key)
        transaction.publish("test_message", "test_channel")
        expected = await batch_test(transaction, glide_client, keyslot)
        result = await glide_client.exec(transaction, raise_on_error=True)
        assert isinstance(result, list)
        assert isinstance(result[0], bytes)
        result[0] = result[0].decode()
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:5] == [OK, False, OK, value.encode()]
        assert result[5:13] == [2, 2, 2, [b"Bob", b"Alice"], 2, OK, None, 0]
        assert result[13:] == expected

    async def test_transaction_clear(self):
        transaction = Batch(is_atomic=True)
        transaction.info()
        transaction.select(1)
        transaction.clear()
        assert len(transaction.commands) == 0

    @pytest.mark.skip_if_version_below("6.2.0")
    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_copy_transaction(self, glide_client: GlideClient):
        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(10))  # to get the same slot
        key1 = "{{{}}}:{}".format(
            keyslot, get_random_string(10)
        )  # to get the same slot
        value = get_random_string(5)
        transaction = Batch(is_atomic=True)
        transaction.select(1)
        transaction.set(key, value)
        transaction.copy(key, key1, 1, replace=True)
        transaction.get(key1)
        result = await glide_client.exec(transaction, raise_on_error=True)
        assert result is not None
        assert result[2] is True
        assert result[3] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_chaining_calls(self, glide_client: TGlideClient):
        key = get_random_string(3)

        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.set(key, "value").get(key).delete([key])

        result = await exec_batch(glide_client, transaction)
        assert result == [OK, b"value", 1]

    # The object commands are tested here instead of transaction_test because they have special requirements:
    # - OBJECT FREQ and OBJECT IDLETIME require specific maxmemory policies to be set on the config
    # - we cannot reliably predict the exact response values for OBJECT FREQ, OBJECT IDLETIME, and OBJECT REFCOUNT
    # - OBJECT ENCODING is tested here since all the other OBJECT commands are tested here
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_object_commands(
        self, glide_client: TGlideClient, cluster_mode: bool
    ):
        string_key = get_random_string(10)
        maxmemory_policy_key = "maxmemory-policy"
        config = await glide_client.config_get([maxmemory_policy_key])
        config_decoded = cast(dict, convert_bytes_to_string_object(config))
        assert config_decoded is not None
        maxmemory_policy = cast(str, config_decoded.get(maxmemory_policy_key))

        try:
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_client, GlideClient)
                else ClusterBatch(is_atomic=True)
            )
            transaction.set(string_key, "foo")
            transaction.object_encoding(string_key)
            transaction.object_refcount(string_key)
            # OBJECT FREQ requires a LFU maxmemory-policy
            transaction.config_set({maxmemory_policy_key: "allkeys-lfu"})
            transaction.object_freq(string_key)
            # OBJECT IDLETIME requires a non-LFU maxmemory-policy
            transaction.config_set({maxmemory_policy_key: "allkeys-random"})
            transaction.object_idletime(string_key)

            response = await exec_batch(glide_client, transaction)
            assert response is not None
            assert response[0] == OK  # transaction.set(string_key, "foo")
            assert response[1] == b"embstr"  # transaction.object_encoding(string_key)
            # transaction.object_refcount(string_key)
            assert cast(int, response[2]) >= 0
            # transaction.config_set({maxmemory_policy_key: "allkeys-lfu"})
            assert response[3] == OK
            assert cast(int, response[4]) >= 0  # transaction.object_freq(string_key)
            # transaction.config_set({maxmemory_policy_key: "allkeys-random"})
            assert response[5] == OK
            # transaction.object_idletime(string_key)
            assert cast(int, response[6]) >= 0
        finally:
            await glide_client.config_set({maxmemory_policy_key: maxmemory_policy})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_xinfo_stream(
        self, glide_client: TGlideClient, cluster_mode: bool, protocol
    ):
        key = get_random_string(10)
        stream_id1_0 = "1-0"
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.xadd(key, [("foo", "bar")], StreamAddOptions(stream_id1_0))
        transaction.xinfo_stream(key)
        transaction.xinfo_stream_full(key)

        response = await exec_batch(glide_client, transaction)
        assert response is not None
        # transaction.xadd(key, [("foo", "bar")], StreamAddOptions(stream_id1_0))
        assert response[0] == stream_id1_0.encode()
        # transaction.xinfo_stream(key)
        info = cast(dict, response[1])
        assert info.get(b"length") == 1
        assert info.get(b"groups") == 0
        assert info.get(b"first-entry") == [stream_id1_0.encode(), [b"foo", b"bar"]]
        assert info.get(b"first-entry") == info.get(b"last-entry")

        # transaction.xinfo_stream_full(key)
        info_full = cast(dict, response[2])
        assert info_full.get(b"length") == 1
        assert info_full.get(b"entries") == [[stream_id1_0.encode(), [b"foo", b"bar"]]]
        assert info_full.get(b"groups") == []

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_lastsave(
        self, glide_client: TGlideClient, cluster_mode: bool
    ):
        yesterday = date.today() - timedelta(1)
        yesterday_unix_time = time.mktime(yesterday.timetuple())
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.lastsave()
        response = await exec_batch(glide_client, transaction)
        assert isinstance(response, list)
        lastsave_time = response[0]
        assert isinstance(lastsave_time, int)
        assert lastsave_time > yesterday_unix_time

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lolwut_transaction(self, glide_client: GlideClusterClient):
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.lolwut().lolwut(5).lolwut(parameters=[1, 2]).lolwut(6, [42])
        results = await glide_client.exec(transaction, raise_on_error=True)
        assert results is not None

        for element in results:
            assert isinstance(element, bytes)
            assert b"Redis ver. " in element

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_dump_restore(
        self, glide_client: TGlideClient, cluster_mode, protocol
    ):
        cluster_mode = isinstance(glide_client, GlideClusterClient)
        keyslot = get_random_string(3)
        key1 = "{{{}}}:{}".format(
            keyslot, get_random_string(10)
        )  # to get the same slot
        key2 = "{{{}}}:{}".format(keyslot, get_random_string(10))

        # Verify Dump
        transaction = (
            ClusterBatch(is_atomic=True) if cluster_mode else Batch(is_atomic=True)
        )
        transaction.set(key1, "value")
        transaction.dump(key1)
        result1 = await exec_batch(glide_client, transaction)
        assert result1 is not None
        assert isinstance(result1, list)
        assert result1[0] == OK
        assert isinstance(result1[1], bytes)

        # Verify Restore - use result1[1] from above
        transaction = (
            ClusterBatch(is_atomic=True) if cluster_mode else Batch(is_atomic=True)
        )
        transaction.restore(key2, 0, result1[1])
        transaction.get(key2)
        result2 = await exec_batch(glide_client, transaction)
        assert result2 is not None
        assert isinstance(result2, list)
        assert result2[0] == OK
        assert result2[1] == b"value"

        # Restore with frequency and idletime both set.
        with pytest.raises(RequestError) as e:
            await glide_client.restore(
                key2, 0, b"", replace=True, idletime=-10, frequency=10
            )
        assert "syntax error: IDLETIME and FREQ cannot be set at the same time." in str(
            e
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_function_dump_restore(
        self, glide_client: TGlideClient, cluster_mode, protocol
    ):
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            # Setup (will not verify)
            assert await glide_client.function_flush() == OK
            lib_name = f"mylib_{get_random_string(10)}"
            func_name = f"myfun_{get_random_string(10)}"
            code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_client, GlideClient)
                else ClusterBatch(is_atomic=True)
            )
            transaction.function_load(code, True)

            # Verify function_dump
            transaction.function_dump()
            result1 = await exec_batch(glide_client, transaction)
            assert result1 is not None
            assert isinstance(result1, list)
            assert isinstance(result1[1], bytes)

            # Verify function_restore - use result1[2] from above
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_client, GlideClient)
                else ClusterBatch(is_atomic=True)
            )
            transaction.function_restore(result1[1], FunctionRestorePolicy.REPLACE)
            # For the cluster mode, PRIMARY SlotType is required to avoid the error:
            #  "RequestError: An error was signalled by the server -
            #   ReadOnly: You can't write against a read only replica."
            result2 = await exec_batch(
                glide_client, transaction, SlotIdRoute(SlotType.PRIMARY, 1)
            )

            assert result2 is not None
            assert isinstance(result2, list)
            assert result2[0] == OK

            # Test clean up
            await glide_client.function_flush()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_timeout(self, glide_client: TGlideClient, is_atomic: bool):
        assert await glide_client.custom_command(["FLUSHALL"]) == OK

        batch = (
            Batch(is_atomic=is_atomic)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=is_atomic)
        )

        batch.custom_command(["DEBUG", "SLEEP", "0.5"])  # Sleep for 0.5 second

        # Expect a timeout on short timeout window
        with pytest.raises(TimeoutError, match="timed out"):
            await exec_batch(glide_client, batch, raise_on_error=True, timeout=100)

        # Wait for sleep to finish
        time.sleep(0.5)

        # Execute the same batch again with a longer timeout, should succeed now
        result = await exec_batch(
            glide_client, batch, raise_on_error=True, timeout=1000
        )
        assert result is not None
        assert len(result) == 1  # Should contain only the result of DEBUG SLEEP

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_deprecated_transaction_classes(self, glide_client: TGlideClient):
        """Tests that the deprecated Transaction and ClusterTransaction classes still work.

        Verifies that they correctly set is_atomic=True and execute commands atomically.
        """
        key = get_random_string(10)
        value = get_random_string(10)

        transaction: Union[Transaction, ClusterTransaction]
        if isinstance(glide_client, GlideClient):
            transaction = Transaction()
        else:
            transaction = ClusterTransaction()

        assert transaction.is_atomic is True

        # Add some simple commands
        transaction.set(key, value)
        transaction.get(key)

        result = await exec_batch(glide_client, transaction, raise_on_error=True)
        assert isinstance(result, list)
        assert result[0] == OK
        assert result[1] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_multislot(self, glide_client: GlideClusterClient):
        num_keys = 10
        keys = [get_random_string(10) for _ in range(num_keys)]
        values = [get_random_string(5) for _ in range(num_keys)]

        mapping = dict(zip(keys, values))
        assert await glide_client.mset(mapping) == OK

        batch = ClusterBatch(is_atomic=False)
        batch.mget(keys)

        result = await exec_batch(glide_client, batch)

        expected = [v.encode() for v in values]

        assert isinstance(result, list)
        assert result[0] == expected

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_all_nodes(self, glide_client: GlideClusterClient):
        config_key = "maxmemory"
        config_value = "987654321"
        original_config = await glide_client.config_get([config_key])
        original_value = original_config.get(config_key.encode())

        try:
            batch = ClusterBatch(is_atomic=False)
            batch.config_set({config_key: config_value})

            result = await glide_client.exec(batch, raise_on_error=True)

            assert result == ["OK"]

            batch = ClusterBatch(is_atomic=False)
            batch.info()
            result = await exec_batch(glide_client, batch, raise_on_error=True)

            assert isinstance(result, list)
            assert len(result) == 1
            assert isinstance(result[0], dict)

            for info in result[0].values():
                assert isinstance(info, bytes)
            # Check if the config change is reflected in the info output
            assert f"{config_key}:{config_value}" in info.decode()  # type: ignore # noqa
            await glide_client.flushall(FlushMode.ASYNC)
        finally:
            if not isinstance(original_value, (str, bytes)):
                raise ValueError(
                    f"Cannot set config to non-string value: {original_value}"
                )
            await glide_client.config_set({config_key: original_value})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_raise_on_error(
        self, glide_client: GlideClusterClient, is_atomic: bool
    ):
        key = get_random_string(10)
        # Ensure key2 is in the same slot as key for atomic transactions
        key2 = f"{{{key}}}:{get_random_string(10)}"  # type: ignore # noqa

        batch = (
            ClusterBatch(is_atomic=is_atomic)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=is_atomic)
        )

        batch.set(key, "hello")
        batch.lpop(key)
        batch.delete([key])
        batch.rename(key, key2)

        # Test with raise_on_error=False
        result = await exec_batch(glide_client, batch, raise_on_error=False)

        assert result is not None
        assert len(result) == 4

        assert result[0] == "OK"  # set(key, "hello")

        assert isinstance(result[1], RequestError)  # lpop(key)
        assert "WRONGTYPE" in str(result[1])

        assert result[2] == 1  # delete([key])

        assert isinstance(result[3], RequestError)  # rename(key, key2)
        assert "no such key" in str(result[3])

        # Test with raise_on_error=True
        with pytest.raises(RequestError) as e:
            await exec_batch(glide_client, batch, raise_on_error=True)

        assert "WRONGTYPE" in str(e.value)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_batch_route(
        self,
        glide_client: GlideClusterClient,
        protocol: ProtocolVersion,
        is_atomic: bool,
    ):
        if await check_if_server_version_lt(glide_client, "6.0.0"):
            pytest.skip("CONFIG RESETSTAT requires redis >= 6.0")

        assert await glide_client.config_resetstat() == OK

        key = get_random_string(10)
        value_bytes = b"value"

        batch = ClusterBatch(is_atomic=is_atomic)
        batch.set(key, value_bytes)
        batch.get(key)

        route = SlotKeyRoute(slot_type=SlotType.PRIMARY, slot_key=key)
        results = await glide_client.exec(
            batch, raise_on_error=True, route=route, timeout=2000
        )

        assert results == [OK, value_bytes]

        # Check that no MOVED error occurred by inspecting errorstats on all nodes
        error_stats_dict = await glide_client.info(
            sections=[InfoSection.ERROR_STATS], route=AllNodes()
        )
        assert isinstance(error_stats_dict, dict)

        for node_address, node_info in error_stats_dict.items():
            assert isinstance(node_info, bytes)
            # Ensure the errorstats section indicates no errors reported for this test
            # It should only contain the header line.
            assert (
                node_info.strip() == b"# Errorstats"
            ), f"Node {node_address.decode()} reported errors: {node_info.decode()}"
