// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// Optional arguments for `Echo` for standalone client
type EchoOptions struct {
	Message string
}

// Optional arguments for `Echo` for cluster client
type ClusterEchoOptions struct {
	*EchoOptions
	*RouteOption
}

func (opts *EchoOptions) ToArgs() ([]string, error) {
	if opts == nil {
		return []string{}, nil
	}
	args := []string{}
	if opts.Message != "" {
		args = append(args, opts.Message)
	}
	return args, nil
}
