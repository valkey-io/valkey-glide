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

	resultSet, _ := client.Set("keyEdric123", "Hello")
	fmt.Println(resultSet)

	// resultSet, _ = client.Set("keyEdric345", "world")
	// fmt.Println(resultSet)

	//Create Transcation
	tx := api.NewTransaction(client)
	tx.Get("keyEdric123")
	tx.Get("keyEdric345")
	//tx.Get("key1")
	//tx.Set("keyEd567", "Again")

	// This adds the GET command to the transaction queue.

	//watchresult, _ := tx.Watch([]string{"keyEdric123"})
	//fmt.Println(watchresult)

	// Execute the transaction (MULTI + EXEC)

	err = tx.Exec()
	if err != nil {
		log.Fatalf("Transaction failed: %v", err)
	} else {
		fmt.Println("Transaction executed successfully!")
	}

	// err = tx.Discard()
	// if err != nil {
	// 	log.Fatalf("Transaction failed: %v", err)
	// } else {
	// 	fmt.Println("Transaction successfully discarded!")
	// }
	client.Close()
}
