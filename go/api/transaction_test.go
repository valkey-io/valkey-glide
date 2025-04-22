// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	//"github.com/valkey-io/valkey-glide/go/api"
)

func ExampleTransaction_Exec() {
	var client *Transaction = getExampleTransactionGlideClient() // example helper function

	// tx := api.NewTransaction(client)
	// cmd := tx.GlideClient
	client.Set("key123", "Glide")
	client.Get("key123")
	client.Del([]string{"key123"})

	result, _ := client.Exec()
	fmt.Println(result)

	// Output:
	// [OK Glide 1]
}

func ExampleTransaction_Watch() {
	var client *Transaction = getExampleTransactionGlideClient() // example helper function

	// tx := api.NewTransaction(client)
	// cmd := tx.GlideClient
	client.Set("key123", "Glide")
	client.Get("key123")
	client.Del([]string{"key123"})

	result, _ := client.Exec()
	fmt.Println(result)

	// Output:
	// [OK Glide 1]
}
