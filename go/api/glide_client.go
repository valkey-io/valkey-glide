// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

// GlideClient interface compliance check.
var _ IGlideClient = (*GlideClient)(nil)

// IGlideClient is a client used for connection in Standalone mode.
type IGlideClient interface {
	BaseClient
	GenericCommands
	ServerManagementCommands
}

// GlideClient implements standalone mode operations by extending baseClient functionality.
type GlideClient struct {
	*baseClient
}

// NewGlideClient creates a [IGlideClient] in standalone mode using the given [GlideClientConfiguration].
func NewGlideClient(config *GlideClientConfiguration) (IGlideClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &GlideClient{client}, nil
}

func (client *GlideClient) CustomCommand(args []string) (interface{}, error) {
	res, err := client.executeCommand(C.CustomCommand, args)
	if err != nil {
		return nil, err
	}
	return handleInterfaceResponse(res)
}

func (client *GlideClient) ConfigSet(parameters map[string]string) (string, error) {
	result, err := client.executeCommand(C.ConfigSet, utils.MapToString(parameters))
	if err != nil {
		return "", err
	}
	return handleStringResponse(result)
}

func (client *GlideClient) ConfigGet(args []string) (map[string]string, error) {
	res, err := client.executeCommand(C.ConfigGet, args)
	if err != nil {
		return nil, err
	}
	return handleStringToStringMapResponse(res)
}

func (client *GlideClient) Select(index int64) (string, error) {
	result, err := client.executeCommand(C.Select, []string{utils.IntToString(index)})
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}
