package main

import (
	"fmt"
	"log"

	"github.com/valkey-io/valkey-glide/go/api"
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

	// resultUnWatch, err := clientNormal.Unwatch([]string{"key123", "key345"})
	// if err != nil {
	// 	log.Fatal("error connecting to database: ", err)
	// }
	// fmt.Println(resultUnWatch)

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
	//tx.Dump("key2")
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
