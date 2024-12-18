// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
//
// void successCallback(void *channelPtr, struct CommandResponse *message);
// void failureCallback(void *channelPtr, char *errMessage, RequestErrorType errType);
import "C"

// GlideClusterClient is a client used for connection in cluster mode.
type GlideClusterClient struct {
	*baseClient
}

// NewGlideClusterClient creates a [GlideClusterClient] in cluster mode using the given [GlideClusterClientConfiguration].
func NewGlideClusterClient(config *GlideClusterClientConfiguration) (*GlideClusterClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &GlideClusterClient{client}, nil
}

// Pings the server and returns "PONG".
//
// Paramters:
//
//	route - Specifies the routing configuration for the command. The client will route the command to the nodes defined by
//
// route.
//
// Return value:
//
//	A Result[string] containing "PONG" is returned.
//
// For example:
//
//	result, err := client.PingWithRoute(api.SimpleNodeRouteAllPrimaries)
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *GlideClusterClient) PingWithRoute(route route) (Result[string], error) {
	result, err := client.executeCommandWithRoute(C.Ping, []string{}, route)
	if err != nil {
		return CreateNilStringResult(), err
	}

	return handleStringResponse(result)
}

// Pings the server and returns the message.
//
// Paramters:
//
//	route - Specifies the routing configuration for the command. The client will route the command to the nodes defined by
//
// route.
//
// Return value:
//
//	A Result[string] containing message is returned.
//
// For example:
//
//	result, err := client.PingWithRouteAndMessage("Hello", api.SimpleNodeRouteAllPrimaries)
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *GlideClusterClient) PingWithRouteAndMessage(message string, route route) (Result[string], error) {
	result, err := client.executeCommandWithRoute(C.Ping, []string{message}, route)
	if err != nil {
		return CreateNilStringResult(), err
	}

	return handleStringResponse(result)
}
