// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// ClientReplyMode represents the mode for the `CLIENT REPLY` command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/client-reply/
type ClientReplyMode string

const (
	// ClientReplyModeOn resumes normal reply behavior.
	ClientReplyModeOn ClientReplyMode = "ON"

	// ClientReplyModeOff suppresses all replies until `CLIENT REPLY ON` is sent.
	ClientReplyModeOff ClientReplyMode = "OFF"

	// ClientReplyModeSkip suppresses the reply for the next command only.
	ClientReplyModeSkip ClientReplyMode = "SKIP"
)
