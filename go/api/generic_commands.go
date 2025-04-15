// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// GenericCommands supports commands for the "Generic Commands" group for standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#generic
type GenericCommands interface {
	CustomCommand(args []string) (interface{}, error)

	Move(key string, dbIndex int64) (bool, error)

	Scan(cursor int64) (string, []string, error)

	ScanWithOptions(cursor int64, scanOptions options.ScanOptions) (string, []string,
		error)

	RandomKey() (Result[string], error)
}
