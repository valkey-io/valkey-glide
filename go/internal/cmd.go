// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

// #include "../lib.h"
import "C"

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/config"
)

type Batch struct {
	Commands []Cmd
	IsAtomic bool
	Errors   []error // errors processing command args, spotted while batch is filled
}

type Cmd struct {
	RequestType uint32
	Args        []string
	Converter   func(any) (any, error) // Response converter
}

func MakeCmd(requestType uint32, args []string, converter func(any) (any, error)) Cmd {
	return Cmd{RequestType: requestType, Args: args, Converter: converter}
}

func (b Batch) Convert(response []any) ([]any, error) {
	if len(response) != len(b.Commands) {
		return nil, fmt.Errorf("response misaligned: received %d responses for %d commands", len(response), len(b.Commands))
	}
	for i, res := range response {
		converted, err := b.Commands[i].Converter(res)
		if err != nil {
			return nil, fmt.Errorf("failed to process response for %d'th command: %w", i, err)
		}
		response[i] = converted
	}
	return response, nil
}

type BatchOptions struct {
	Timeout              *uint32
	Route                *config.Route
	RetryServerError     *bool
	RetryConnectionError *bool
}
