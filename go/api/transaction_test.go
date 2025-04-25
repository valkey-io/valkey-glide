// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
)

func ExampleTransaction_Exec() {
	var cmd *Transaction = getExampleTransactionGlideClient() // example helper function
	cmd.Set("key123", "Glide")
	result, _ := cmd.Exec()
	fmt.Println(result)

	// Output:
	// [OK]
}

func ExampleTransaction_Watch() {
	var clientTx *Transaction = getExampleTransactionGlideClient() // example helper function
	clientTx.Set("key123", "Glide")
	clientTx.Watch([]string{"key123", "key345"})
	clientTx.Get("key123")
	clientTx.Del([]string{"key123"})

	result, _ := clientTx.Exec()
	fmt.Println(result)

	// Output:
	// [OK Glide 1]
}

func ExampleTransaction_Unwatch() {
	var clientTx *Transaction = getExampleTransactionGlideClient() // example helper function
	clientTx.Set("key123", "Glide")
	clientTx.Watch([]string{"key123", "key345"})
	clientTx.Get("key123")
	clientTx.Unwatch()
	clientTx.Del([]string{"key123"})

	result, _ := clientTx.Exec()
	fmt.Println(result)

	// Output:
	// [OK Glide 1]
}
