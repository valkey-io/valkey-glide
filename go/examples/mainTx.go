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

	tx := api.NewTransaction(client)
	tx.Set("key1", "Glide")
	tx.Set("key2", "Hello")
	tx.Set("key3", "KeyToDelete")
	tx.Get("key1")
	tx.Get("key2")
	tx.Get("key3")
	tx.Del([]string{"key3"})
	tx.Append("key2", "_World")
	tx.Get("key2")

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
