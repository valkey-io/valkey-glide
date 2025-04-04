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

	//Non transaction
	resultSet, _ := client.Set("key123", "Hello")
	fmt.Println(resultSet)

	resultSet, _ = client.Set("key345", "world")
	fmt.Println(resultSet)

	//Create Transcation
	tx := api.NewTransaction(client)
	tx.Get("key123")
	tx.Get("key345")
	//tx.Set("key678", "Glide")
	result, err := tx.Exec()

	if err != nil {
		log.Fatalf("Transaction failed: %v", err)
	}
	fmt.Println(result)

	// err = tx.Discard()
	// if err != nil {
	// 	log.Fatalf("Transaction failed: %v", err)
	// } else {
	// 	fmt.Println("Transaction successfully discarded!")
	// }
	client.Close()
}
