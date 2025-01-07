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

	// Changes the ownership of a pending message.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the stream.
	//  group    - The name of the consumer group.
	//  consumer - The name of the consumer.
	//  minIdleTime - The minimum idle time in milliseconds.
	//  ids        - The ids of the entries to claim.
	//
	// Return value:
	//  A `map of message entries with the format `{"entryId": [["entry", "data"], ...], ...}` that were claimed by
	//  the consumer.
	//
	// Example:
	//
	// [valkey.io]: https://valkey.io/commands/xclaim/
	XClaim(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
	) (map[Result[string]][][]Result[string], error)

	// Changes the ownership of a pending message.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the stream.
	//  group    - The name of the consumer group.
	//  consumer - The name of the consumer.
	//  minIdleTime - The minimum idle time in milliseconds.
	//  ids        - The ids of the entries to claim.
	//  options    - Stream claim options.
	//
	// Return value:
	//  A `map` of message entries with the format `{"entryId": [["entry", "data"], ...], ...}` that were claimed by
	//  the consumer.
	//
	// Example:
	//  result, err := ...
	//
	// [valkey.io]: https://valkey.io/commands/xclaim/
	XClaimWithOptions(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
		options *options.StreamClaimOptions,
	) (map[Result[string]][][]Result[string], error)

	// Changes the ownership of a pending message. This function returns an `array` with
	// only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the stream.
	//  group    - The name of the consumer group.
	//  consumer - The name of the consumer.
	//  minIdleTime - The minimum idle time in milliseconds.
	//  ids        - The ids of the entries to claim.
	//  options    - Stream claim options.
	//
	// Return value:
	//  An array of the ids of the entries that were claimed by the consumer.
	//
	// Example:
	//  result, err := ...
	// [valkey.io]: https://valkey.io/commands/xclaim/
	XClaimJustId(key string, group string, consumer string, minIdleTime int64, ids []string) ([]Result[string], error)

	// Changes the ownership of a pending message. This function returns an `array` with
	// only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the stream.
	//  group    - The name of the consumer group.
	//  consumer - The name of the consumer.
	//  minIdleTime - The minimum idle time in milliseconds.
	//  ids        - The ids of the entries to claim.
	//  options    - Stream claim options.
	//
	// Return value:
	//  An array of the ids of the entries that were claimed by the consumer.
	//
	// Example:
	//  result, err := ...
	//
	// [valkey.io]: https://valkey.io/commands/xclaim/
	XClaimJustIdWithOptions(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
		options *options.StreamClaimOptions,
	) ([]Result[string], error)
}
