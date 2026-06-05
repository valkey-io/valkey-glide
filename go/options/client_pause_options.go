// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// ClientPauseMode represents the mode for the `CLIENT PAUSE` command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/client-pause/
type ClientPauseMode string

const (
	// ClientPauseModeAll pauses all client commands.
	ClientPauseModeAll ClientPauseMode = "ALL"

	// ClientPauseModeWrite pauses client write commands.
	ClientPauseModeWrite ClientPauseMode = "WRITE"
)

// ClientPauseClusterOptions provides optional arguments for `CLIENT PAUSE` for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/client-pause/
type ClientPauseClusterOptions struct {
	// Mode specifies the pause mode. If nil, defaults to [ClientPauseModeAll].
	Mode *ClientPauseMode

	// RouteOption specifies the routing configuration for the command.
	*RouteOption
}
