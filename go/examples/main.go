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
	resultSet, _ := client.Set("keyEdric123", "Hello")
	fmt.Println(resultSet)

	// resultSet, _ = client.Set("keyEdric345", "world")
	// fmt.Println(resultSet)

	//Create Transcation
	// tx := api.NewTransaction(client).Set("key1", "value").Set("key2", "value")
	// // This adds the GET command to the transaction queue.
	// // Execute the transaction (MULTI + EXEC)
	// err = tx.Exec()
	// if err != nil {
	// 	log.Fatalf("Transaction failed: %v", err)
	// } else {
	// 	fmt.Println("Transaction executed successfully!")
	// }

	// err = tx.Discard()
	// if err != nil {
	// 	log.Fatalf("Transaction failed: %v", err)
	// } else {
	// 	fmt.Println("Transaction successfully discarded!")
	// }
	client.Close()
}
