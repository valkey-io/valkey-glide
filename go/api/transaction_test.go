// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api"
)

func ExampleGlideClient_Transaction() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	tx := api.NewTransaction(client)
	cmd := tx.GlideClient
	cmd.Set("key123", "Glide")
	cmd.Get("key123")
	cmd.Del([]string{"key3"})

	result, err := tx.Exec()
	fmt.Println(result)

	// Output:
	// [OK Glide 1]
}
