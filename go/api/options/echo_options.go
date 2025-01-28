// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/glide/api/config"
)

// Optional arguments for `Echo` for standalone client
type EchoOptions struct {
	Message string
}

// Optional arguments for `Echo` for cluster client
type ClusterEchoOptions struct {
	*EchoOptions
	// Specifies the routing configuration for the command.
	// The client will route the command to the nodes defined by *Route*.
	Route *config.Route
}

func (opts *EchoOptions) ToArgs() []string {
	if opts == nil {
		return []string{}
	}
	args := []string{}
	if opts.Message != "" {
		args = append(args, opts.Message)
	}
	return args
}
