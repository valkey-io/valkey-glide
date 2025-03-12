package main

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api"
)

func main() {
	host := "localhost"
	port := 7001

	config := api.NewGlideClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.NewGlideClusterClient(config)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}

	res, err := client.Ping()
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}
	fmt.Println(res) // PONG

	client.Close()
}
