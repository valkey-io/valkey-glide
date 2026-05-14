// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// parseLatencyHistoryEntries: typed parsing happy path.
func TestParseLatencyHistoryEntries_Typed(t *testing.T) {
	input := []any{
		[]any{int64(1700000000), int64(123)},
		[]any{int64(1700000060), int64(45)},
	}

	entries, err := parseLatencyHistoryEntries(input)
	require.NoError(t, err)
	require.Len(t, entries, 2)
	assert.Equal(t, models.LatencyHistoryEntry{Timestamp: 1700000000, LatencyMs: 123}, entries[0])
	assert.Equal(t, models.LatencyHistoryEntry{Timestamp: 1700000060, LatencyMs: 45}, entries[1])
}

func TestParseLatencyHistoryEntries_Empty(t *testing.T) {
	entries, err := parseLatencyHistoryEntries([]any{})
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestParseLatencyHistoryEntries_Nil(t *testing.T) {
	entries, err := parseLatencyHistoryEntries(nil)
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestParseLatencyHistoryEntries_RejectsWrongTopType(t *testing.T) {
	_, err := parseLatencyHistoryEntries("not an array")
	require.Error(t, err)
	assert.Contains(t, err.Error(), "unexpected type for LATENCY HISTORY response")
}

func TestParseLatencyHistoryEntries_RejectsWrongInnerType(t *testing.T) {
	_, err := parseLatencyHistoryEntries([]any{"not a pair"})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "unexpected type for LATENCY HISTORY entry")
}

func TestParseLatencyHistoryEntries_RejectsShortPair(t *testing.T) {
	_, err := parseLatencyHistoryEntries([]any{[]any{int64(1)}})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "expected at least 2")
}

func TestParseLatencyHistoryEntries_RejectsNonIntTimestamp(t *testing.T) {
	_, err := parseLatencyHistoryEntries([]any{[]any{"oops", int64(1)}})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "timestamp")
}

func TestParseLatencyHistoryEntries_RejectsNonIntLatency(t *testing.T) {
	_, err := parseLatencyHistoryEntries([]any{[]any{int64(1), "oops"}})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "latency")
}

// parseLatencyLatestEntries: typed parsing happy path.
func TestParseLatencyLatestEntries_Typed(t *testing.T) {
	input := []any{
		[]any{"command", int64(1738651470), int64(254), int64(1005)},
		[]any{"fork", int64(1738651500), int64(7), int64(42)},
	}

	entries, err := parseLatencyLatestEntries(input)
	require.NoError(t, err)
	require.Len(t, entries, 2)
	assert.Equal(t, models.LatencyLatestEntry{
		EventName: "command",
		Timestamp: 1738651470,
		LatestMs:  254,
		MaxMs:     1005,
	}, entries[0])
	assert.Equal(t, models.LatencyLatestEntry{
		EventName: "fork",
		Timestamp: 1738651500,
		LatestMs:  7,
		MaxMs:     42,
	}, entries[1])
}

// parseLatencyLatestEntries should ignore the optional sum/count tail added in 8.1+.
func TestParseLatencyLatestEntries_IgnoresTrailingFields(t *testing.T) {
	input := []any{
		[]any{"command", int64(1738651470), int64(254), int64(1005), int64(1259), int64(2)},
	}

	entries, err := parseLatencyLatestEntries(input)
	require.NoError(t, err)
	require.Len(t, entries, 1)
	assert.Equal(t, models.LatencyLatestEntry{
		EventName: "command",
		Timestamp: 1738651470,
		LatestMs:  254,
		MaxMs:     1005,
	}, entries[0])
}

func TestParseLatencyLatestEntries_Empty(t *testing.T) {
	entries, err := parseLatencyLatestEntries([]any{})
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestParseLatencyLatestEntries_Nil(t *testing.T) {
	entries, err := parseLatencyLatestEntries(nil)
	require.NoError(t, err)
	assert.Empty(t, entries)
}

func TestParseLatencyLatestEntries_RejectsShortEntry(t *testing.T) {
	_, err := parseLatencyLatestEntries([]any{[]any{"command", int64(1), int64(2)}})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "expected at least 4")
}

func TestParseLatencyLatestEntries_RejectsWrongEventNameType(t *testing.T) {
	_, err := parseLatencyLatestEntries([]any{[]any{int64(1), int64(2), int64(3), int64(4)}})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "event name")
}

// Each numeric field must be int64; check that bad types are rejected with a clear message.
func TestParseLatencyLatestEntries_RejectsWrongNumericType(t *testing.T) {
	cases := []struct {
		name    string
		entry   []any
		wantSub string
	}{
		{"timestamp", []any{"command", "x", int64(2), int64(3)}, "timestamp"},
		{"latest", []any{"command", int64(1), "x", int64(3)}, "latest_ms"},
		{"max", []any{"command", int64(1), int64(2), "x"}, "max_ms"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := parseLatencyLatestEntries([]any{tc.entry})
			require.Error(t, err)
			assert.True(t,
				strings.Contains(err.Error(), tc.wantSub),
				"expected error to contain %q, got: %v", tc.wantSub, err)
		})
	}
}

// LatencyHistoryEntry / LatencyLatestEntry zero-values are stable to guard against accidental
// renames (the structs are part of the public API surface).
func TestLatencyResponseTypes_ZeroValues(t *testing.T) {
	var hist models.LatencyHistoryEntry
	assert.Equal(t, int64(0), hist.Timestamp)
	assert.Equal(t, int64(0), hist.LatencyMs)

	var latest models.LatencyLatestEntry
	assert.Equal(t, "", latest.EventName)
	assert.Equal(t, int64(0), latest.Timestamp)
	assert.Equal(t, int64(0), latest.LatestMs)
	assert.Equal(t, int64(0), latest.MaxMs)
}

// mergeLatencyHistoryEntries: empty / single-node / multi-node sorts stably by timestamp.
func TestMergeLatencyHistoryEntries_EmptyMap(t *testing.T) {
	got, err := mergeLatencyHistoryEntries(nil)
	require.NoError(t, err)
	assert.Empty(t, got)

	got, err = mergeLatencyHistoryEntries(map[string]any{})
	require.NoError(t, err)
	assert.Empty(t, got)
}

func TestMergeLatencyHistoryEntries_MultiNodeSortedByTimestamp(t *testing.T) {
	perNode := map[string]any{
		"127.0.0.1:7000": []any{
			[]any{int64(100), int64(5)},
			[]any{int64(300), int64(7)},
		},
		"127.0.0.1:7001": []any{
			[]any{int64(200), int64(6)},
		},
	}

	got, err := mergeLatencyHistoryEntries(perNode)
	require.NoError(t, err)
	require.Len(t, got, 3)
	assert.Equal(t, int64(100), got[0].Timestamp)
	assert.Equal(t, int64(200), got[1].Timestamp)
	assert.Equal(t, int64(300), got[2].Timestamp)
}

// Equal-timestamp samples must remain in sorted-address insertion order.
func TestMergeLatencyHistoryEntries_StableOnTimestampTies(t *testing.T) {
	perNode := map[string]any{
		// Sorted-address order: 7000, 7001 → entries with timestamp 100 keep that order.
		"127.0.0.1:7000": []any{[]any{int64(100), int64(5)}},
		"127.0.0.1:7001": []any{[]any{int64(100), int64(9)}},
	}

	got, err := mergeLatencyHistoryEntries(perNode)
	require.NoError(t, err)
	require.Len(t, got, 2)
	assert.Equal(t, int64(5), got[0].LatencyMs, "lexicographically smaller address should appear first")
	assert.Equal(t, int64(9), got[1].LatencyMs)
}

func TestMergeLatencyHistoryEntries_PropagatesParseError(t *testing.T) {
	perNode := map[string]any{
		"127.0.0.1:7000": []any{[]any{int64(100), "oops"}},
	}
	_, err := mergeLatencyHistoryEntries(perNode)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "latency")
}

// mergeLatencyLatestEntries: most-recent timestamp wins; MaxMs is cross-node max regardless.
func TestMergeLatencyLatestEntries_MostRecentTimestampWins(t *testing.T) {
	perNode := map[string]any{
		"127.0.0.1:7000": []any{
			[]any{"command", int64(1700000000), int64(100), int64(500)},
			[]any{"fork", int64(1700000050), int64(7), int64(7)},
		},
		"127.0.0.1:7001": []any{
			[]any{"command", int64(1700000060), int64(200), int64(900)},
		},
	}

	got, err := mergeLatencyLatestEntries(perNode)
	require.NoError(t, err)
	require.Len(t, got, 2)

	byEvent := map[string]models.LatencyLatestEntry{got[0].EventName: got[0], got[1].EventName: got[1]}
	cmd := byEvent["command"]
	assert.Equal(t, int64(1700000060), cmd.Timestamp, "most recent timestamp wins")
	assert.Equal(t, int64(200), cmd.LatestMs)
	assert.Equal(t, int64(900), cmd.MaxMs, "MaxMs is cross-node max")

	fork := byEvent["fork"]
	assert.Equal(t, int64(1700000050), fork.Timestamp)
}

// MaxMs aggregation must keep the cross-node max even when the winning node has a lower MaxMs.
func TestMergeLatencyLatestEntries_MaxMsIsCrossNodeMax(t *testing.T) {
	perNode := map[string]any{
		// Older sample but holds the all-time-max from a long-gone spike.
		"127.0.0.1:7000": []any{[]any{"command", int64(1700000000), int64(50), int64(2000)}},
		// Newer sample; lower MaxMs.
		"127.0.0.1:7001": []any{[]any{"command", int64(1700000060), int64(10), int64(60)}},
	}

	got, err := mergeLatencyLatestEntries(perNode)
	require.NoError(t, err)
	require.Len(t, got, 1)
	assert.Equal(t, int64(1700000060), got[0].Timestamp)
	assert.Equal(t, int64(10), got[0].LatestMs)
	assert.Equal(t, int64(2000), got[0].MaxMs, "MaxMs must be max across all nodes")
}

// On equal Timestamp ties the larger LatestMs wins.
func TestMergeLatencyLatestEntries_TimestampTieBreaksOnLatestMs(t *testing.T) {
	perNode := map[string]any{
		"127.0.0.1:7000": []any{[]any{"command", int64(1700000000), int64(50), int64(50)}},
		"127.0.0.1:7001": []any{[]any{"command", int64(1700000000), int64(75), int64(75)}},
	}

	got, err := mergeLatencyLatestEntries(perNode)
	require.NoError(t, err)
	require.Len(t, got, 1)
	assert.Equal(t, int64(75), got[0].LatestMs)
	assert.Equal(t, int64(75), got[0].MaxMs)
}

func TestMergeLatencyLatestEntries_EmptyMap(t *testing.T) {
	got, err := mergeLatencyLatestEntries(nil)
	require.NoError(t, err)
	assert.Empty(t, got)
}

// Result is sorted by event name for deterministic ordering across runs.
func TestMergeLatencyLatestEntries_SortedByEventName(t *testing.T) {
	perNode := map[string]any{
		"127.0.0.1:7000": []any{
			[]any{"zeta", int64(1), int64(1), int64(1)},
			[]any{"alpha", int64(1), int64(1), int64(1)},
			[]any{"mu", int64(1), int64(1), int64(1)},
		},
	}

	got, err := mergeLatencyLatestEntries(perNode)
	require.NoError(t, err)
	require.Len(t, got, 3)
	assert.Equal(t, "alpha", got[0].EventName)
	assert.Equal(t, "mu", got[1].EventName)
	assert.Equal(t, "zeta", got[2].EventName)
}
