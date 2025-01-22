// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/glide/api/config"
)

type PingOptions struct {
	message string
	Route   config.Route
}

func NewPingOptionsBuilder() *PingOptions {
	return &PingOptions{}
}

func (pingOptions *PingOptions) SetMessage(msg string) *PingOptions {
	pingOptions.message = msg
	return pingOptions
}

func (pingOptions *PingOptions) SetRoute(route config.Route) *PingOptions {
	pingOptions.Route = route
	return pingOptions
}

func (opts *PingOptions) ToArgs() ([]string, error) {
	args := []string{}

	if opts.message != "" {
		args = append(args, opts.message)
	}
	return args, nil
}
