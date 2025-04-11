package main

import (
	"fmt"
	"log"

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

	resultDump, err := clientNormal.Dump("key123")
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println("resultdump", resultDump)

	// resultDump, err = clientNormal.Dump("ssdsffigjdgf")
	// if err != nil {
	// 	log.Fatal("error connecting to database: ", err)
	// }
	// fmt.Println("resultdump", resultDump)

	resultObjEnc, err := clientNormal.ObjectEncoding("key123")
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println("resultObjEnc", resultObjEnc)

	clientNormal.LPush("keylist", []string{"1", "3", "2", "4", "5"})
	resultLpop, err := clientNormal.LPop("keylist")
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println("resultLpop", resultLpop)

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

	// // err = tx.Discard()
	// // if err != nil {
	// // 	log.Fatalf("Transaction Discard failed: %v", err)
	// // } else {
	// // 	fmt.Println("Transaction successfully discarded!")
	// // }
	result, err := tx.Exec()
	if err != nil {
		log.Fatalf("Transaction failed: %v", err)
	}
	fmt.Println(result)
	client.Close()

}
