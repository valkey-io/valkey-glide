// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/glide/api/config"
)

type EchoOptions struct {
	message string
	Route   config.Route
}

func NewEchoOptionsBuilder() *EchoOptions {
	return &EchoOptions{}
}

func (echoOptions *EchoOptions) SetMessage(msg string) *EchoOptions {
	echoOptions.message = msg
	return echoOptions
}

func (echoOptions *EchoOptions) SetRoute(route config.Route) *EchoOptions {
	echoOptions.Route = route
	return echoOptions
}

func (opts *EchoOptions) ToArgs() ([]string, error) {
	args := []string{}

	if opts.message != "" {
		args = append(args, opts.message)
	}
	return args, nil
}
