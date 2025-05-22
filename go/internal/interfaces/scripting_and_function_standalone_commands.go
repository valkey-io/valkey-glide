// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// Supports commands and transactions for the "Scripting and Function" group for a standalone
// client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=scripting
type ScriptingAndFunctionStandaloneCommands interface {
	FunctionStats(ctx context.Context) (map[string]models.FunctionStatsResult, error)

	FunctionDelete(ctx context.Context, libName string) (string, error)

	FunctionKill(ctx context.Context) (string, error)

	FunctionList(ctx context.Context, query models.FunctionListQuery) ([]models.LibraryInfo, error)

	FunctionDump(ctx context.Context) (string, error)

	FunctionRestore(ctx context.Context, payload string) (string, error)

	FunctionRestoreWithPolicy(ctx context.Context, payload string, policy constants.FunctionRestorePolicy) (string, error)
}
