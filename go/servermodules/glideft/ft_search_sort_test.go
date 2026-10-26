// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// TestSortDocuments_SortByFieldNotScoreWhenWithScoresCombined is a regression test for the
// port rule: sortDocuments must keep sorting by the SortBy field (or SortKey, when
// WithSortKeys is set), never by the document's Score, once SortBy and WithScores combine. A
// naive implementation that fell back to sorting by Score once it became available on the
// document would silently produce the wrong order here -- the fixture's score values are
// deliberately inverted relative to the "price" field's values, so sorting by score instead of
// price would reverse the expected order.
func TestSortDocuments_SortByFieldNotScoreWhenWithScoresCombined(t *testing.T) {
	score1, score2, score3 := 0.9, 0.5, 0.1 // descending, the OPPOSITE of price order below
	docs := []models.FtSearchDocument{
		{Key: "cheap", Fields: map[string]any{"price": "1"}, Score: &score1},
		{Key: "mid", Fields: map[string]any{"price": "5"}, Score: &score2},
		{Key: "expensive", Fields: map[string]any{"price": "10"}, Score: &score3},
	}
	// Shuffle input order so SliceStable's stability can't accidentally produce the right answer.
	docs[0], docs[2] = docs[2], docs[0]

	opts := &options.FtSearchOptions{WithScores: true, SortBy: "price", SortByOrder: constants.FtSearchSortOrderAsc}
	sortDocuments(docs, opts, false)

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(t, []string{"cheap", "mid", "expensive"}, keysInOrder, "must sort by price (SortBy), not by Score")
}

// TestSortDocuments_SortByUsesSortKeyNotScoreWithWithSortKeys mirrors the above with
// WithSortKeys set instead of relying on the Fields map. L3 fix: the fixture uses three
// documents whose Score order is NOT a monotonic (ascending or descending) match for their
// SortKey order, unlike the previous two-element fixture where ascending-by-SortKey and
// ascending-by-Score happened to produce the same answer. A sort-by-score bug, in either
// direction, now produces a different order than the correct SortKey-based sort.
func TestSortDocuments_SortByUsesSortKeyNotScoreWithWithSortKeys(t *testing.T) {
	scoreA, scoreB, scoreC := 0.5, 0.9, 0.1 // not monotonic with SortKey "3","2","1" in either direction
	docs := []models.FtSearchDocument{
		{Key: "a", SortKey: "3", Score: &scoreA},
		{Key: "b", SortKey: "2", Score: &scoreB},
		{Key: "c", SortKey: "1", Score: &scoreC},
	}

	opts := &options.FtSearchOptions{WithScores: true, SortBy: "price", WithSortKeys: true}
	sortDocuments(docs, opts, true)

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(t, []string{"c", "b", "a"}, keysInOrder, "must sort by SortKey ascending, not by Score")
}

// TestSortDocuments_WithScoresAloneSortsByScoreDescending covers M1: when WithScores is set and
// SortBy is empty, Documents must come back sorted by Score descending -- restoring the
// server's own BM25 relevance order, which Go's map-based intermediate representation
// otherwise loses (unlike Python's dict, which preserves insertion order). The fixture's input
// slice order deliberately does not match either ascending or descending score order, so this
// test fails if the sort is removed (arbitrary map-iteration order would not reliably match
// descending score order) and fails if the sort is ascending instead of descending.
func TestSortDocuments_WithScoresAloneSortsByScoreDescending(t *testing.T) {
	scoreLow, scoreHigh, scoreMid := 0.1, 0.9, 0.5
	docs := []models.FtSearchDocument{
		{Key: "low", Fields: map[string]any{}, Score: &scoreLow},
		{Key: "high", Fields: map[string]any{}, Score: &scoreHigh},
		{Key: "mid", Fields: map[string]any{}, Score: &scoreMid},
	}

	opts := &options.FtSearchOptions{WithScores: true}
	sortDocuments(docs, opts, false)

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(
		t,
		[]string{"high", "mid", "low"},
		keysInOrder,
		"WithScores alone (no SortBy) must sort by Score descending, matching the server's relevance rank",
	)
}
