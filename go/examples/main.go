package main

import (
	"fmt"
	"log"

	"github.com/valkey-io/valkey-glide/go/api"
)

func main() {
	host := "localhost"
	port := 6379

	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.NewGlideClient(config)
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}

	client.Set("apples", "oran\x00ges")

	// Create Transcation
	tx := api.NewTransaction(client)
	getResult, _ := tx.Get("apples") // This adds the GET command to the transaction queue.
	fmt.Println(getResult)
	//fmt.Println(res) // PONG

	client.Close()
}
