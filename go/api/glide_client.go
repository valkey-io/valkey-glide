// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"
import "github.com/valkey-io/valkey-glide/go/glide/utils"

// GlideClient is a client used for connection in Standalone mode.
type GlideClient struct {
	*baseClient
}

// NewGlideClient creates a [GlideClient] in standalone mode using the given [GlideClientConfiguration].
func NewGlideClient(config *GlideClientConfiguration) (*GlideClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &GlideClient{client}, nil
}

// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command, including
// the command name and subcommands, should be added as a separate value in args. The returning value depends on the executed
// command.
//
// This function should only be used for single-response commands. Commands that don't return complete response and awaits
// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
//
// For example, to return a list of all pub/sub clients:
//
//	client.CustomCommand([]string{"CLIENT", "LIST","TYPE", "PUBSUB"})
//
// TODO: Add support for complex return types.
func (client *GlideClient) CustomCommand(args []string) (interface{}, error) {
	res, err := client.executeCommand(C.CustomCommand, args)
	if err != nil {
		return nil, err
	}

	return handleStringOrNullResponse(res)
}

// Sets configuration parameters to the specified values.
//
// Note: Prior to Version 7.0.0, only one parameter can be send.
//
// Parameters:
//
//	parameters - A map consisting of configuration parameters and their respective values to set.
//
// Return value:
//
//	"OK" if all configurations have been successfully set. Otherwise, raises an error.
//
// For example:
//
//	result, err := client.ConfigSet(map[string]string{"timeout": "1000", "maxmemory": "1GB"})
//	result: "OK"
//
// [valkey.io]: https://valkey.io/commands/config-set/
func (client *GlideClient) ConfigSet(parameters map[string]string) (string, error) {
	result, err := client.executeCommand(C.ConfigSet, utils.MapToString(parameters))
	if err != nil {
		return "", err
	}
	return handleStringResponse(result)
}

// Gets the values of configuration parameters.
//
// Note: Prior to Version 7.0.0, only one parameter can be send.
//
// Parameters:
//
//	args - A slice of configuration parameter names to retrieve values for.
//
// Return value:
//
//	A map of values corresponding to the configuration parameters.
//
// For example:
//
//	result, err := client.ConfigGet([]string{"timeout" , "maxmemory"})
//	result["timeout"] = "1000"
//	result["maxmemory"] = "1GB"
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (client *GlideClient) ConfigGet(args []string) (map[string]string, error) {
	res, err := client.executeCommand(C.ConfigGet, args)
	if err != nil {
		return nil, err
	}
	return handleStringToStringMapResponse(res)
}
