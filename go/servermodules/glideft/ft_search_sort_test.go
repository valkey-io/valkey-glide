// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func TestSortDocuments_SortByFieldNotScoreWhenWithScoresCombined(t *testing.T) {
	score1, score2, score3 := 0.9, 0.5, 0.1
	docs := []models.FtSearchDocument{
		{Key: "cheap", Fields: map[string]any{"price": "1"}, Score: &score1},
		{Key: "mid", Fields: map[string]any{"price": "5"}, Score: &score2},
		{Key: "expensive", Fields: map[string]any{"price": "10"}, Score: &score3},
	}
	docs[0], docs[2] = docs[2], docs[0]

	opts := &options.FtSearchOptions{WithScores: true, SortBy: "price", SortByOrder: constants.FtSearchSortOrderAsc}
	sortDocuments(docs, opts, false, "@title:hello")

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(t, []string{"cheap", "mid", "expensive"}, keysInOrder, "must sort by price (SortBy), not by Score")
}

func TestSortDocuments_SortByUsesSortKeyNotScoreWithWithSortKeys(t *testing.T) {
	scoreA, scoreB, scoreC := 0.5, 0.9, 0.1
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

func TestSortDocuments_PureVectorKnnQuerySortsByDistanceFieldAscending(t *testing.T) {
	zeroScore := 0.0
	docs := []models.FtSearchDocument{
		{Key: "far", Fields: map[string]any{"__vec_score": "0.9"}, Score: &zeroScore},
		{Key: "near", Fields: map[string]any{"__vec_score": "0.1"}, Score: &zeroScore},
		{Key: "mid", Fields: map[string]any{"__vec_score": "0.5"}, Score: &zeroScore},
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
		"a pure-vector KNN query (no pre-filter) must sort by the real distance field ascending (closest first)",
	)
}

func TestSortDocuments_PureVectorKnnQueryWithKnnAsAliasSortsByAliasedDistanceField(t *testing.T) {
	zeroScore := 0.0
	docs := []models.FtSearchDocument{
		{Key: "far", Fields: map[string]any{"dist": "0.9"}, Score: &zeroScore},
		{Key: "near", Fields: map[string]any{"dist": "0.1"}, Score: &zeroScore},
		{Key: "mid", Fields: map[string]any{"dist": "0.5"}, Score: &zeroScore},
	}

	opts := &options.FtSearchOptions{WithScores: true}
	sortDocuments(docs, opts, false, "*=>[KNN 10 @vec $q AS dist]")

	keysInOrder := make([]string, len(docs))
	for i, d := range docs {
		keysInOrder[i] = d.Key
	}
	assert.Equal(
		t,
		[]string{"near", "mid", "far"},
		keysInOrder,
		"a pure-vector KNN query with its own AS alias must sort by that aliased distance field ascending",
	)
}

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
