// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "github.com/valkey-io/valkey-glide/go/glide/api/options"

// Supports commands and transactions for the "Stream" group of commands for standalone and cluster clients.
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

	// Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the stream.
	//  values   - Field-value pairs to be added to the entry.
	//  options  - Stream add options.
	//
	// Return value:
	//  The id of the added entry.
	//
	// For example:
	//  options := options.NewXAddOptions().SetId("100-500").SetDontMakeNewStream()
	//  result, err := client.XAddWithOptions("myStream", [][]string{{"field1", "value1"}, {"field2", "value2"}}, options)
	//  result.IsNil(): false
	//  result.Value(): "100-500"
	//
	// [valkey.io]: https://valkey.io/commands/xadd/
	XAddWithOptions(key string, values [][]string, options *options.XAddOptions) (Result[string], error)

	// Reads entries from the given streams.
	//
	// Note:
	//  When in cluster mode, all keys in `keysAndIds` must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  keysAndIds - A map of keys and entry IDs to read from.
	//
	// Return value:
	// A `map[string]map[string][][]string` of stream keys to a map of stream entry IDs mapped to an array entries or `nil` if
	// key does not exist.
	//
	// For example:
	//  result, err := client.XRead({"stream1": "0-0", "stream2": "0-1"})
	//  err == nil: true
	//  result: map[string]map[string][][]string{
	//    "stream1": {"0-1": {{"field1", "value1"}}, "0-2": {{"field2", "value2"}, {"field2", "value3"}}},
	//    "stream2": {},
	//  }
	//
	// [valkey.io]: https://valkey.io/commands/xread/
	XRead(keysAndIds map[string]string) (map[string]map[string][][]string, error)

	// Reads entries from the given streams.
	//
	// Note:
	//  When in cluster mode, all keys in `keysAndIds` must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  keysAndIds - A map of keys and entry IDs to read from.
	//  options - Options detailing how to read the stream.
	//
	// Return value:
	// A `map[string]map[string][][]string` of stream keys to a map of stream entry IDs mapped to an array entries or `nil` if
	// key does not exist.
	//
	// For example:
	//  options := options.NewXReadOptions().SetBlock(100500)
	//  result, err := client.XReadWithOptions({"stream1": "0-0", "stream2": "0-1"}, options)
	//  err == nil: true
	//  result: map[string]map[string][][]string{
	//    "stream1": {"0-1": {{"field1", "value1"}}, "0-2": {{"field2", "value2"}, {"field2", "value3"}}},
	//    "stream2": {},
	//  }
	//
	// [valkey.io]: https://valkey.io/commands/xread/
	XReadWithOptions(keysAndIds map[string]string, options *options.XReadOptions) (map[string]map[string][][]string, error)
}
