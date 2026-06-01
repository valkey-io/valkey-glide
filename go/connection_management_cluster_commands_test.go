// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClusterClient_Ping() {
	var client *ClusterClient = getExampleClusterClient()
	result, err := client.Ping(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleClusterClient_PingWithOptions() {
	var client *ClusterClient = getExampleClusterClient()
	options := options.ClusterPingOptions{
		PingOptions: &options.PingOptions{
			Message: "hello",
		},
		RouteOption: nil,
	}
	result, err := client.PingWithOptions(context.Background(), options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: hello
}

func ExampleClusterClient_Echo() {
	var client *ClusterClient = getExampleClusterClient()
	result, err := client.Echo(context.Background(), "Hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello false}
}

func ExampleClusterClient_EchoWithOptions() {
	var client *ClusterClient = getExampleClusterClient()
	result, err := client.EchoWithOptions(context.Background(), "Hello World", options.RouteOption{Route: config.RandomRoute})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: Hello World
}

func ExampleClusterClient_ClientId() {
	var client *ClusterClient = getExampleClusterClient()
	result, err := client.ClientId(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result.IsSingleValue()
	fmt.Println(assert)

	// Output: true
}

func ExampleClusterClient_ClientIdWithOptions() {
	var client *ClusterClient = getExampleClusterClient()
	opts := options.RouteOption{Route: nil}
	result, err := client.ClientIdWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result.IsSingleValue()
	fmt.Println(assert)

	// Output: true
}

func ExampleClusterClient_ClientSetName() {
	var client *ClusterClient = getExampleClusterClient()
	connectionName := "ConnectionName-" + uuid.NewString()
	result, err := client.ClientSetName(context.Background(), connectionName)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClusterClient_ClientGetName() {
	var client *ClusterClient = getExampleClusterClient()
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(context.Background(), connectionName)
	result, err := client.ClientGetName(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value() == connectionName)

	// Output: true
}

func ExampleClusterClient_ClientSetNameWithOptions() {
	var client *ClusterClient = getExampleClusterClient()
	connectionName := "ConnectionName-" + uuid.NewString()
	opts := options.RouteOption{Route: nil}
	result, err := client.ClientSetNameWithOptions(context.Background(), connectionName, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClusterClient_ClientGetNameWithOptions() {
	var client *ClusterClient = getExampleClusterClient()
	connectionName := "ConnectionName-" + uuid.NewString()
	opts := options.RouteOption{Route: nil}
	client.ClientSetNameWithOptions(context.Background(), connectionName, opts)
	result, err := client.ClientGetNameWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue().Value() == connectionName)

	// Output: true
}

func ExampleClusterClient_ClientPause() {
	var client *ClusterClient = getExampleClusterClient()
	result, err := client.ClientPause(context.Background(), 0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClusterClient_ClientPauseWithOptions() {
	var client *ClusterClient = getExampleClusterClient()
	mode := options.ClientPauseModeWrite
	opts := options.ClientPauseClusterOptions{
		Mode:        &mode,
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	result, err := client.ClientPauseWithOptions(context.Background(), 0, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClusterClient_ClientUnpause() {
	var client *ClusterClient = getExampleClusterClient()
	result, err := client.ClientUnpause(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClusterClient_ClientUnpauseWithOptions() {
	var client *ClusterClient = getExampleClusterClient()
	opts := options.RouteOption{Route: config.AllPrimaries}
	result, err := client.ClientUnpauseWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

// TODO #6083: add end-to-end tests for OFF and SKIP once supported.
// Only ON is exercised end-to-end. OFF and SKIP suppress the server's
// replies, which would desync GLIDE's multiplexed connection because
// responses are matched to in-flight requests by order.
func ExampleClusterClient_ClientReply() {
	var client *ClusterClient = getExampleClusterClient()
	result, err := client.ClientReply(context.Background(), options.ClientReplyModeOn)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

// TODO #6083: add end-to-end tests for OFF and SKIP once supported.
// Only ON is exercised end-to-end. OFF and SKIP suppress the server's
// replies, which would desync GLIDE's multiplexed connection because
// responses are matched to in-flight requests by order.
func ExampleClusterClient_ClientReplyWithOptions() {
	var client *ClusterClient = getExampleClusterClient()
	opts := options.RouteOption{Route: config.AllPrimaries}
	result, err := client.ClientReplyWithOptions(context.Background(), options.ClientReplyModeOn, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func TestClientPauseAllThenUnpauseCluster(t *testing.T) {
	client, err := NewClusterClient(config.NewClusterClientConfiguration().
		WithAddress(&getClusterAddresses()[0]).
		WithRequestTimeout(10 * time.Second))
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}
	defer client.Close()

	ctx := context.Background()
	key := "clientPauseAll_then_clientUnpause_cluster_key"
	if _, err := client.Set(ctx, key, "before"); err != nil {
		t.Fatalf("SET before: %v", err)
	}

	mode := options.ClientPauseModeAll
	pauseOpts := options.ClientPauseClusterOptions{
		Mode:        &mode,
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	if result, err := client.ClientPauseWithOptions(ctx, 2*time.Second, pauseOpts); err != nil || result != "OK" {
		t.Fatalf("ClientPauseWithOptions: result=%q err=%v", result, err)
	}

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
		unpauseRouteOpts := options.RouteOption{Route: config.AllPrimaries}
		v, err := client.ClientUnpauseWithOptions(ctx, unpauseRouteOpts)
		unpauseCh <- stringResult{value: v, err: err}
	}()

	// Verify that none of the commands completes.
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

	after, err := client.Get(ctx, key)
	if err != nil || after.Value() != "after" {
		t.Errorf("GET after: value=%q err=%v", after.Value(), err)
	}
}

func TestClientPauseWriteThenUnpauseCluster(t *testing.T) {
	client, err := NewClusterClient(config.NewClusterClientConfiguration().
		WithAddress(&getClusterAddresses()[0]).
		WithRequestTimeout(10 * time.Second))
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}
	defer client.Close()

	ctx := context.Background()
	key := "clientPauseWrite_then_clientUnpause_cluster_key"
	if _, err := client.Set(ctx, key, "before"); err != nil {
		t.Fatalf("SET before: %v", err)
	}

	mode := options.ClientPauseModeWrite
	pauseOpts := options.ClientPauseClusterOptions{
		Mode:        &mode,
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	if result, err := client.ClientPauseWithOptions(ctx, 2*time.Second, pauseOpts); err != nil || result != "OK" {
		t.Fatalf("ClientPauseWithOptions: result=%q err=%v", result, err)
	}

	// Reads are not blocked by PAUSE WRITE.
	before, err := client.Get(ctx, key)
	if err != nil || before.Value() != "before" {
		t.Fatalf("GET before: value=%q err=%v", before.Value(), err)
	}

	type setResult struct {
		value string
		err   error
	}
	done := make(chan setResult, 1)
	go func() {
		v, err := client.Set(ctx, key, "after")
		done <- setResult{value: v, err: err}
	}()

	// Verify that SET has not completed because server is paused.
	select {
	case r := <-done:
		t.Fatalf("SET completed while paused: value=%q err=%v", r.value, r.err)
	case <-time.After(300 * time.Millisecond):
	}

	unpauseOpts := options.RouteOption{Route: config.AllPrimaries}
	if result, err := client.ClientUnpauseWithOptions(ctx, unpauseOpts); err != nil || result != "OK" {
		t.Fatalf("ClientUnpauseWithOptions: result=%q err=%v", result, err)
	}

	// Verify that SET completes once pause expires.
	select {
	case r := <-done:
		if r.err != nil || r.value != "OK" {
			t.Errorf("SET after unpause: value=%q err=%v", r.value, r.err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("SET did not complete within 5s of CLIENT UNPAUSE")
	}

	after, err := client.Get(ctx, key)
	if err != nil || after.Value() != "after" {
		t.Errorf("GET after: value=%q err=%v", after.Value(), err)
	}
}
