package main

import (
	"fmt"
	"log"

	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/options"
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
	//tx.ObjectEncoding("key1")
	//tx.ObjectFreq("key1")
	//tx.ObjectIdleTime("key1")
	//tx.ObjectFreq("key1")
	tx.Set("key4", "newkey3")
	tx.Rename("key2", "newkey2")
	tx.RenameNX("key4", "newkey4") //newKey must be not existing
	tx.Copy("key2", "keyCopy")
	tx.Dump("keyToDump")
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
	tx.Wait(10, 10)

	tx.SAdd("someKey", []string{"value", "value1", "value2"})
	tx.SRem("someKey", []string{"value1"})
	tx.SMembers("someKey")
	tx.SCard("someKey")
	tx.SIsMember("someKey", "value2")
	//tx.SPop("somekey")
	//tx.SRandMember("somekey")
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
	//tx.LPop("keylist")
	//tx.LPos("keylist", "1")
	tx.LRange("keylist", 1, 4)
	tx.RPush("keylist", []string{"a", "b"})
	//tx.RPop("keylist")
	tx.LLen("keylist")
	tx.LRem("keylist", 0, "b")
	tx.LTrim("keylist", 3, 4)
	//tx.LIndex("keylist", 1)

	tx.RPush("my_list", []string{"hello", "world"})
	tx.LInsert("my_list", options.Before, "world", "there")
	tx.LRange("my_list", 0, -1)

	tx.RPush("list_a", []string{"a", "b", "c", "d", "e"})
	tx.RPush("list_b", []string{"f", "g", "h", "i", "j"})
	//tx.BLPop([]string{"list_a", "list_b"}, 0.5)
	//tx.BRPop([]string{"list_a", "list_b"}, 0.5)
	tx.LPushX("my_list", []string{"value2", "value3"})
	tx.RPushX("my_list", []string{"value2", "value3"})
	//tx.LMPop([]string{"my_list"}, options.Left)
	//tx.BLMPop([]string{"my_listPOP"}, options.Left, 0.1)

	tx.LPush("my_list1", []string{"two", "one"})
	tx.LPush("my_list2", []string{"four", "three"})
	//tx.LMove("my_list1", "my_list2", options.Left, options.Left)
	//tx.BLMove("my_list1", "my_list2", options.Left, options.Left, 0.1)

	//hash
	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	tx.HSet("my_hash", fields)
	//tx.HGet("my_hash", "field1")
	tx.HMGet("my_hash", []string{"field1", "field2"})
	tx.HGetAll("my_hash")

	// err = tx.Discard()
	// if err != nil {
	// 	log.Fatalf("Transaction Discard failed: %v", err)
	// } else {
	// 	fmt.Println("Transaction successfully discarded!")
	// }

	result, err := tx.Exec()
	if err != nil {
		log.Fatalf("Transaction failed: %v", err)
	}
	fmt.Println(result)
	client.Close()

}
