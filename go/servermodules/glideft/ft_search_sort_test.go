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
// WithSortKeys set instead of relying on the Fields map.
func TestSortDocuments_SortByUsesSortKeyNotScoreWithWithSortKeys(t *testing.T) {
	score1, score2 := 0.9, 0.1 // descending, opposite of SortKey order below
	docs := []models.FtSearchDocument{
		{Key: "a", SortKey: "2", Score: &score1},
		{Key: "b", SortKey: "1", Score: &score2},
	}

	opts := &options.FtSearchOptions{WithScores: true, SortBy: "price", WithSortKeys: true}
	sortDocuments(docs, opts, true)

	assert.Equal(t, "b", docs[0].Key, "must sort by SortKey, not by Score")
	assert.Equal(t, "a", docs[1].Key)
}
