// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// Optional arguments for `Ping` for standalone client
type PingOptions struct {
	Message string
}

// Optional arguments for `Ping` for cluster client
type ClusterPingOptions struct {
	*PingOptions
	*RouteOption
}

func (opts PingOptions) ToArgs() ([]string, error) {
	return []string{opts.Message}, nil
}
