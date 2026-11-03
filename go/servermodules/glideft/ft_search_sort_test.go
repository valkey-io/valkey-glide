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
	sortDocuments(docs, opts, false, "@title:hello")

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(t, []string{"cheap", "mid", "expensive"}, keysInOrder, "must sort by price (SortBy), not by Score")
}

// TestSortDocuments_SortByUsesSortKeyNotScoreWithWithSortKeys mirrors the above with
// WithSortKeys set instead of relying on the Fields map. The fixture uses three
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
	sortDocuments(docs, opts, true, "@title:hello")

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(t, []string{"c", "b", "a"}, keysInOrder, "must sort by SortKey ascending, not by Score")
}

// TestSortDocuments_WithScoresAloneSortsByScoreDescending covers the case where WithScores is
// set and SortBy is empty: Documents must come back sorted by Score descending -- restoring the
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
	sortDocuments(docs, opts, false, "@title:hello")

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

// TestSortDocuments_WithScoresTiesBrokenByKeyDeterministically covers two documents with an
// identical Score and no SortBy: the order between them must be decided by Key, not left to Go's
// randomized map-iteration order (which SliceStable alone would otherwise preserve verbatim).
// Runs the sort many times over a shuffled input to catch a tiebreak that is missing or only
// sometimes applied.
func TestSortDocuments_WithScoresTiesBrokenByKeyDeterministically(t *testing.T) {
	tiedScore := 0.5
	opts := &options.FtSearchOptions{WithScores: true}

	for i := 0; i < 20; i++ {
		scoreA, scoreB := tiedScore, tiedScore
		docs := []models.FtSearchDocument{
			{Key: "zzz", Fields: map[string]any{}, Score: &scoreB},
			{Key: "aaa", Fields: map[string]any{}, Score: &scoreA},
		}
		sortDocuments(docs, opts, false, "@title:hello")
		assert.Equal(t, []string{"aaa", "zzz"}, []string{docs[0].Key, docs[1].Key},
			"tied scores must break deterministically by key ascending")
	}
}

// TestSortDocuments_PureVectorKnnQuerySortsScoreAscending covers a pure-vector KNN query (no
// text/tag/numeric pre-filter): Score there is a KNN distance, where lower means closer, and the
// server's own reply order is ascending by distance -- not descending like BM25 relevance. The
// fixture's input slice order does not match ascending order, so this test fails if the sort
// stays descending (the pre-existing, BM25-only behavior) or is removed.
func TestSortDocuments_PureVectorKnnQuerySortsScoreAscending(t *testing.T) {
	distFar, distNear, distMid := 0.9, 0.1, 0.5
	docs := []models.FtSearchDocument{
		{Key: "far", Fields: map[string]any{}, Score: &distFar},
		{Key: "near", Fields: map[string]any{}, Score: &distNear},
		{Key: "mid", Fields: map[string]any{}, Score: &distMid},
	}

	opts := &options.FtSearchOptions{WithScores: true}
	sortDocuments(docs, opts, false, "*=>[KNN 10 @vec $q]")

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(
		t,
		[]string{"near", "mid", "far"},
		keysInOrder,
		"a pure-vector KNN query (no pre-filter) must sort by Score ascending (closest distance first)",
	)
}

// TestSortDocuments_HybridTextKnnQuerySortsScoreDescending covers a hybrid `text=>[KNN ...]`
// query (a text pre-filter alongside the vector clause): valkey-search's own response_generator
// re-ranks this case to relevance descending, same as a pure-text query, rather than leaving it
// in the KNN-distance ascending order a pure-vector query keeps. isPureVectorKnnQuery must say
// false here so sortDocuments does not mistakenly sort it ascending.
func TestSortDocuments_HybridTextKnnQuerySortsScoreDescending(t *testing.T) {
	scoreLow, scoreHigh, scoreMid := 0.1, 0.9, 0.5
	docs := []models.FtSearchDocument{
		{Key: "low", Fields: map[string]any{}, Score: &scoreLow},
		{Key: "high", Fields: map[string]any{}, Score: &scoreHigh},
		{Key: "mid", Fields: map[string]any{}, Score: &scoreMid},
	}

	opts := &options.FtSearchOptions{WithScores: true}
	sortDocuments(docs, opts, false, "@title:hello=>[KNN 10 @vec $q]")

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(
		t,
		[]string{"high", "mid", "low"},
		keysInOrder,
		"a hybrid text+KNN query must sort by Score descending, matching the server's re-ranked relevance order",
	)
}

func TestIsPureVectorKnnQuery(t *testing.T) {
	cases := []struct {
		name  string
		query string
		want  bool
	}{
		{"no vector delimiter", "@title:hello", false},
		{"bare match-all pre-filter", "*=>[KNN 10 @vec $q]", true},
		{"empty pre-filter", "=>[KNN 10 @vec $q]", true},
		{"whitespace-only pre-filter", "  *  =>[KNN 10 @vec $q]", true},
		{"text pre-filter", "@title:hello=>[KNN 10 @vec $q]", false},
		{"tag pre-filter", "@tag:{a}=>[KNN 10 @vec $q]", false},
		{"whitespace then bracket still matches", "*=>   [KNN 10 @vec $q]", true},
		{"literal => in free text not followed by [ is not the delimiter", "a=>b hello", false},
		{
			"literal => in free text before the real KNN delimiter is skipped, not matched first",
			"@title:\"a=>b\"=>[KNN 10 @vec $q]",
			false,
		},
		{"QMA-style \"=> {\" block is not the vector delimiter", "*=> {some: qma}", false},
		{
			// Today's server (confirmed as of 1.3.0-rc2, search.cc's FindVectorDelimiter) has
			// no brace/quote/escape awareness: a literal "=>[" sequence inside a tag value's
			// braces is still read as the real vector delimiter server-side, truncating the
			// pre-filter early. This function matches that current server behaviour exactly --
			// see its docstring for what changes here if the server's own scan gains brace/
			// quote/escape awareness later. The truncated pre-filter up to that first "=>[" is
			// "@tag:{x", which is neither empty nor "*", so this correctly reads as NOT a pure
			// vector query -- the quirk doesn't actually produce a wrong boolean answer for this
			// specific input shape, but a future server-side fix would change where the
			// delimiter is found, which is exactly what this test pins down.
			"a literal =>[ inside tag braces is read as the real delimiter, matching today's server",
			"@tag:{x=>[y}=>[KNN 10 @vec $q]",
			false,
		},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			assert.Equal(t, c.want, isPureVectorKnnQuery(c.query))
		})
	}
}
