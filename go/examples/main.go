package main

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api"
)

func main() {
	host := "localhost"
	port := 6379

	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.NewGlideClient(config)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}

	res, err := client.ClientId()
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}
	fmt.Println(res) // PONG

	client.Close()
}
