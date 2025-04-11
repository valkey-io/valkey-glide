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
	resultSet, err := client.Set("key123", "Hello")
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println(resultSet)

	resultSet, err = client.Set("key123", "Hello")
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println(resultSet)

	resultGet, err := client.Get("key123")
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println(resultGet)

	resultFuncFlush, err := client.FunctionFlush()
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println(resultFuncFlush)
	client.Close()

}
