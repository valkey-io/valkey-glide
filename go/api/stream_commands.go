// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "Stream Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#stream
type StreamCommands interface {
	// Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the stream.
	//  values   - Field-value pairs to be added to the entry.
	//
	// Return value:
	//  The id of the added entry.
	//
	// For example:
	//  result, err := client.XAdd("myStream", [][]string{{"field1", "value1"}, {"field2", "value2"}})
	//  result.IsNil(): false
	//  result.Value(): "1526919030474-55"
	//
	// [valkey.io]: https://valkey.io/commands/xadd/
	XAdd(key string, values [][]string) (Result[string], error)
}
