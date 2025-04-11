package main

import (
	"fmt"
	"log"

	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/integTest"
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

	// Create a new GlideClient
	clientNormal, err := api.NewGlideClient(config)
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}

	//Non transaction
	resultSet, err := clientNormal.Set("key123", "Hello")
	if err != nil {
		log.Fatal("Set error: ", err)
	}
	fmt.Println(resultSet)
	//Non transaction
	resultSet, err = clientNormal.Set("key345", "Hello")
	if err != nil {
		log.Fatal("Set error: ", err)
	}
	fmt.Println(resultSet)
	resultWatch, err := clientNormal.Watch([]string{"key123", "key345"})
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println(resultWatch)

	resultUnWatch, err := clientNormal.Unwatch()
	if err != nil {
		log.Fatal("error connecting to database: ", err)
	}
	fmt.Println(resultUnWatch)

	tx := api.NewTransaction(client)
	//Scripting and Function
	var (
		libraryCode         = integTest.GenerateLuaLibCode("mylib", map[string]string{"myfunc_1": "return 42"}, true)
		libraryCodeWithArgs = integTest.GenerateLuaLibCode("mylib", map[string]string{"myfunc_2": "return args[1]"}, true)
	)
	tx.FunctionLoad(libraryCode, true)
	// tx.FCall("myfunc")
	// tx.FCallReadOnly("myfunc")

	tx.FunctionLoad(libraryCodeWithArgs, true)
	// key1 := "{testKey}-" + uuid.New().String()
	// key2 := "{testKey}-" + uuid.New().String()
	// tx.FCallWithKeysAndArgs("myfunc", []string{key1, key2}, []string{"3", "4"})
	// tx.FCallReadOnly("myfunc")
	// tx.FCallReadOnlyWithKeysAndArgs("myfunc", []string{key1, key2}, []string{"3", "4"})
	tx.FunctionFlush()

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
