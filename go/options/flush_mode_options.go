// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// FlushMode represents the database flush operation mode
type FlushMode string

const (
	// SYNC flushes synchronously.
	// Since Valkey 6.2 and above.
	SYNC FlushMode = "SYNC"

	// ASYNC flushes asynchronously.
	ASYNC FlushMode = "ASYNC"
)

// FlushClusterOptions provides optional arguments for FlushAll for cluster client
type FlushClusterOptions struct {
	*FlushMode
	// Specifies the routing configuration for the command.
	// The client will route the command to the nodes defined by Route.
	// The command will be routed to all primary nodes, unless Route is provided.
	*RouteOption
}

// ToArgs converts the options to argument strings
func (opts *FlushClusterOptions) ToArgs() []string {
	args := []string{}
	if opts == nil {
		return []string{}
	}
	if opts.FlushMode != nil {
		args = append(args, string(*opts.FlushMode))
	}
	return args
}
