package main

import (
	"fmt"
	"log"

	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func main() {
	host := "localhost"
	port := 7005

	config := api.NewGlideClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.NewGlideClusterClient(config)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}

	// res, err := client.Ping()
	// if err != nil {
	// 	fmt.Println("There was an error: ", err)
	// 	return
	// }
	// fmt.Println(res) // PONG

	tx := api.NewClusterTransaction(client)
	tx.Info()
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Cluster}},
	}

	tx.InfoWithOptions(opts)
	//tx.Ping()
	result, err := tx.Exec()
	if err != nil {
		log.Fatalf("Transaction failed: %v", err)
	}
	fmt.Println(result)
	client.Close()
}
