// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

// #include "../lib.h"
import "C"

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/errors"
)

type Batch struct {
	Commands []Cmd
	IsAtomic bool
	Errors   []string // errors processing command args, spotted while batch is filled
}

type Cmd struct {
	RequestType uint32 // TODO why C.RequestType doesn't work?
	Args        []string
	// Response converter
	Converter func(any) (any, error)
}

func MakeCmd(requestType uint32, args []string, converter func(any) (any, error)) Cmd {
	return Cmd{RequestType: requestType, Args: args, Converter: converter}
}

func (b Batch) Convert(response []any) ([]any, error) {
	if len(response) != len(b.Commands) {
		return nil, &errors.RequestError{
			Msg: fmt.Sprintf("Response misaligned: received %d responses for %d commands", len(response), len(b.Commands)),
		}
	}
	for i, res := range response {
		converted, err := b.Commands[i].Converter(res)
		if err != nil {
			// TODO after merging with https://github.com/valkey-io/valkey-glide/pull/4090
			// wrap the error and list in which command it failed
			return nil, err
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
