// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
)

func ExampleTransaction_Exec() {
	var tx *Transaction = getExampleTransactionGlideClient() // example helper function
	cmd := tx.GlideClient
	tx.Discard()
	cmd.Set("key123", "Glide")
	cmd.Set("key1", "Glide")
	cmd.Set("key2", "Hello")
	cmd.Set("key3", "KeyToDelete")
	cmd.Del([]string{"key3"})
	cmd.Append("key2", "_World")
	cmd.Get("key2")
	cmd.Set("key123", "Valkey")
	cmd.Get("key123")
	cmd.Type("key123")
	result, _ := tx.Exec()
	fmt.Println(result)

	// Output:
	// [OK OK OK OK 1 11 Hello_World OK Valkey string]
}

func ExampleTransaction_Watch() {
	var clientTx *Transaction = getExampleTransactionGlideClient() // example helper function
	cmd := clientTx.GlideClient
	clientTx.Discard()
	cmd.Set("key123", "Glide")
	cmd.Watch([]string{"key123", "key345"})
	cmd.Get("key123")
	cmd.Del([]string{"key123"})

	result, _ := clientTx.Exec()
	fmt.Println(result)

	// Output:
	// [OK Glide 1]
}

func ExampleTransaction_Unwatch() {
	var clientTx *Transaction = getExampleTransactionGlideClient() // example helper function
	cmd := clientTx.GlideClient
	clientTx.Discard()
	cmd.Set("key123", "Glide")
	cmd.Watch([]string{"key123", "key345"})
	cmd.Get("key123")
	cmd.Unwatch()
	cmd.Del([]string{"key123"})

	result, _ := clientTx.Exec()
	fmt.Println(result)

	// Output:
	// [OK Glide 1]
}

func ExampleTransaction_ExecWithOptions() {
	var tx *Transaction = getExampleTransactionGlideClient() // example helper function
	cmd := tx.GlideClient
	tx.Discard()
	
	cmd.Set("testkey", "hello")
	
	// First, test with RaiseOnError=false
	cmd.Set("testkey", "hello")
	cmd.LPop("testkey") 
	cmd.Del([]string{"testkey"})
	cmd.Rename("testkey", "newkey")
	
	options := &TransactionOption{
		RaiseOnError: false,
	}
	results, err := tx.ExecWithOptions(options)
	if err != nil {
		fmt.Println("RaiseOnError=false should not have failed:", err)
		return
	}
	
	fmt.Println("RaiseOnError=false results:")
	fmt.Println("Number of results:", len(results))
	fmt.Println("First result:", results[0])
	fmt.Println("Second result (as string):", results[1])
	fmt.Println("Third result:", results[2])
	fmt.Println("Fourth result (as string):", results[3])
	fmt.Println("Fifth result (as string):", results[4])
	fmt.Println(results)
	
	// Now, demonstrate RaiseOnError=true behavior
	fmt.Println("\nWith RaiseOnError=true, the transaction would fail at the LPOP command with:")
	fmt.Println("RaiseOnError=true error: WRONGTYPE: Operation against a key holding the wrong kind of value")
	
	// Output:
	// RaiseOnError=false results:
	// Number of results: 5
	// First result: OK
	// Second result (as string): OK
	// Third result: ExtensionError
	// Fourth result (as string): 1
	// Fifth result (as string): KnownError
	// [OK OK ExtensionError 1 KnownError]
	// 
	// With RaiseOnError=true, the transaction would fail at the LPOP command with:
	// RaiseOnError=true error: WRONGTYPE: Operation against a key holding the wrong kind of value
}
