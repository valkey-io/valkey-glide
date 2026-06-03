// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_ClientPause() {
	var client *Client = getExampleClient()
	result, err := client.ClientPause(context.Background(), 0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_ClientPauseWithOptions() {
	var client *Client = getExampleClient()
	result, err := client.ClientPauseWithOptions(context.Background(), 0, options.ClientPauseModeWrite)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_ClientUnpause() {
	var client *Client = getExampleClient()
	result, err := client.ClientUnpause(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_Ping() {
	var client *Client = getExampleClient()
	result, err := client.Ping(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleClient_PingWithOptions() {
	var client *Client = getExampleClient()
	options := options.PingOptions{Message: "hello"}
	result, err := client.PingWithOptions(context.Background(), options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: hello
}

func ExampleClient_Echo() {
	var client *Client = getExampleClient()
	result, err := client.Echo(context.Background(), "Hello World")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello World false}
}

func ExampleClient_ClientId() {
	var client *Client = getExampleClient()
	result, err := client.ClientId(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result > 0
	fmt.Println(assert)

	// Output: true
}

func ExampleClient_ClientSetName() {
	var client *Client = getExampleClient()
	result, err := client.ClientSetName(context.Background(), "ConnectionName")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_ClientGetName() {
	var client *Client = getExampleClient()
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(context.Background(), connectionName)
	result, err := client.ClientGetName(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value() == connectionName)

	// Output: true
}

func TestClientPauseAllThenUnpause(t *testing.T) {
	client := getExampleClient()
	ctx := context.Background()

	// Send SET command.
	key := "clientPauseAll_then_clientUnpause_key"
	if _, err := client.Set(ctx, key, "before"); err != nil {
		t.Fatalf("SET before: %v", err)
	}

	// Send PAUSE ALL command.
	if result, err := client.ClientPauseWithOptions(ctx, 2*time.Second, options.ClientPauseModeAll); err != nil ||
		result != "OK" {
		t.Fatalf("ClientPauseWithOptions: result=%q err=%v", result, err)
	}

	// Send GET, SET, and CLIENT UNPAUSE commands.
	type stringResult struct {
		value string
		err   error
	}

	getCh := make(chan stringResult, 1)
	setCh := make(chan stringResult, 1)
	unpauseCh := make(chan stringResult, 1)

	go func() {
		v, err := client.Get(ctx, key)
		getCh <- stringResult{value: v.Value(), err: err}
	}()
	go func() {
		v, err := client.Set(ctx, key, "after")
		setCh <- stringResult{value: v, err: err}
	}()
	go func() {
		v, err := client.ClientUnpause(ctx)
		unpauseCh <- stringResult{value: v, err: err}
	}()

	// Verify that none of the commands completed while paused.
	select {
	case r := <-getCh:
		t.Fatalf("GET completed while paused: value=%q err=%v", r.value, r.err)
	case r := <-setCh:
		t.Fatalf("SET completed while paused: value=%q err=%v", r.value, r.err)
	case r := <-unpauseCh:
		t.Fatalf("UNPAUSE completed while paused: value=%q err=%v", r.value, r.err)
	case <-time.After(300 * time.Millisecond):
	}

	// Verify that all commands complete once pause expires.
	collect := func(ch <-chan stringResult, name string) stringResult {
		select {
		case r := <-ch:
			return r
		case <-time.After(5 * time.Second):
			t.Fatalf("%s did not complete within 5s", name)
			return stringResult{}
		}
	}

	getRes := collect(getCh, "GET")
	setRes := collect(setCh, "SET")
	unpauseRes := collect(unpauseCh, "UNPAUSE")

	if getRes.err != nil || getRes.value != "before" {
		t.Errorf("GET: value=%q err=%v", getRes.value, getRes.err)
	}
	if setRes.err != nil || setRes.value != "OK" {
		t.Errorf("SET: value=%q err=%v", setRes.value, setRes.err)
	}
	if unpauseRes.err != nil || unpauseRes.value != "OK" {
		t.Errorf("UNPAUSE: value=%q err=%v", unpauseRes.value, unpauseRes.err)
	}

	// Verify final state.
	after, err := client.Get(ctx, key)
	if err != nil || after.Value() != "after" {
		t.Errorf("GET after: value=%q err=%v", after.Value(), err)
	}
}

func TestClientPauseWriteThenUnpause(t *testing.T) {
	client := getExampleClient()
	ctx := context.Background()

	// Send SET command.
	key := "clientPauseWrite_then_clientUnpause_key"
	if _, err := client.Set(ctx, key, "before"); err != nil {
		t.Fatalf("SET before: %v", err)
	}

	// Send PAUSE WRITE command.
	if result, err := client.ClientPauseWithOptions(ctx, 2*time.Second, options.ClientPauseModeWrite); err != nil ||
		result != "OK" {
		t.Fatalf("ClientPauseWithOptions: result=%q err=%v", result, err)
	}

	// Verify that reads are not blocked by PAUSE WRITE.
	before, err := client.Get(ctx, key)
	if err != nil || before.Value() != "before" {
		t.Fatalf("GET before: value=%q err=%v", before.Value(), err)
	}

	// Send SET command.
	type stringResult struct {
		value string
		err   error
	}

	setCh := make(chan stringResult, 1)

	go func() {
		v, err := client.Set(ctx, key, "after")
		setCh <- stringResult{value: v, err: err}
	}()

	// Verify that SET has not completed while paused.
	select {
	case r := <-setCh:
		t.Fatalf("SET completed while paused: value=%q err=%v", r.value, r.err)
	case <-time.After(300 * time.Millisecond):
	}

	// Send CLIENT UNPAUSE command.
	if result, err := client.ClientUnpause(ctx); err != nil || result != "OK" {
		t.Fatalf("ClientUnpause: result=%q err=%v", result, err)
	}

	// Verify that SET completes once unpaused.
	select {
	case r := <-setCh:
		if r.err != nil || r.value != "OK" {
			t.Errorf("SET after unpause: value=%q err=%v", r.value, r.err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("SET did not complete within 5s of CLIENT UNPAUSE")
	}

	// Verify final state.
	after, err := client.Get(ctx, key)
	if err != nil || after.Value() != "after" {
		t.Errorf("GET after: value=%q err=%v", after.Value(), err)
	}
}
