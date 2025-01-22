// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

// GlideClient interface compliance check.
var _ GlideClient = (*glideClient)(nil)

// GlideClient is a client used for connection in Standalone mode.
type GlideClient interface {
	BaseClient
	GenericCommands
	ServerManagementCommands
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
		return defaultStringResponse, err
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
		return defaultStringResponse, err
	}

	return handleStringResponse(result)
}

// Gets information and statistics about the server.
//
// See [valkey.io] for details.
//
// Return value:
//
//	A string with the information for the default sections.
//
// Example:
//
//	response, err := standaloneClient.Info(opts)
//	if err != nil {
//		// handle error
//	}
//	fmt.Println(response)
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *glideClient) Info() (string, error) {
	return client.InfoWithOptions(InfoOptions{[]Section{}})
}

// Gets information and statistics about the server.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	options - Additional command parameters, see [InfoOptions] for more details.
//
// Return value:
//
//	A string containing the information for the sections requested.
//
// Example:
//
//	opts := api.InfoOptions{Sections: []api.Section{api.Server}}
//	response, err := standaloneClient.InfoWithOptions(opts)
//	if err != nil {
//		// handle error
//	}
//	fmt.Println(response)
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *glideClient) InfoWithOptions(options InfoOptions) (string, error) {
	result, err := client.executeCommand(C.Info, options.toArgs())
	if err != nil {
		return defaultStringResponse, err
	}

	return handleStringResponse(result)
}
