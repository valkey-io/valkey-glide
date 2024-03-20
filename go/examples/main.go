// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package main

import (
	"log"

	"github.com/aws/glide-for-redis/go/glide/api"
)

func main() {
	host := "localhost"
	port := 6379

	config := api.NewRedisClientConfiguration().
		WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.CreateClient(config)
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}

	// TODO: Add example commands as they are implemented

	client.Close()
}
