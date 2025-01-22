// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"github.com/valkey-io/valkey-glide/go/glide/api/errors"
	"github.com/valkey-io/valkey-glide/go/glide/api/options"
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

// GlideClient interface compliance check.
var _ GlideClient = (*glideClient)(nil)

// GlideClient is a client used for connection in Standalone mode.
type GlideClient interface {
	BaseClient
	GenericCommands
	ServerManagementCommands
	ConnectionManagementCommands
}

// glideClient implements standalone mode operations by extending baseClient functionality.
type glideClient struct {
	*baseClient
}

// NewGlideClient creates a [GlideClient] in standalone mode using the given [GlideClientConfiguration].
func NewGlideClient(config *GlideClientConfiguration) (GlideClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &glideClient{client}, nil
}

func (client *glideClient) CustomCommand(args []string) (interface{}, error) {
	res, err := client.executeCommand(C.CustomCommand, args)
	if err != nil {
		return nil, err
	}
	return handleInterfaceResponse(res)
}

func (client *glideClient) ConfigSet(parameters map[string]string) (string, error) {
	result, err := client.executeCommand(C.ConfigSet, utils.MapToString(parameters))
	if err != nil {
		return "", err
	}
	return handleStringResponse(result)
}

func (client *glideClient) ConfigGet(args []string) (map[string]string, error) {
	res, err := client.executeCommand(C.ConfigGet, args)
	if err != nil {
		return nil, err
	}
	return handleStringToStringMapResponse(res)
}

func (client *glideClient) Select(index int64) (string, error) {
	result, err := client.executeCommand(C.Select, []string{utils.IntToString(index)})
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}

// Echo the provided message back.
// The command will be routed a random node.
//
// Parameters:
//
//	message - The provided message.
//
// Return value:
//
//	The provided message
//
// For example:
//
//	 result, err := client.Echo("Hello World")
//		if err != nil {
//		    // handle error
//		}
//		fmt.Println(result.Value()) // Output: Hello World
//
// [valkey.io]: https://valkey.io/commands/echo/
func (client *baseClient) Echo(message string) (Result[string], error) {
	result, err := client.executeCommand(C.Echo, []string{message})
	if err != nil {
		return CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Pings the server.
//
// Return value:
//
//	Returns "PONG".
//
// For example:
//
//	result, err := client.Ping()
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *glideClient) Ping() (string, error) {
	result, err := client.executeCommand(C.Ping, []string{})
	if err != nil {
		return defaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Pings the server.
//
// Parameters:
//
//	pingOptions - The PingOptions type.
//
// Return value:
//
//	Returns "PONG" or the copy of message.
//
// For example:
//
//	options := options.NewPingOptionsBuilder().SetMessage("hello")
//	result, err := client.PingWithOptions(options)
//	result: "hello"
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *glideClient) PingWithOptions(opts *options.PingOptions) (string, error) {
	if opts != nil && opts.Route != nil {
		return defaultStringResponse, &errors.RequestError{Msg: "Route option is only available in cluster mode"}
	}
	args, err := opts.ToArgs()
	if err != nil {
		return defaultStringResponse, err
	}
	result, err := client.executeCommand(C.Ping, append([]string{}, args...))
	if err != nil {
		return defaultStringResponse, err
	}

	return handleStringResponse(result)
}
