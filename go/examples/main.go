package main

import (
	"fmt"
	"log"
	"time"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/options"
	"github.com/valkey-io/valkey-glide/go/integTest"
)

func main() {
	host := "localhost"
	port := 6379

	// Create a new GlideClient
	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.NewGlideClient(config)
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}

	// Create a new GlideClient
	clientNormal, err := api.NewGlideClient(config)
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}

	//Non transaction
	resultSet, err := clientNormal.Set("key123", "Hello")
	if err != nil {
		log.Fatal("Set error: ", err)
	}
	fmt.Println(resultSet)
	//Non transaction
	resultSet, err = clientNormal.Set("key345", "Hello")
	if err != nil {
		log.Fatal("Set error: ", err)
	}
	fmt.Println(resultSet)
	resultWatch, err := clientNormal.Watch([]string{"key123", "key345"})
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println(resultWatch)

	resultUnWatch, err := clientNormal.Unwatch()
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println(resultUnWatch)
	tx := api.NewTransaction(client)
	tx.Set("key123", "Glide")
	tx.Watch([]string{"key123"})
	tx.Set("key1", "Glide")
	tx.Set("key2", "Hello")
	tx.Set("key3", "KeyToDelete")
	tx.Get("key1")
	tx.Get("key2")
	tx.Get("key3")
	tx.Del([]string{"key3"})
	tx.Append("key2", "_World")
	tx.Get("key2")
	tx.Set("key123", "Valkey")
	tx.Get("key123")
	tx.Type("key123")
	tx.Exists([]string{"key1", "key2"})
	tx.Touch([]string{"key1", "key2"})
	tx.PTTL("key1")
	tx.TTL("key1")
	tx.Set("UnlinkKey", "Hello")
	tx.Unlink([]string{"UnlinkKey"})
	tx.Persist("key1")
	tx.ObjectEncoding("key123")
	tx.ObjectIdleTime("key1")
	//tx.ObjectFreq("key1") //config
	tx.ObjectRefCount("key1")
	tx.Set("key4", "newkey3")
	tx.Rename("key2", "newkey2")
	tx.RenameNX("key4", "newkey4") //newKey must be not existing
	tx.Copy("key2", "keyCopy")
	tx.Dump("key123")
	tx.Del([]string{"key2"})
	//tx.Restore("key2", 0, "\x00\x0bHello World\x0b\x00\xad\xb7\xa9\x8fcM3Y")
	tx.PExpire("key4", 100)
	tx.Expire("key4", 100)
	tx.ExpireTime("key4")
	tx.PExpireTime("key4")
	tx.PExpireAt("key4", 100)
	tx.PExpire("key4", 100)
	tx.LPush("keySort", []string{"1", "3", "2", "4"})
	tx.Sort("keySort")
	//tx.sort_ro
	tx.SortStore("keySort", "key1_store")
	tx.Wait(10, 10)

	tx.SAdd("someKey", []string{"value", "value1", "value2"})
	tx.SRem("someKey", []string{"value1"})
	tx.SMembers("someKey")
	tx.SCard("someKey")
	tx.SIsMember("someKey", "value2")
	tx.SPop("somekey")
	tx.SRandMember("somekey")
	tx.SAdd("someKey1", []string{"value", "value1", "value2"})
	tx.SDiff([]string{"someKey", "someKey1"})
	tx.SDiffStore("someKey3", []string{"someKey"})
	tx.SInterCard([]string{"someKey", "someKey1"})
	tx.SUnionStore("destinationKey", []string{"someKey3"})
	tx.SUnion([]string{"someKey3", "someKey1"})
	tx.SMove("destinationKey1", "someKey3", "value1")
	tx.SInterStore("destinationKey2", []string{"destinationKey1"})
	tx.SInter([]string{"destinationKey1", "someKey3"})
	tx.SMIsMember("someKey", []string{"value", "value1", "value2"})
	tx.SScan("someKey", "0")

	tx.LPush("keylist", []string{"1", "3", "2", "4"})
	tx.LPop("keylist")
	tx.LPos("keylist", "1")
	tx.LRange("keylist", 1, 4)
	tx.RPush("keylist", []string{"a", "b"})
	tx.RPop("keylist")
	tx.LLen("keylist")
	tx.LRem("keylist", 0, "b")
	tx.LTrim("keylist", 3, 4)
	tx.LIndex("keylist", 1)

	tx.RPush("my_list", []string{"hello", "world"})
	tx.LInsert("my_list", options.Before, "world", "there")
	tx.LRange("my_list", 0, -1)

	tx.RPush("list_a", []string{"a", "b", "c", "d", "e"})
	tx.RPush("list_b", []string{"f", "g", "h", "i", "j"})
	tx.BLPop([]string{"list_a", "list_b"}, 0.5)
	tx.BRPop([]string{"list_a", "list_b"}, 0.5)
	tx.LPushX("my_list", []string{"value2", "value3"})
	tx.RPushX("my_list", []string{"value2", "value3"})
	//tx.LMPop([]string{"my_list"}, options.Left)
	//tx.BLMPop([]string{"my_listPOP"}, options.Left, 0.1)

	tx.LPush("my_list1", []string{"two", "one"})
	tx.LPush("my_list2", []string{"four", "three"})
	tx.LMove("my_list1", "my_list2", options.Left, options.Left)
	tx.BLMove("my_list1", "my_list2", options.Left, options.Left, 0.1)

	//hash
	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	tx.HSet("my_hash", fields)
	tx.HGet("my_hash", "field1")
	tx.HMGet("my_hash", []string{"field1", "field2"})
	tx.HGetAll("my_hash")
	tx.HLen("my_hash")
	tx.HSetNX("my_hash", "field3", "value")
	tx.HVals("my_hash")
	tx.HKeys("my_hash")
	tx.HStrLen("my_hash", "field1")

	fields = map[string]string{
		"field1": "10",
		"field2": "14",
	}
	tx.HSet("my_hash_int", fields)
	tx.HIncrBy("my_hash_int", "field1", 1)
	fields = map[string]string{
		"field1": "10",
		"field2": "14",
	}
	tx.HSet("my_hash_loat", fields)
	tx.HIncrByFloat("my_hash_loat", "field1", 1.5)
	tx.HRandField("my_hash")
	tx.HRandFieldWithCount("my_hash", 2)
	tx.HRandFieldWithCountWithValues("my_hash", 2)
	tx.HScan("my_hash", "0")

	//Geospatial indices commands
	key := uuid.New().String()
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}
	tx.GeoAdd(key, membersToCoordinates)
	tx.GeoPos(key, []string{"Palermo", "Catania"})
	tx.GeoHash(key, []string{"Palermo", "Catania"})
	tx.GeoDist(key, "Palermo", "Catania")

	tx.GeoSearch(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)
	tx.GeoSearch(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)
	source := uuid.New().String()
	destination := uuid.New().String()
	tx.GeoSearchStore(
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)

	//Bitmap Commands
	commands := []options.BitFieldSubCommands{
		options.NewBitFieldGet(options.SignedInt, 8, 16),
		options.NewBitFieldOverflow(options.SAT),
		options.NewBitFieldSet(options.UnsignedInt, 4, 0, 7),
		options.NewBitFieldIncrBy(options.SignedInt, 5, 100, 1),
	}
	tx.BitField("mykey", commands)
	tx.SetBit("my_key", 1, 1)
	tx.GetBit("my_key", 1)
	tx.BitCount("my_key")

	bitopkey1 := "{bitop_test}key1"
	bitopkey2 := "{bitop_test}key2"
	destKey := "{bitop_test}dest"

	// Set initial values
	client.Set(bitopkey1, "foobar")
	client.Set(bitopkey2, "abcdef")
	commandsRO := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	tx.BitFieldRO("mykey", commandsRO)

	// Perform BITOP AND
	tx.BitOp(options.AND, destKey, []string{bitopkey1, bitopkey2})
	tx.BitPos("mykey", 1)

	//Sorted Set Commands
	tx.ZAdd("keySet", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	tx.ZRem("keySet", []string{"one", "two", "nonMember"})
	tx.ZRange("keySet", options.NewRangeByIndexQuery(0, -1))
	tx.ZAdd("keyZRangeStore", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
	tx.ZRangeStore("dest", "keyZRangeStore", query)
	tx.ZRemRangeByScore("keySet", *options.NewRangeByScoreQuery(
		options.NewInfiniteScoreBoundary(options.NegativeInfinity),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
	))
	tx.ZRemRangeByRank("keySet", 1, 3)
	tx.ZAdd("keyScore", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	tx.ZScore("keyScore", "three")
	tx.ZCard("keyScore")
	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
	)
	tx.ZCount("keySet", *zCountRange)
	tx.ZIncrBy("keySet", 3.0, "two")

	tx.ZAdd("keySetPop", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	tx.ZPopMin("keySetPop")
	tx.ZRank("keySetPop", "two")
	tx.ZRankWithScore("keySetPop", "two")

	client.ZAdd("key1ZInterStore", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2ZInterStore", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	tx.ZInterStoreWithOptions(
		"dest",
		options.KeyArray{
			Keys: []string{"key1ZInterStore", "key2ZInterStore"},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)

	tx.ZRevRank("keySet", "two")
	tx.ZAdd("keyRankWithScore", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	tx.ZRevRankWithScore("keyRankWithScore", "two")

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("ZUnionStore1", memberScoreMap1)
	client.ZAdd("ZUnionStore2", memberScoreMap2)

	tx.ZUnionStore(
		"dest",
		options.KeyArray{Keys: []string{"ZUnionStore1", "ZUnionStore2"}},
	)
	tx.ZPopMax("keyRankWithScore")
	opts := options.NewZPopOptions().SetCount(2)
	tx.ZPopMaxWithOptions("keyRankWithScore", *opts)
	tx.ZRemRangeByLex(
		"keyRankWithScore",
		*options.NewRangeByLexQuery(options.NewLexBoundary("a", false), options.NewLexBoundary("c", true)),
	)

	tx.ZAdd("ZLexCount", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	tx.ZLexCount("ZLexCount",
		options.NewRangeByLexQuery(
			options.NewLexBoundary("a", false),
			options.NewLexBoundary("c", true),
		),
	)
	tx.ZRandMember("ZLexCount")
	tx.ZRandMemberWithCount("ZLexCount", 4)
	tx.ZRandMemberWithCountWithScores("ZLexCount", 4)

	tx.ZAdd("keyZAdd1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	tx.ZAdd("keyZAdd2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	tx.ZDiffStore("dest", []string{"keyZAdd1", "keyZAdd2"})

	tx.ZDiff([]string{"keyZAdd1", "keyZAdd2"})
	tx.ZDiffWithScores([]string{"keyZAdd1", "keyZAdd2"})

	tx.ZInter(options.KeyArray{
		Keys: []string{"keyZAdd1", "keyZAdd2"},
	})

	tx.ZInterWithScores(
		options.KeyArray{
			Keys: []string{"keyZAdd1", "keyZAdd2"},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)

	memberScoreMap1 = map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 = map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}
	tx.ZAdd("keyZUnion1", memberScoreMap1)
	tx.ZAdd("keyZUnion2", memberScoreMap2)
	tx.ZUnion(options.KeyArray{Keys: []string{"keyZUnion1", "keyZUnion2"}})
	tx.ZUnionWithScores(
		options.KeyArray{Keys: []string{"keyZUnion1", "keyZUnion2"}},
		options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	tx.BZPopMin([]string{"keyZUnion1", "keyZUnion2"}, 0.5)
	tx.ZAdd("key1ZScan", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	tx.ZScan("key1ZScan", "0")

	tx.ZAdd("keyBZMPop", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	tx.BZMPop([]string{"keyBZMPop"}, options.MAX, float64(0.5))

	tx.BZMPopWithOptions([]string{"keyBZMPop"}, options.MAX, 0.1, *options.NewZMPopOptions().SetCount(2))

	//Scripting and Function
	funcName := "testKey7"
	funcName1 := "testKey8"

	var (
		libraryCode         = integTest.GenerateLuaLibCode("mylib", map[string]string{funcName: "return 42"}, true)
		libraryCodeWithArgs = integTest.GenerateLuaLibCode("mylib", map[string]string{funcName1: "return args[1]"}, true)
	)
	tx.FunctionLoad(libraryCode, true)
	tx.FCall(funcName)
	tx.FCallReadOnly(funcName)

	tx.FunctionLoad(libraryCodeWithArgs, true)
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	tx.FCallWithKeysAndArgs(funcName1, []string{key1, key2}, []string{"3", "4"})
	tx.FCallReadOnly(funcName1)
	tx.FCallReadOnlyWithKeysAndArgs(funcName1, []string{key1, key2}, []string{"3", "4"})
	tx.FunctionFlush()
	tx.FunctionFlushSync()
	tx.FunctionFlushAsync()

	//Stream Commands
	keyStream := "key" + uuid.New().String()
	tx.XAdd(
		keyStream,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
	)
	tx.XTrim(keyStream, *options.NewXTrimOptionsWithMaxLen(0).SetExactTrimming())
	tx.XLen(keyStream)

	counter := 1234572
	counter1 := 12347
	counter++ // Increment counter by 1
	counter1++
	streamId := fmt.Sprintf("%d-%d", counter, counter1)
	keyStream = "KeyStream-" + uuid.New().String()
	optionsXAdd := options.NewXAddOptions().SetId(streamId)
	values := [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	}
	tx.XAddWithOptions(keyStream, values, *optionsXAdd)
	tx.XRead(map[string]string{keyStream: "0-0"})
	tx.XTrim(keyStream, *options.NewXTrimOptionsWithMaxLen(0).SetExactTrimming())
	tx.XInfoStream(keyStream)
	optionsInforStream := options.NewXInfoStreamOptionsOptions().SetCount(2)
	tx.XInfoStreamFullWithOptions(keyStream, optionsInforStream)
	tx.XRange(keyStream,
		options.NewInfiniteStreamBoundary(options.NegativeInfinity),
		options.NewInfiniteStreamBoundary(options.PositiveInfinity))
	tx.XRangeWithOptions(keyStream,
		options.NewInfiniteStreamBoundary(options.NegativeInfinity),
		options.NewInfiniteStreamBoundary(options.PositiveInfinity),
		*options.NewXRangeOptions().SetCount(1))

	counter++ // Increment counter by 1
	counter1++
	streamId1RevRange := fmt.Sprintf("%d-%d", counter, counter1)
	counter++ // Increment counter by 1
	counter1++
	streamId2RevRange := fmt.Sprintf("%d-%d", counter, counter1)
	tx.XAddWithOptions(keyStream, [][]string{{"field1", "value1"}},
		*options.NewXAddOptions().SetId(streamId1RevRange))
	tx.XAddWithOptions(keyStream, [][]string{{"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId2RevRange))
	tx.XRevRange(keyStream,
		options.NewStreamBoundary(streamId1RevRange, true),
		options.NewStreamBoundary(streamId2RevRange, true))

	tx.XDel(keyStream, []string{"0-1", "0-2", "0-3"})

	keyStreamGroup := "KeyStreamG-" + uuid.New().String()
	group := "G" + uuid.New().String()
	consumer := "c" + uuid.New().String()
	counterG := 1234572
	counter1G := 12347
	counterG++ // Increment counter by 1
	counter1G++
	streamIdG := fmt.Sprintf("%d-%d", counterG, counter1G)

	tx.XGroupCreateWithOptions(keyStreamGroup, group, "0",
		*options.NewXGroupCreateOptions().SetMakeStream())
	tx.XGroupCreateConsumer(keyStreamGroup, group, consumer)
	tx.XInfoConsumers(keyStreamGroup, group)
	tx.XGroupDelConsumer(keyStreamGroup, group, consumer)
	tx.XAddWithOptions(
		keyStreamGroup,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamIdG),
	)
	tx.XGroupSetId(keyStreamGroup, group, "0-0")
	optsSetID := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
	tx.XGroupSetIdWithOptions(keyStreamGroup, group, "0-0", *optsSetID)
	tx.XReadGroup(group, consumer, map[string]string{keyStreamGroup: ">"})
	tx.XReadGroupWithOptions(group, consumer, map[string]string{keyStreamGroup: ">"},
		*options.NewXReadGroupOptions().SetCount(1))
	tx.XAutoClaim(keyStreamGroup, group, consumer, 0, "0-0")
	optionsAutoClaim := options.NewXAutoClaimOptions().SetCount(1)
	tx.XAutoClaimWithOptions(keyStreamGroup, group, consumer, 0, "0-0", *optionsAutoClaim)
	tx.XAutoClaimJustId(keyStreamGroup, group, consumer, 0, "0-0")
	optionsAutoClaim = options.NewXAutoClaimOptions().SetCount(1)
	tx.XAutoClaimJustIdWithOptions(keyStreamGroup, group, consumer, 0, "0-1", *optionsAutoClaim)

	//XGroupCreate
	keyStreamGroup = "KeyStreamG-" + uuid.New().String()
	counterG++ // Increment counter by 1
	counter1G++
	streamIdG = fmt.Sprintf("%d-%d", counterG, counter1G)
	group = "G" + uuid.New().String()
	tx.XAddWithOptions(
		keyStreamGroup,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamIdG),
	) // This will create the stream if it does not exist
	tx.XGroupCreate(keyStreamGroup, group, "0")
	tx.XInfoGroups(keyStreamGroup)
	tx.XPending(keyStreamGroup, group)
	tx.XPendingWithOptions(
		keyStreamGroup,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer),
	)
	tx.XGroupDestroy(keyStreamGroup, group)
	tx.XAck(keyStreamGroup, group, []string{streamIdG})

	//Hyperlog
	sourceKey1 := uuid.New().String() + "{group}"
	sourceKey2 := uuid.New().String() + "{group}"
	destKey = uuid.New().String() + "{group}"
	tx.PfAdd(sourceKey1, []string{"value1", "value2", "value3"})
	tx.PfAdd(sourceKey2, []string{"value1", "value2", "value3"})
	tx.PfCount([]string{uuid.New().String()})
	tx.PfMerge(destKey, []string{sourceKey1, sourceKey2})

	//Server Management commands
	tx.ConfigSet(map[string]string{"timeout": "1000", "maxmemory": "1GB"})
	tx.ConfigGet([]string{"timeout", "maxmemory"})
	tx.Time()
	tx.Info()
	optsInfo := options.InfoOptions{Sections: []options.Section{options.Server}}
	tx.InfoWithOptions(optsInfo)
	tx.Select(1)
	tx.Time()
	tx.Lolwut()
	optsLolwut := options.NewLolwutOptions(6)
	tx.LolwutWithOptions(*optsLolwut)
	tx.LastSave()
	tx.ConfigResetStat()
	//tx.ConfigRewrite() //
	//tx.FlushAll()
	//tx.FlushAllWithOptions(options.ASYNC)
	//tx.FlushDB()
	//tx.FlushDBWithOptions(options.SYNC)

	//Connection Management
	tx.Ping()
	optsPing := options.PingOptions{Message: "hello"}
	tx.PingWithOptions(optsPing)
	//tx.Echo("Hello World")
	tx.ClientId()
	tx.ClientGetName()
	tx.ClientSetName("ConnectionName")

	//String Command
	// Testing INCR/INCRBY/INCRBYFLOAT in transaction mode
	fmt.Println("\n--- Testing INCR/INCRBY/INCRBYFLOAT in transaction mode ---")
	tx.Set("counter_tx", "10")
	tx.Incr("counter_tx")
	tx.Get("counter_tx")

	tx.Set("incrby_counter", "50")
	tx.IncrBy("incrby_counter", 25)
	tx.Get("incrby_counter")

	tx.Set("incrby_negative", "100")
	tx.IncrBy("incrby_negative", -30)
	tx.Get("incrby_negative")

	tx.IncrBy("incrby_new", 15)
	tx.Get("incrby_new")

	tx.Set("float_counter", "10.5")
	tx.IncrByFloat("float_counter", 2.5)
	tx.Get("float_counter")

	tx.Set("float_negative", "20.75")
	tx.IncrByFloat("float_negative", -5.25)
	tx.Get("float_negative")

	tx.IncrByFloat("float_new", 3.14)
	tx.Get("float_new")

	tx.Set("mixed_counter", "5")
	tx.Incr("mixed_counter")
	tx.IncrBy("mixed_counter", 10)
	tx.IncrByFloat("mixed_counter", 1.5)
	tx.Get("mixed_counter")

	// Testing DECR and DECRBY commands
	fmt.Println("\n--- Testing DECR/DECRBY in transaction mode ---")
	tx.Set("decr_counter", "20")
	tx.Decr("decr_counter")
	tx.Get("decr_counter")

	tx.Set("decrby_counter", "100")
	tx.DecrBy("decrby_counter", 35)
	tx.Get("decrby_counter")

	tx.Decr("decr_new")
	tx.Get("decr_new")

	tx.DecrBy("decrby_new", 10)
	tx.Get("decrby_new")

	// Test: SET with options
	fmt.Println("\n--- Testing SET command with options ---")
	tx.Set("set_basic", "hello world")
	tx.Get("set_basic")

	// Test: SET with options
	fmt.Println("\n--- Testing SET command with options ---")
	tx.Set("set_basic", "hello world")
	tx.Get("set_basic")

	// SET with EX option (expire in seconds)
	expiryOpts := options.NewSetOptions()
	expiry := &options.Expiry{
		Type:  options.Seconds,
		Count: 60,
	}
	expiryOpts.SetExpiry(expiry)
	tx.SetWithOptions("set_ex", "expire in 60 seconds", *expiryOpts)
	tx.TTL("set_ex")

	// SET with NX option (only set if key doesn't exist)
	nxOpts := options.NewSetOptions()
	nxOpts.SetOnlyIfDoesNotExist()
	tx.SetWithOptions("set_nx_1", "new key created", *nxOpts)
	tx.Get("set_nx_1")

	// Set key first, then try SET NX (should not set)
	tx.Set("set_nx_2", "original value")
	tx.SetWithOptions("set_nx_2", "should not change", *nxOpts)
	tx.Get("set_nx_2")

	// Test SET with KEEPTTL option
	keepTTLOpts := options.NewSetOptions()
	keepTTLExpiry := &options.Expiry{
		Type: options.KeepExisting,
	}
	keepTTLOpts.SetExpiry(keepTTLExpiry)
	tx.Expire("set_basic", 100)
	tx.SetWithOptions("set_basic", "updated but keep TTL", *keepTTLOpts)
	tx.Get("set_basic")
	tx.TTL("set_basic")

	// Test SET with XX option (only set if key exists)
	xxOpts := options.NewSetOptions()
	xxOpts.SetOnlyIfExists()
	tx.SetWithOptions("set_xx_1", "will not be set", *xxOpts)
	tx.Get("set_xx_1")
	tx.SetWithOptions("set_basic", "updated with XX", *xxOpts)
	tx.Get("set_basic")

	// Test SET with GET option (return old value)
	getOpts := options.NewSetOptions()
	getOpts.SetReturnOldValue(true)
	tx.SetWithOptions("set_basic", "new value with GET", *getOpts)

	// Test: MSET (set multiple keys in a single operation)
	fmt.Println("\n--- Testing MSET command ---")
	keyValues := map[string]string{
		"mset_key1": "value1",
		"mset_key2": "value2",
		"mset_key3": "value3",
	}
	tx.MSet(keyValues)
	tx.Get("mset_key1")
	tx.Get("mset_key2")
	tx.Get("mset_key3")

	// Test: MSETNX (set multiple keys only if none exist)
	fmt.Println("\n--- Testing MSETNX command ---")
	// All new keys, should succeed
	newKeyValues := map[string]string{
		"msetnx_key1": "new value1",
		"msetnx_key2": "new value2",
	}
	tx.MSetNX(newKeyValues)
	tx.Get("msetnx_key1")
	tx.Get("msetnx_key2")

	// Try with a mix of existing and new keys (should fail for all)
	mixedKeyValues := map[string]string{
		"msetnx_key1": "changed value1",
		"msetnx_key3": "new value3",
	}
	tx.MSetNX(mixedKeyValues)
	tx.Get("msetnx_key1")
	tx.Get("msetnx_key3")

	// After testing MSET command in your transaction
	fmt.Println("\n--- Testing MGET command ---")

	// First set some keys to retrieve later
	tx.Set("mget_key1", "value1")
	tx.Set("mget_key2", "value2")
	tx.Set("mget_key3", "value3")

	// Test MGet with existing keys
	tx.MGet([]string{"mget_key1", "mget_key2", "mget_key3"})

	// Test MGet with a mix of existing and non-existing keys
	tx.MGet([]string{"mget_key1", "nonexistent_key", "mget_key3"})

	// Test MGet with all non-existing keys
	tx.MGet([]string{"nonexistent_key1", "nonexistent_key2"})

	// Test MGet using the keys set with MSET earlier
	tx.MGet([]string{"mset_key1", "mset_key2", "mset_key3"})

	// Test: Additional APPEND command testing
	fmt.Println("\n--- Testing APPEND command ---")
	tx.Set("append_key", "Hello")
	tx.Append("append_key", " World")
	tx.Get("append_key")

	tx.Append("append_new", "Starting fresh")
	tx.Get("append_new")

	// Test: STRLEN command
	fmt.Println("\n--- Testing STRLEN command ---")
	tx.Set("strlen_key", "Hello Redis")
	tx.Strlen("strlen_key")

	tx.Strlen("strlen_nonexistent")

	// Test: SETRANGE command
	fmt.Println("\n--- Testing SETRANGE command ---")
	tx.Set("setrange_key", "Hello World")
	tx.SetRange("setrange_key", 6, "Redis")
	tx.Get("setrange_key")

	tx.SetRange("setrange_new", 5, "value")
	tx.Get("setrange_new")

	// After testing other string commands in your transaction
	fmt.Println("\n--- Testing GETRANGE command ---")

	// First set a string to perform range operations on
	tx.Set("getrange_key", "Hello Redis World")

	// Test GetRange with different ranges
	tx.GetRange("getrange_key", 0, 4)
	tx.GetRange("getrange_key", 6, 10)
	tx.GetRange("getrange_key", -5, -1)
	tx.GetRange("getrange_key", 0, -1)
	tx.GetRange("getrange_key", 5, 5)
	tx.GetRange("getrange_key", 10, 5)
	tx.GetRange("getrange_key", 20, 25)

	// Test GetRange on a non-existing key
	tx.GetRange("nonexistent_key", 0, 5)

	// After testing other string commands in your transaction
	fmt.Println("\n--- Testing GETDEL command ---")

	// First set some keys to get and delete
	tx.Set("getdel_key1", "value to get and delete")
	tx.Set("getdel_key2", "another value to get and delete")

	// Test GetDel on existing keys
	tx.GetDel("getdel_key1")
	tx.Exists([]string{"getdel_key1"})

	// Check that GetDel returned the value but removed the key
	tx.Get("getdel_key1")

	// Test GetDel on a non-existing key
	tx.GetDel("nonexistent_getdel_key")

	// Test GetDel followed by another operation on the same key
	tx.GetDel("getdel_key2")
	tx.Set("getdel_key2", "new value")
	tx.Get("getdel_key2")

	// After testing other string commands in your transaction
	fmt.Println("\n--- Testing GETEX command ---")

	tx.Set("getex_key", "value to get with expiration")

	tx.GetEx("getex_key")

	tx.Get("getex_key")

	// Test GetEx on a non-existing key
	tx.GetEx("nonexistent_getex_key")

	// After testing other string commands in your transaction
	fmt.Println("\n--- Testing GETEX with options command ---")

	// First set a key to get with expiration
	tx.Set("getex_opt_key1", "value with expiration")
	tx.Set("getex_opt_key2", "value to persist")
	tx.Set("getex_opt_key3", "value with unix time expiry")

	// Test GetEx with EX option (expire in seconds)
	exExpiry := options.NewExpiry()
	exExpiry.SetType(options.Seconds)
	exExpiry.SetCount(60)

	exOpts := options.NewGetExOptions()
	exOpts.SetExpiry(exExpiry)

	tx.GetExWithOptions("getex_opt_key1", *exOpts)
	tx.TTL("getex_opt_key1")

	// Test GetEx with PERSIST option (remove expiration)
	persistExpiry := options.NewExpiry()
	persistExpiry.SetType(options.Persist)

	persistOpts := options.NewGetExOptions()
	persistOpts.SetExpiry(persistExpiry)

	// First set expiry on the key
	tx.Expire("getex_opt_key2", 100)
	// Then persist it while getting value
	tx.GetExWithOptions("getex_opt_key2", *persistOpts)
	tx.TTL("getex_opt_key2")

	// Test GetEx with EXAT option (expire at Unix timestamp)
	currentTime := uint64(time.Now().Unix()) + 120
	exatExpiry := options.NewExpiry()
	exatExpiry.SetType(options.UnixSeconds)
	exatExpiry.SetCount(currentTime)

	exatOpts := options.NewGetExOptions()
	exatOpts.SetExpiry(exatExpiry)

	tx.GetExWithOptions("getex_opt_key3", *exatOpts)
	tx.TTL("getex_opt_key3")

	// Test GetEx with PX option (expire in milliseconds)
	pxExpiry := options.NewExpiry()
	pxExpiry.SetType(options.Milliseconds)
	pxExpiry.SetCount(30000)

	pxOpts := options.NewGetExOptions()
	pxOpts.SetExpiry(pxExpiry)

	tx.GetExWithOptions("getex_opt_key1", *pxOpts)
	tx.TTL("getex_opt_key1")

	// Test GetEx on a non-existing key
	tx.GetExWithOptions("nonexistent_getex_key", *exOpts)

	// Test: LCS (Longest Common Substring)
	fmt.Println("\n--- Testing LCS command ---")
	tx.Set("lcs_key1", "hello world")
	tx.Set("lcs_key2", "hello redis world")
	tx.LCS("lcs_key1", "lcs_key2")
	tx.Set("lcs_key3", "ABCDE")
	tx.Set("lcs_key4", "ACDF")
	tx.LCS("lcs_key3", "lcs_key4")
	// In the transaction testing section, after your LCS commands:
	fmt.Println("\n--- Testing LCSLen command ---")
	tx.LCSLen("lcs_key1", "lcs_key2")
	tx.LCSLen("lcs_key3", "lcs_key4")

	// In the transaction testing section, after your LCS and LCSLen commands:
	fmt.Println("\n--- Testing LCSWithOptions command ---")

	// First test: Get indices of matches with LCSWithOptions
	tx.Set("lcs_opt_key1", "hello world")
	tx.Set("lcs_opt_key2", "hello redis world")

	lcsIdxOptions := options.NewLCSIdxOptions()
	lcsIdxOptions.SetMinMatchLen(3)
	lcsIdxOptions.SetWithMatchLen(true)

	tx.LCSWithOptions("lcs_opt_key1", "lcs_opt_key2", *lcsIdxOptions)

	// Second test: With different data and options
	tx.Set("lcs_opt_key3", "ABCDEFGH")
	tx.Set("lcs_opt_key4", "XBCDYZFGH")

	lcsIdxOptions2 := options.NewLCSIdxOptions()
	lcsIdxOptions2.SetMinMatchLen(2)
	lcsIdxOptions2.SetWithMatchLen(true)

	tx.LCSWithOptions("lcs_opt_key3", "lcs_opt_key4", *lcsIdxOptions2)

	//Generic Commands
	tx.CustomCommand([]string{"ping"})
	key = uuid.New().String()
	tx.Set(key, "Hello World")
	tx.Move(key, 2)
	tx.Scan(1)
	optsScan := options.NewScanOptions().SetCount(10)
	tx.ScanWithOptions(0, *optsScan)

	tx.RandomKey()

	result, err := tx.Exec()
	if err != nil {
		log.Fatalf("Transaction failed: %v", err)
	}
	fmt.Println(result)
	client.Close()
}
