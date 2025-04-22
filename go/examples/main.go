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
	cmd := tx.GlideClient
	cmd.Set("key123", "Glide")
	cmd.Watch([]string{"key123"})
	cmd.Set("key1", "Glide")
	cmd.Set("key2", "Hello")
	cmd.Set("key3", "KeyToDelete")
	cmd.Get("key1")
	cmd.Get("key2")
	cmd.Get("key3")
	cmd.Del([]string{"key3"})
	cmd.Append("key2", "_World")
	cmd.Get("key2")
	cmd.Set("key123", "Valkey")
	cmd.Get("key123")
	cmd.Type("key123")
	cmd.Exists([]string{"key1", "key2"})
	cmd.Touch([]string{"key1", "key2"})
	cmd.PTTL("key1")
	cmd.TTL("key1")
	cmd.Set("UnlinkKey", "Hello")
	cmd.Unlink([]string{"UnlinkKey"})
	cmd.Persist("key1")
	cmd.ObjectEncoding("key123")
	cmd.ObjectIdleTime("key1")
	//cmd.ObjectFreq("key1") //config
	cmd.ObjectRefCount("key1")
	cmd.Set("key4", "newkey3")
	cmd.Rename("key2", "newkey2")
	cmd.RenameNX("key4", "newkey4") //newKey must be not existing
	cmd.Copy("key2", "keyCopy")
	cmd.Dump("key123")
	cmd.Del([]string{"key2"})
	//cmd.Restore("key2", 0, "\x00\x0bHello World\x0b\x00\xad\xb7\xa9\x8fcM3Y")
	cmd.PExpire("key4", 100)
	cmd.Expire("key4", 100)
	cmd.ExpireTime("key4")
	cmd.PExpireTime("key4")
	cmd.PExpireAt("key4", 100)
	cmd.PExpire("key4", 100)
	cmd.LPush("keySort", []string{"1", "3", "2", "4"})
	cmd.Sort("keySort")
	//cmd.sort_ro
	cmd.SortStore("keySort", "key1_store")
	cmd.Wait(10, 10)

	cmd.SAdd("someKey", []string{"value", "value1", "value2"})
	cmd.SRem("someKey", []string{"value1"})
	cmd.SMembers("someKey")
	cmd.SCard("someKey")
	cmd.SIsMember("someKey", "value2")
	cmd.SPop("somekey")
	cmd.SRandMember("somekey")
	cmd.SAdd("someKey1", []string{"value", "value1", "value2"})
	cmd.SDiff([]string{"someKey", "someKey1"})
	cmd.SDiffStore("someKey3", []string{"someKey"})
	cmd.SInterCard([]string{"someKey", "someKey1"})
	cmd.SUnionStore("destinationKey", []string{"someKey3"})
	cmd.SUnion([]string{"someKey3", "someKey1"})
	cmd.SMove("destinationKey1", "someKey3", "value1")
	cmd.SInterStore("destinationKey2", []string{"destinationKey1"})
	cmd.SInter([]string{"destinationKey1", "someKey3"})
	cmd.SMIsMember("someKey", []string{"value", "value1", "value2"})
	cmd.SScan("someKey", "0")

	cmd.LPush("keylist", []string{"1", "3", "2", "4"})
	cmd.LPop("keylist")
	cmd.LPos("keylist", "1")
	cmd.LRange("keylist", 1, 4)
	cmd.RPush("keylist", []string{"a", "b"})
	cmd.RPop("keylist")
	cmd.LLen("keylist")
	cmd.LRem("keylist", 0, "b")
	cmd.LTrim("keylist", 3, 4)
	cmd.LIndex("keylist", 1)

	cmd.RPush("my_list", []string{"hello", "world"})
	cmd.LInsert("my_list", options.Before, "world", "there")
	cmd.LRange("my_list", 0, -1)

	cmd.RPush("list_a", []string{"a", "b", "c", "d", "e"})
	cmd.RPush("list_b", []string{"f", "g", "h", "i", "j"})
	cmd.BLPop([]string{"list_a", "list_b"}, 0.5)
	cmd.BRPop([]string{"list_a", "list_b"}, 0.5)
	cmd.LPushX("my_list", []string{"value2", "value3"})
	cmd.RPushX("my_list", []string{"value2", "value3"})
	//cmd.LMPop([]string{"my_list"}, options.Left)
	//cmd.BLMPop([]string{"my_listPOP"}, options.Left, 0.1)

	cmd.LPush("my_list1", []string{"two", "one"})
	cmd.LPush("my_list2", []string{"four", "three"})
	cmd.LMove("my_list1", "my_list2", options.Left, options.Left)
	cmd.BLMove("my_list1", "my_list2", options.Left, options.Left, 0.1)

	//hash
	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	cmd.HSet("my_hash", fields)
	cmd.HGet("my_hash", "field1")
	cmd.HMGet("my_hash", []string{"field1", "field2"})
	cmd.HGetAll("my_hash")
	cmd.HLen("my_hash")
	cmd.HSetNX("my_hash", "field3", "value")
	cmd.HVals("my_hash")
	cmd.HKeys("my_hash")
	cmd.HStrLen("my_hash", "field1")

	fields = map[string]string{
		"field1": "10",
		"field2": "14",
	}
	cmd.HSet("my_hash_int", fields)
	cmd.HIncrBy("my_hash_int", "field1", 1)
	fields = map[string]string{
		"field1": "10",
		"field2": "14",
	}
	cmd.HSet("my_hash_loat", fields)
	cmd.HIncrByFloat("my_hash_loat", "field1", 1.5)
	cmd.HRandField("my_hash")
	cmd.HRandFieldWithCount("my_hash", 2)
	cmd.HRandFieldWithCountWithValues("my_hash", 2)
	cmd.HScan("my_hash", "0")

	//Geospatial indices commands
	key := uuid.New().String()
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}
	cmd.GeoAdd(key, membersToCoordinates)
	cmd.GeoPos(key, []string{"Palermo", "Catania"})
	cmd.GeoHash(key, []string{"Palermo", "Catania"})
	cmd.GeoDist(key, "Palermo", "Catania")

	cmd.GeoSearch(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)
	cmd.GeoSearch(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)
	source := uuid.New().String()
	destination := uuid.New().String()
	cmd.GeoSearchStore(
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
	cmd.BitField("mykey", commands)
	cmd.SetBit("my_key", 1, 1)
	cmd.GetBit("my_key", 1)
	cmd.BitCount("my_key")

	bitopkey1 := "{bitop_test}key1"
	bitopkey2 := "{bitop_test}key2"
	destKey := "{bitop_test}dest"

	// Set initial values
	client.Set(bitopkey1, "foobar")
	client.Set(bitopkey2, "abcdef")
	commandsRO := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	cmd.BitFieldRO("mykey", commandsRO)

	// Perform BITOP AND
	cmd.BitOp(options.AND, destKey, []string{bitopkey1, bitopkey2})
	cmd.BitPos("mykey", 1)

	//Sorted Set Commands
	cmd.ZAdd("keySet", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	cmd.ZRem("keySet", []string{"one", "two", "nonMember"})
	cmd.ZRange("keySet", options.NewRangeByIndexQuery(0, -1))
	cmd.ZAdd("keyZRangeStore", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
	cmd.ZRangeStore("dest", "keyZRangeStore", query)
	cmd.ZRemRangeByScore("keySet", *options.NewRangeByScoreQuery(
		options.NewInfiniteScoreBoundary(options.NegativeInfinity),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
	))
	cmd.ZRemRangeByRank("keySet", 1, 3)
	cmd.ZAdd("keyScore", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	cmd.ZScore("keyScore", "three")
	cmd.ZCard("keyScore")
	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
	)
	cmd.ZCount("keySet", *zCountRange)
	cmd.ZIncrBy("keySet", 3.0, "two")

	cmd.ZAdd("keySetPop", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	cmd.ZPopMin("keySetPop")
	// cmd.ZRank("keySetPop", "two")
	// cmd.ZRankWithScore("keySetPop", "two")

	client.ZAdd("key1ZInterStore", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2ZInterStore", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	cmd.ZInterStoreWithOptions(
		"dest",
		options.KeyArray{
			Keys: []string{"key1ZInterStore", "key2ZInterStore"},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)

	// cmd.ZRevRank("keySet", "two")
	cmd.ZAdd("keyRankWithScore", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	// cmd.ZRevRankWithScore("keyRankWithScore", "two")

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

	cmd.ZUnionStore(
		"dest",
		options.KeyArray{Keys: []string{"ZUnionStore1", "ZUnionStore2"}},
	)
	cmd.ZPopMax("keyRankWithScore")
	opts := options.NewZPopOptions().SetCount(2)
	cmd.ZPopMaxWithOptions("keyRankWithScore", *opts)
	cmd.ZRemRangeByLex(
		"keyRankWithScore",
		*options.NewRangeByLexQuery(options.NewLexBoundary("a", false), options.NewLexBoundary("c", true)),
	)

	cmd.ZAdd("ZLexCount", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	cmd.ZLexCount("ZLexCount",
		options.NewRangeByLexQuery(
			options.NewLexBoundary("a", false),
			options.NewLexBoundary("c", true),
		),
	)
	cmd.ZRandMember("ZLexCount")
	cmd.ZRandMemberWithCount("ZLexCount", 4)
	cmd.ZRandMemberWithCountWithScores("ZLexCount", 4)

	cmd.ZAdd("keyZAdd1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	cmd.ZAdd("keyZAdd2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	cmd.ZDiffStore("dest", []string{"keyZAdd1", "keyZAdd2"})

	cmd.ZDiff([]string{"keyZAdd1", "keyZAdd2"})
	cmd.ZDiffWithScores([]string{"keyZAdd1", "keyZAdd2"})

	cmd.ZInter(options.KeyArray{
		Keys: []string{"keyZAdd1", "keyZAdd2"},
	})

	cmd.ZInterWithScores(
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
	cmd.ZAdd("keyZUnion1", memberScoreMap1)
	cmd.ZAdd("keyZUnion2", memberScoreMap2)
	cmd.ZUnion(options.KeyArray{Keys: []string{"keyZUnion1", "keyZUnion2"}})
	cmd.ZUnionWithScores(
		options.KeyArray{Keys: []string{"keyZUnion1", "keyZUnion2"}},
		options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	cmd.BZPopMin([]string{"keyZUnion1", "keyZUnion2"}, 0.5)
	cmd.ZAdd("key1ZScan", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	cmd.ZScan("key1ZScan", "0")

	cmd.ZAdd("keyBZMPop", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	cmd.BZMPop([]string{"keyBZMPop"}, options.MAX, float64(0.5))

	cmd.BZMPopWithOptions([]string{"keyBZMPop"}, options.MAX, 0.1, *options.NewZMPopOptions().SetCount(2))

	//Scripting and Function
	funcName := "testKey7"
	funcName1 := "testKey8"

	var (
		libraryCode         = integTest.GenerateLuaLibCode("mylib", map[string]string{funcName: "return 42"}, true)
		libraryCodeWithArgs = integTest.GenerateLuaLibCode("mylib", map[string]string{funcName1: "return args[1]"}, true)
	)
	cmd.FunctionLoad(libraryCode, true)
	cmd.FCall(funcName)
	cmd.FCallReadOnly(funcName)

	cmd.FunctionLoad(libraryCodeWithArgs, true)
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	cmd.FCallWithKeysAndArgs(funcName1, []string{key1, key2}, []string{"3", "4"})
	cmd.FCallReadOnly(funcName1)
	cmd.FCallReadOnlyWithKeysAndArgs(funcName1, []string{key1, key2}, []string{"3", "4"})
	cmd.FunctionFlush()
	cmd.FunctionFlushSync()
	cmd.FunctionFlushAsync()

	//Stream Commands
	keyStream := "key" + uuid.New().String()
	cmd.XAdd(
		keyStream,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
	)
	cmd.XTrim(keyStream, *options.NewXTrimOptionsWithMaxLen(0).SetExactTrimming())
	cmd.XLen(keyStream)

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
	cmd.XAddWithOptions(keyStream, values, *optionsXAdd)
	cmd.XRead(map[string]string{keyStream: "0-0"})
	cmd.XTrim(keyStream, *options.NewXTrimOptionsWithMaxLen(0).SetExactTrimming())
	cmd.XInfoStream(keyStream)
	optionsInforStream := options.NewXInfoStreamOptionsOptions().SetCount(2)
	cmd.XInfoStreamFullWithOptions(keyStream, optionsInforStream)
	cmd.XRange(keyStream,
		options.NewInfiniteStreamBoundary(options.NegativeInfinity),
		options.NewInfiniteStreamBoundary(options.PositiveInfinity))
	cmd.XRangeWithOptions(keyStream,
		options.NewInfiniteStreamBoundary(options.NegativeInfinity),
		options.NewInfiniteStreamBoundary(options.PositiveInfinity),
		*options.NewXRangeOptions().SetCount(1))

	counter++ // Increment counter by 1
	counter1++
	streamId1RevRange := fmt.Sprintf("%d-%d", counter, counter1)
	counter++ // Increment counter by 1
	counter1++
	streamId2RevRange := fmt.Sprintf("%d-%d", counter, counter1)
	cmd.XAddWithOptions(keyStream, [][]string{{"field1", "value1"}},
		*options.NewXAddOptions().SetId(streamId1RevRange))
	cmd.XAddWithOptions(keyStream, [][]string{{"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId2RevRange))
	cmd.XRevRange(keyStream,
		options.NewStreamBoundary(streamId1RevRange, true),
		options.NewStreamBoundary(streamId2RevRange, true))

	cmd.XDel(keyStream, []string{"0-1", "0-2", "0-3"})

	keyStreamGroup := "KeyStreamG-" + uuid.New().String()
	group := "G" + uuid.New().String()
	consumer := "c" + uuid.New().String()
	counterG := 1234572
	counter1G := 12347
	counterG++ // Increment counter by 1
	counter1G++
	streamIdG := fmt.Sprintf("%d-%d", counterG, counter1G)

	cmd.XGroupCreateWithOptions(keyStreamGroup, group, "0",
		*options.NewXGroupCreateOptions().SetMakeStream())
	cmd.XGroupCreateConsumer(keyStreamGroup, group, consumer)
	cmd.XInfoConsumers(keyStreamGroup, group)
	cmd.XGroupDelConsumer(keyStreamGroup, group, consumer)
	cmd.XAddWithOptions(
		keyStreamGroup,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamIdG),
	)
	cmd.XGroupSetId(keyStreamGroup, group, "0-0")
	optsSetID := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
	cmd.XGroupSetIdWithOptions(keyStreamGroup, group, "0-0", *optsSetID)
	cmd.XReadGroup(group, consumer, map[string]string{keyStreamGroup: ">"})
	cmd.XReadGroupWithOptions(group, consumer, map[string]string{keyStreamGroup: ">"},
		*options.NewXReadGroupOptions().SetCount(1))
	cmd.XAutoClaim(keyStreamGroup, group, consumer, 0, "0-0")
	optionsAutoClaim := options.NewXAutoClaimOptions().SetCount(1)
	cmd.XAutoClaimWithOptions(keyStreamGroup, group, consumer, 0, "0-0", *optionsAutoClaim)
	cmd.XAutoClaimJustId(keyStreamGroup, group, consumer, 0, "0-0")
	optionsAutoClaim = options.NewXAutoClaimOptions().SetCount(1)
	cmd.XAutoClaimJustIdWithOptions(keyStreamGroup, group, consumer, 0, "0-1", *optionsAutoClaim)

	//XGroupCreate
	keyStreamGroup = "KeyStreamG-" + uuid.New().String()
	counterG++ // Increment counter by 1
	counter1G++
	streamIdG = fmt.Sprintf("%d-%d", counterG, counter1G)
	group = "G" + uuid.New().String()
	cmd.XAddWithOptions(
		keyStreamGroup,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamIdG),
	) // This will create the stream if it does not exist
	cmd.XGroupCreate(keyStreamGroup, group, "0")
	cmd.XInfoGroups(keyStreamGroup)
	cmd.XPending(keyStreamGroup, group)
	cmd.XPendingWithOptions(
		keyStreamGroup,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer),
	)
	cmd.XGroupDestroy(keyStreamGroup, group)
	cmd.XAck(keyStreamGroup, group, []string{streamIdG})

	//Hyperlog
	sourceKey1 := uuid.New().String() + "{group}"
	sourceKey2 := uuid.New().String() + "{group}"
	destKey = uuid.New().String() + "{group}"
	cmd.PfAdd(sourceKey1, []string{"value1", "value2", "value3"})
	cmd.PfAdd(sourceKey2, []string{"value1", "value2", "value3"})
	cmd.PfCount([]string{uuid.New().String()})
	cmd.PfMerge(destKey, []string{sourceKey1, sourceKey2})

	//Server Management commands
	cmd.ConfigSet(map[string]string{"timeout": "1000", "maxmemory": "1GB"})
	cmd.ConfigGet([]string{"timeout", "maxmemory"})
	cmd.Time()
	cmd.Info()
	optsInfo := options.InfoOptions{Sections: []options.Section{options.Server}}
	cmd.InfoWithOptions(optsInfo)
	cmd.Select(1)
	cmd.Time()
	cmd.Lolwut()
	optsLolwut := options.NewLolwutOptions(6)
	cmd.LolwutWithOptions(*optsLolwut)
	cmd.LastSave()
	cmd.ConfigResetStat()
	//cmd.ConfigRewrite() //
	//cmd.FlushAll()
	//cmd.FlushAllWithOptions(options.ASYNC)
	//cmd.FlushDB()
	//cmd.FlushDBWithOptions(options.SYNC)

	//Connection Management
	cmd.Ping()
	optsPing := options.PingOptions{Message: "hello"}
	cmd.PingWithOptions(optsPing)
	//cmd.Echo("Hello World")
	cmd.ClientId()
	cmd.ClientGetName()
	cmd.ClientSetName("ConnectionName")

	//String Command
	// Testing INCR/INCRBY/INCRBYFLOAT in transaction mode
	fmt.Println("\n--- Testing INCR/INCRBY/INCRBYFLOAT in transaction mode ---")
	cmd.Set("counter_tx", "10")
	cmd.Incr("counter_tx")
	cmd.Get("counter_tx")

	cmd.Set("incrby_counter", "50")
	cmd.IncrBy("incrby_counter", 25)
	cmd.Get("incrby_counter")

	cmd.Set("incrby_negative", "100")
	cmd.IncrBy("incrby_negative", -30)
	cmd.Get("incrby_negative")

	cmd.IncrBy("incrby_new", 15)
	cmd.Get("incrby_new")

	cmd.Set("float_counter", "10.5")
	cmd.IncrByFloat("float_counter", 2.5)
	cmd.Get("float_counter")

	cmd.Set("float_negative", "20.75")
	cmd.IncrByFloat("float_negative", -5.25)
	cmd.Get("float_negative")

	cmd.IncrByFloat("float_new", 3.14)
	cmd.Get("float_new")

	cmd.Set("mixed_counter", "5")
	cmd.Incr("mixed_counter")
	cmd.IncrBy("mixed_counter", 10)
	cmd.IncrByFloat("mixed_counter", 1.5)
	cmd.Get("mixed_counter")

	// Testing DECR and DECRBY commands
	fmt.Println("\n--- Testing DECR/DECRBY in transaction mode ---")
	cmd.Set("decr_counter", "20")
	cmd.Decr("decr_counter")
	cmd.Get("decr_counter")

	cmd.Set("decrby_counter", "100")
	cmd.DecrBy("decrby_counter", 35)
	cmd.Get("decrby_counter")

	cmd.Decr("decr_new")
	cmd.Get("decr_new")

	cmd.DecrBy("decrby_new", 10)
	cmd.Get("decrby_new")

	// Test: SET with options
	fmt.Println("\n--- Testing SET command with options ---")
	cmd.Set("set_basic", "hello world")
	cmd.Get("set_basic")

	// Test: SET with options
	fmt.Println("\n--- Testing SET command with options ---")
	cmd.Set("set_basic", "hello world")
	cmd.Get("set_basic")

	// SET with EX option (expire in seconds)
	expiryOpts := options.NewSetOptions()
	expiry := &options.Expiry{
		Type:  options.Seconds,
		Count: 60,
	}
	expiryOpts.SetExpiry(expiry)
	cmd.SetWithOptions("set_ex", "expire in 60 seconds", *expiryOpts)
	cmd.TTL("set_ex")

	// SET with NX option (only set if key doesn't exist)
	nxOpts := options.NewSetOptions()
	nxOpts.SetOnlyIfDoesNotExist()
	cmd.SetWithOptions("set_nx_1", "new key created", *nxOpts)
	cmd.Get("set_nx_1")

	// Set key first, then try SET NX (should not set)
	cmd.Set("set_nx_2", "original value")
	cmd.SetWithOptions("set_nx_2", "should not change", *nxOpts)
	cmd.Get("set_nx_2")

	// Test SET with KEEPTTL option
	keepTTLOpts := options.NewSetOptions()
	keepTTLExpiry := &options.Expiry{
		Type: options.KeepExisting,
	}
	keepTTLOpts.SetExpiry(keepTTLExpiry)
	cmd.Expire("set_basic", 100)
	cmd.SetWithOptions("set_basic", "updated but keep TTL", *keepTTLOpts)
	cmd.Get("set_basic")
	cmd.TTL("set_basic")

	// Test SET with XX option (only set if key exists)
	xxOpts := options.NewSetOptions()
	xxOpts.SetOnlyIfExists()
	cmd.SetWithOptions("set_xx_1", "will not be set", *xxOpts)
	cmd.Get("set_xx_1")
	cmd.SetWithOptions("set_basic", "updated with XX", *xxOpts)
	cmd.Get("set_basic")

	// Test SET with GET option (return old value)
	getOpts := options.NewSetOptions()
	getOpts.SetReturnOldValue(true)
	cmd.SetWithOptions("set_basic", "new value with GET", *getOpts)

	// Test: MSET (set multiple keys in a single operation)
	fmt.Println("\n--- Testing MSET command ---")
	keyValues := map[string]string{
		"mset_key1": "value1",
		"mset_key2": "value2",
		"mset_key3": "value3",
	}
	cmd.MSet(keyValues)
	cmd.Get("mset_key1")
	cmd.Get("mset_key2")
	cmd.Get("mset_key3")

	// Test: MSETNX (set multiple keys only if none exist)
	fmt.Println("\n--- Testing MSETNX command ---")
	// All new keys, should succeed
	newKeyValues := map[string]string{
		"msetnx_key1": "new value1",
		"msetnx_key2": "new value2",
	}
	cmd.MSetNX(newKeyValues)
	cmd.Get("msetnx_key1")
	cmd.Get("msetnx_key2")

	// Try with a mix of existing and new keys (should fail for all)
	mixedKeyValues := map[string]string{
		"msetnx_key1": "changed value1",
		"msetnx_key3": "new value3",
	}
	cmd.MSetNX(mixedKeyValues)
	cmd.Get("msetnx_key1")
	cmd.Get("msetnx_key3")

	// After testing MSET command in your transaction
	fmt.Println("\n--- Testing MGET command ---")

	// First set some keys to retrieve later
	cmd.Set("mget_key1", "value1")
	cmd.Set("mget_key2", "value2")
	cmd.Set("mget_key3", "value3")

	// Test MGet with existing keys
	cmd.MGet([]string{"mget_key1", "mget_key2", "mget_key3"})

	// Test MGet with a mix of existing and non-existing keys
	cmd.MGet([]string{"mget_key1", "nonexistent_key", "mget_key3"})

	// Test MGet with all non-existing keys
	cmd.MGet([]string{"nonexistent_key1", "nonexistent_key2"})

	// Test MGet using the keys set with MSET earlier
	cmd.MGet([]string{"mset_key1", "mset_key2", "mset_key3"})

	// Test: Additional APPEND command testing
	fmt.Println("\n--- Testing APPEND command ---")
	cmd.Set("append_key", "Hello")
	cmd.Append("append_key", " World")
	cmd.Get("append_key")

	cmd.Append("append_new", "Starting fresh")
	cmd.Get("append_new")

	// Test: STRLEN command
	fmt.Println("\n--- Testing STRLEN command ---")
	cmd.Set("strlen_key", "Hello Redis")
	cmd.Strlen("strlen_key")

	cmd.Strlen("strlen_nonexistent")

	// Test: SETRANGE command
	fmt.Println("\n--- Testing SETRANGE command ---")
	cmd.Set("setrange_key", "Hello World")
	cmd.SetRange("setrange_key", 6, "Redis")
	cmd.Get("setrange_key")

	cmd.SetRange("setrange_new", 5, "value")
	cmd.Get("setrange_new")

	// After testing other string commands in your transaction
	fmt.Println("\n--- Testing GETRANGE command ---")

	// First set a string to perform range operations on
	cmd.Set("getrange_key", "Hello Redis World")

	// Test GetRange with different ranges
	cmd.GetRange("getrange_key", 0, 4)
	cmd.GetRange("getrange_key", 6, 10)
	cmd.GetRange("getrange_key", -5, -1)
	cmd.GetRange("getrange_key", 0, -1)
	cmd.GetRange("getrange_key", 5, 5)
	cmd.GetRange("getrange_key", 10, 5)
	cmd.GetRange("getrange_key", 20, 25)

	// Test GetRange on a non-existing key
	cmd.GetRange("nonexistent_key", 0, 5)

	// After testing other string commands in your transaction
	fmt.Println("\n--- Testing GETDEL command ---")

	// First set some keys to get and delete
	cmd.Set("getdel_key1", "value to get and delete")
	cmd.Set("getdel_key2", "another value to get and delete")

	// Test GetDel on existing keys
	// cmd.GetDel("getdel_key1")
	// cmd.Exists([]string{"getdel_key1"})

	// Check that GetDel returned the value but removed the key
	cmd.Get("getdel_key1")

	// Test GetDel on a non-existing key
	// cmd.GetDel("nonexistent_getdel_key")

	// Test GetDel followed by another operation on the same key
	// cmd.GetDel("getdel_key2")
	cmd.Set("getdel_key2", "new value")
	cmd.Get("getdel_key2")

	// After testing other string commands in your transaction
	fmt.Println("\n--- Testing GETEX command ---")

	cmd.Set("getex_key", "value to get with expiration")

	// cmd.GetEx("getex_key")

	cmd.Get("getex_key")

	// Test GetEx on a non-existing key
	cmd.GetEx("nonexistent_getex_key")

	// After testing other string commands in your transaction
	fmt.Println("\n--- Testing GETEX with options command ---")

	// First set a key to get with expiration
	cmd.Set("getex_opt_key1", "value with expiration")
	cmd.Set("getex_opt_key2", "value to persist")
	cmd.Set("getex_opt_key3", "value with unix time expiry")

	// Test GetEx with EX option (expire in seconds)
	exExpiry := options.NewExpiry()
	exExpiry.SetType(options.Seconds)
	exExpiry.SetCount(60)

	exOpts := options.NewGetExOptions()
	exOpts.SetExpiry(exExpiry)

	cmd.GetExWithOptions("getex_opt_key1", *exOpts)
	cmd.TTL("getex_opt_key1")

	// Test GetEx with PERSIST option (remove expiration)
	persistExpiry := options.NewExpiry()
	persistExpiry.SetType(options.Persist)

	persistOpts := options.NewGetExOptions()
	persistOpts.SetExpiry(persistExpiry)

	// First set expiry on the key
	cmd.Expire("getex_opt_key2", 100)
	// Then persist it while getting value
	cmd.GetExWithOptions("getex_opt_key2", *persistOpts)
	cmd.TTL("getex_opt_key2")

	// Test GetEx with EXAT option (expire at Unix timestamp)
	currentTime := uint64(time.Now().Unix()) + 120
	exatExpiry := options.NewExpiry()
	exatExpiry.SetType(options.UnixSeconds)
	exatExpiry.SetCount(currentTime)

	exatOpts := options.NewGetExOptions()
	exatOpts.SetExpiry(exatExpiry)

	cmd.GetExWithOptions("getex_opt_key3", *exatOpts)
	cmd.TTL("getex_opt_key3")

	// Test GetEx with PX option (expire in milliseconds)
	pxExpiry := options.NewExpiry()
	pxExpiry.SetType(options.Milliseconds)
	pxExpiry.SetCount(30000)

	pxOpts := options.NewGetExOptions()
	pxOpts.SetExpiry(pxExpiry)

	cmd.GetExWithOptions("getex_opt_key1", *pxOpts)
	cmd.TTL("getex_opt_key1")

	// Test GetEx on a non-existing key
	cmd.GetExWithOptions("nonexistent_getex_key", *exOpts)

	// Test: LCS (Longest Common Substring)
	fmt.Println("\n--- Testing LCS command ---")
	cmd.Set("lcs_key1", "hello world")
	cmd.Set("lcs_key2", "hello redis world")
	cmd.LCS("lcs_key1", "lcs_key2")
	cmd.Set("lcs_key3", "ABCDE")
	cmd.Set("lcs_key4", "ACDF")
	cmd.LCS("lcs_key3", "lcs_key4")
	// In the transaction testing section, after your LCS commands:
	fmt.Println("\n--- Testing LCSLen command ---")
	cmd.LCSLen("lcs_key1", "lcs_key2")
	cmd.LCSLen("lcs_key3", "lcs_key4")

	// In the transaction testing section, after your LCS and LCSLen commands:
	fmt.Println("\n--- Testing LCSWithOptions command ---")

	// First test: Get indices of matches with LCSWithOptions
	cmd.Set("lcs_opt_key1", "hello world")
	cmd.Set("lcs_opt_key2", "hello redis world")

	lcsIdxOptions := options.NewLCSIdxOptions()
	lcsIdxOptions.SetMinMatchLen(3)
	lcsIdxOptions.SetWithMatchLen(true)

	cmd.LCSWithOptions("lcs_opt_key1", "lcs_opt_key2", *lcsIdxOptions)

	// Second test: With different data and options
	cmd.Set("lcs_opt_key3", "ABCDEFGH")
	cmd.Set("lcs_opt_key4", "XBCDYZFGH")

	lcsIdxOptions2 := options.NewLCSIdxOptions()
	lcsIdxOptions2.SetMinMatchLen(2)
	lcsIdxOptions2.SetWithMatchLen(true)

	cmd.LCSWithOptions("lcs_opt_key3", "lcs_opt_key4", *lcsIdxOptions2)

	//Generic Commands
	cmd.CustomCommand([]string{"ping"})
	key = uuid.New().String()
	cmd.Set(key, "Hello World")
	cmd.Move(key, 2)
	cmd.Scan(1)
	optsScan := options.NewScanOptions().SetCount(10)
	cmd.ScanWithOptions(0, *optsScan)

	cmd.RandomKey()

	result, err := tx.Exec()
	if err != nil {
		log.Fatalf("Transaction failed: %v", err)
	}
	fmt.Println(result)
	client.Close()
}
