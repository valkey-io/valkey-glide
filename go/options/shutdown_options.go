// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// ShutdownMode represents the persistence mode for the SHUTDOWN command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/shutdown/
type ShutdownMode string

const (
	// ShutdownModeSave forces a DB save before shutting down.
	ShutdownModeSave ShutdownMode = "SAVE"

	// ShutdownModeNoSave prevents a DB save before shutting down.
	ShutdownModeNoSave ShutdownMode = "NOSAVE"
)

// ShutdownOptions provides optional arguments for the SHUTDOWN command.
//
// The SHUTDOWN command shuts down the server gracefully. It supports optional persistence
// and behavior modifiers. Note: the server closes the connection upon receiving SHUTDOWN,
// so the client will receive a connection error.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/shutdown/
type ShutdownOptions struct {
	// Mode specifies whether to SAVE or NOSAVE before shutdown. If nil, the server
	// uses its configured save policy.
	Mode *ShutdownMode

	// Now forces the server to skip waiting for lagging replicas.
	Now bool

	// Force forces the shutdown even if errors occur during persistence.
	Force bool

	// Abort cancels an ongoing shutdown that was initiated with a timeout.
	Abort bool
}

// ToArgs converts ShutdownOptions to a command argument slice.
func (opts *ShutdownOptions) ToArgs() []string {
	if opts == nil {
		return []string{}
	}

	if opts.Abort {
		return []string{"ABORT"}
	}

	args := []string{}
	if opts.Mode != nil {
		args = append(args, string(*opts.Mode))
	}
	if opts.Now {
		args = append(args, "NOW")
	}
	if opts.Force {
		args = append(args, "FORCE")
	}
	return args
}

// ShutdownClusterOptions provides optional arguments for SHUTDOWN in cluster mode.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/shutdown/
type ShutdownClusterOptions struct {
	*ShutdownOptions
	*RouteOption
}
