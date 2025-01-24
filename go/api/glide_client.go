// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

// GlideClient interface compliance check.
var _ GlideClientCommands = (*GlideClient)(nil)

// GlideClientCommands is a client used for connection in Standalone mode.
type GlideClientCommands interface {
	BaseClient
	GenericCommands
	ServerManagementCommands
}

// GlideClient implements standalone mode operations by extending baseClient functionality.
type GlideClient struct {
	*baseClient
}

// NewGlideClient creates a [GlideClientCommands] in standalone mode using the given [GlideClientConfiguration].
func NewGlideClient(config *GlideClientConfiguration) (GlideClientCommands, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &GlideClient{client}, nil
}

// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed
// command.
//
// See [Valkey GLIDE Wiki] for details on the restrictions and limitations of the custom command API.
//
// This function should only be used for single-response commands. Commands that don't return complete response and awaits
// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
//
// Parameters:
//
//	args - Arguments for the custom command including the command name.
//
// Return value:
//
//	The returned value for the custom command.
//
// For example:
//
//	result, err := client.CustomCommand([]string{"ping"})
//	result.(string): "PONG"
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *GlideClient) CustomCommand(args []string) (interface{}, error) {
	res, err := client.executeCommand(C.CustomCommand, args)
	if err != nil {
		return nil, err
	}
	return handleInterfaceResponse(res)
}

// Sets configuration parameters to the specified values.
//
// Note: Prior to Version 7.0.0, only one parameter can be send.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	parameters - A map consisting of configuration parameters and their respective values to set.
//
// Return value:
//
//	`"OK"` if all configurations have been successfully set. Otherwise, raises an error.
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
// See [valkey.io] for details.
//
// Parameters:
//
//	args - A slice of configuration parameter names to retrieve values for.
//
// Return value:
//
//	A map of api.Result[string] corresponding to the configuration parameters.
//
// For example:
//
//	result, err := client.ConfigGet([]string{"timeout" , "maxmemory"})
//	// result["timeout"] = "1000"
//	// result["maxmemory"] = "1GB"
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (client *GlideClient) ConfigGet(args []string) (map[string]string, error) {
	res, err := client.executeCommand(C.ConfigGet, args)
	if err != nil {
		return nil, err
	}
	return handleStringToStringMapResponse(res)
}

// Select changes the currently selected database.
//
// Parameters:
//
//	index - The index of the database to select.
//
// Return value:
//
//	A simple `"OK"` response.
//
// Example:
//
//	result, err := client.Select(2)
//	result: "OK"
//
// [valkey.io]: https://valkey.io/commands/select/
func (client *GlideClient) Select(index int64) (string, error) {
	result, err := client.executeCommand(C.Select, []string{utils.IntToString(index)})
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}

// Returns the number of keys in the currently selected database.
//
// Return value:
//
//	The number of keys in the currently selected database.
//
// Example:
//
//	result, err := client.DBSize()
//	if err != nil {
//		// handle error
//	}
//	fmt.Println(result) // Output: 1
//
// [valkey.io]: https://valkey.io/commands/dbsize/
func (client *GlideClient) DBSize() (int64, error) {
	result, err := client.executeCommand(C.DBSize, []string{})
	if err != nil {
		return defaultIntResponse, err
	}
	return handleIntResponse(result)
}
