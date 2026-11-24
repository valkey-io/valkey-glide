// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// fakeStandaloneClient is a minimal standaloneClient double that records the argv it was
// called with and returns a canned reply/error, so the standalone wrapper functions below can
// be exercised without a live server.
type fakeStandaloneClient struct {
	lastArgs []string
	reply    any
	err      error
}

func (f *fakeStandaloneClient) CustomCommand(ctx context.Context, args []string) (any, error) {
	f.lastArgs = args
	return f.reply, f.err
}

// Unit tests for the five standalone-only wrapper functions (FtHybrid, FtHybridWithCursor,
// FtAggregateWithCursor, FtCursorRead, FtCursorDel), which have a distinct exec path
// (execStandalone, via the standaloneClient interface) from their Cluster* counterparts. These
// use a fake standaloneClient directly rather than the defaultClusterClient() live tests use.

func TestFtHybrid_Standalone(t *testing.T) {
	fake := &fakeStandaloneClient{
		reply: []any{map[string]any{"__key": "doc1"}},
	}
	searchArm := options.FtHybridSearchArm{Query: "hello"}
	vsimArm := options.FtHybridVsimArm{Field: "vec", VectorParam: "$q"}
	opts := &options.FtHybridOptions{Params: []options.FtAggregateParam{{Key: "q", Value: "v"}}}

	rows, err := FtHybrid(context.Background(), fake, "idx", searchArm, vsimArm, opts)
	require.NoError(t, err)
	require.Len(t, rows, 1)
	assert.Equal(t, "doc1", rows[0]["__key"])
	assert.Equal(t, []string{
		"FT.HYBRID", "idx", "SEARCH", "hello", "VSIM", "@vec", "$q", "PARAMS", "2", "q", "v",
	}, fake.lastArgs)
}

func TestFtHybridWithCursor_Standalone(t *testing.T) {
	fake := &fakeStandaloneClient{
		reply: []any{[]any{map[string]any{"__key": "doc1"}}, int64(42)},
	}
	searchArm := options.FtHybridSearchArm{Query: "hello"}
	vsimArm := options.FtHybridVsimArm{Field: "vec", VectorParam: "$q"}
	opts := &options.FtHybridOptions{
		Params:     []options.FtAggregateParam{{Key: "q", Value: "v"}},
		WithCursor: &options.FtWithCursor{},
	}

	rows, cursorID, err := FtHybridWithCursor(context.Background(), fake, "idx", searchArm, vsimArm, opts)
	require.NoError(t, err)
	require.Len(t, rows, 1)
	assert.Equal(t, int64(42), cursorID)
	assert.Contains(t, fake.lastArgs, "WITHCURSOR")
}

func TestFtAggregateWithCursor_Standalone(t *testing.T) {
	fake := &fakeStandaloneClient{
		reply: []any{[]any{map[string]any{"n": "1"}}, int64(7)},
	}
	opts := &options.FtAggregateOptions{WithCursor: &options.FtWithCursor{}}

	rows, cursorID, err := FtAggregateWithCursor(context.Background(), fake, "idx", "*", opts)
	require.NoError(t, err)
	require.Len(t, rows, 1)
	assert.Equal(t, int64(7), cursorID)
	assert.Equal(t, []string{"FT.AGGREGATE", "idx", "*", "WITHCURSOR"}, fake.lastArgs)
}

func TestFtCursorRead_Standalone(t *testing.T) {
	fake := &fakeStandaloneClient{
		reply: []any{[]any{map[string]any{"n": "1"}}, int64(0)},
	}
	count := 5
	rows, cursorID, err := FtCursorRead(context.Background(), fake, "idx", 42, &count)
	require.NoError(t, err)
	require.Len(t, rows, 1)
	assert.Equal(t, int64(0), cursorID)
	assert.Equal(t, []string{"FT.CURSOR", "READ", "idx", "42", "COUNT", "5"}, fake.lastArgs)
}

func TestFtCursorDel_Standalone(t *testing.T) {
	fake := &fakeStandaloneClient{reply: "OK"}
	result, err := FtCursorDel(context.Background(), fake, "idx", 42)
	require.NoError(t, err)
	assert.Equal(t, "OK", result)
	assert.Equal(t, []string{"FT.CURSOR", "DEL", "idx", "42"}, fake.lastArgs)
}

// TestFtCursorDel_Standalone_PropagatesError mirrors Python's cluster_mode=[True, False]
// parametrization intent: the standalone exec path must propagate upstream errors the same way
// the cluster path does.
func TestFtCursorDel_Standalone_PropagatesError(t *testing.T) {
	fake := &fakeStandaloneClient{err: assert.AnError}
	_, err := FtCursorDel(context.Background(), fake, "idx", 42)
	assert.ErrorIs(t, err, assert.AnError)
}
