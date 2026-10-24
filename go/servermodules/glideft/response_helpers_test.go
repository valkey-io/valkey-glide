// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Unit tests for the wave-2 reply-shape parsing in parseFtSearchResponse/
// parseCursorWrappedAggregateResponse. These exercise glide-core's already-reshaped reply
// shapes directly (constructed here as the Go generic any/map/slice values CustomCommand would
// hand back) -- they do not re-split or re-derive anything from a raw/un-reshaped reply.

func TestParseFtSearchResponse_WithScoresOnly(t *testing.T) {
	reply := []any{
		int64(2),
		map[string]any{
			"key1": []any{"0.5", map[string]any{"field1": "value1"}},
			"key2": []any{"0.25", map[string]any{"field2": "value2"}},
		},
	}
	result, err := parseFtSearchResponse(reply, nil, false, &options.FtSearchOptions{WithScores: true})
	require.NoError(t, err)
	assert.EqualValues(t, 2, result.TotalResults)
	require.Len(t, result.Documents, 2)

	byKey := map[string]float64{}
	for _, doc := range result.Documents {
		require.NotNil(t, doc.Score)
		byKey[doc.Key] = *doc.Score
		assert.Empty(t, doc.SortKey)
	}
	assert.Equal(t, 0.5, byKey["key1"])
	assert.Equal(t, 0.25, byKey["key2"])
}

func TestParseFtSearchResponse_WithScoresAndNoContent(t *testing.T) {
	// WITHSCORES + NOCONTENT: pairs of (key, score), no field arrays.
	reply := []any{
		int64(2),
		map[string]any{
			"key1": []any{"0.5", map[string]any{}},
			"key2": []any{"0.25", map[string]any{}},
		},
	}
	result, err := parseFtSearchResponse(
		reply, nil, false, &options.FtSearchOptions{WithScores: true, NoContent: true},
	)
	require.NoError(t, err)
	for _, doc := range result.Documents {
		require.NotNil(t, doc.Score)
		assert.Empty(t, doc.Fields)
	}
}

func TestParseFtSearchResponse_WithScoresAndWithSortKeysCombinedShape(t *testing.T) {
	// [score, sortKey, fieldMap] triple when both flags are set and content is present.
	reply := []any{
		int64(1),
		map[string]any{
			"key1": []any{"0.5", "#10", map[string]any{"title": "hello"}},
		},
	}
	result, err := parseFtSearchResponse(
		reply, nil, true, &options.FtSearchOptions{WithScores: true, SortBy: "price", WithSortKeys: true},
	)
	require.NoError(t, err)
	require.Len(t, result.Documents, 1)
	doc := result.Documents[0]
	require.NotNil(t, doc.Score)
	assert.Equal(t, 0.5, *doc.Score)
	assert.Equal(t, "#10", doc.SortKey)
	assert.Equal(t, "hello", doc.Fields["title"])
}

func TestParseFtSearchResponse_WithScoresAndWithSortKeysNoContentDropsSortKey(t *testing.T) {
	// Confirmed live (ported from Python's test_ft_search_with_scores_and_withsortkeys_combined_
	// shape docstring and glide-core's value_conversion.rs): the server drops the sort key
	// entirely in this specific combination, reverting to the plain WITHSCORES-only [score,
	// fieldMap] shape -- not [score, nil, fieldMap].
	reply := []any{
		int64(1),
		map[string]any{
			"key1": []any{"0.5", map[string]any{}},
		},
	}
	result, err := parseFtSearchResponse(
		reply, nil, true,
		&options.FtSearchOptions{WithScores: true, SortBy: "price", WithSortKeys: true, NoContent: true},
	)
	require.NoError(t, err)
	require.Len(t, result.Documents, 1)
	doc := result.Documents[0]
	require.NotNil(t, doc.Score)
	assert.Equal(t, 0.5, *doc.Score)
	assert.Empty(t, doc.SortKey)
}

func TestParseFtSearchResponse_WithSortKeysOnlyUnaffectedByScoreParsing(t *testing.T) {
	reply := []any{
		int64(1),
		map[string]any{
			"key1": []any{"#10", map[string]any{"title": "hello"}},
		},
	}
	result, err := parseFtSearchResponse(
		reply, nil, true, &options.FtSearchOptions{SortBy: "price", WithSortKeys: true},
	)
	require.NoError(t, err)
	require.Len(t, result.Documents, 1)
	doc := result.Documents[0]
	assert.Nil(t, doc.Score)
	assert.Equal(t, "#10", doc.SortKey)
}

func TestParseFtSearchResponse_NeitherFlagPlainFieldMap(t *testing.T) {
	reply := []any{
		int64(1),
		map[string]any{"key1": map[string]any{"title": "hello"}},
	}
	result, err := parseFtSearchResponse(reply, nil, false, &options.FtSearchOptions{})
	require.NoError(t, err)
	require.Len(t, result.Documents, 1)
	doc := result.Documents[0]
	assert.Nil(t, doc.Score)
	assert.Empty(t, doc.SortKey)
	assert.Equal(t, "hello", doc.Fields["title"])
}

func TestParseFtSearchResponse_UnparseableScoreErrors(t *testing.T) {
	reply := []any{
		int64(1),
		map[string]any{"key1": []any{"not-a-number", map[string]any{}}},
	}
	_, err := parseFtSearchResponse(reply, nil, false, &options.FtSearchOptions{WithScores: true})
	assert.Error(t, err)
}

func TestParseCursorWrappedAggregateResponse_RowsAndCursorId(t *testing.T) {
	reply := []any{
		[]any{map[string]any{"n": "1"}, map[string]any{"n": "2"}},
		int64(12345),
	}
	rows, cursorID, err := parseCursorWrappedAggregateResponse(reply, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(12345), cursorID)
	require.Len(t, rows, 2)
	assert.Equal(t, "1", rows[0]["n"])
	assert.Equal(t, "2", rows[1]["n"])
}

func TestParseCursorWrappedAggregateResponse_ExhaustedCursorIsZero(t *testing.T) {
	reply := []any{[]any{map[string]any{"n": "1"}}, int64(0)}
	_, cursorID, err := parseCursorWrappedAggregateResponse(reply, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(0), cursorID)
}

func TestParseCursorWrappedAggregateResponse_WrongShapeErrors(t *testing.T) {
	_, _, err := parseCursorWrappedAggregateResponse([]any{int64(1)}, nil)
	assert.Error(t, err)
}

func TestParseCursorWrappedAggregateResponse_PropagatesUpstreamError(t *testing.T) {
	upstreamErr := assert.AnError
	_, _, err := parseCursorWrappedAggregateResponse(nil, upstreamErr)
	assert.ErrorIs(t, err, upstreamErr)
}
