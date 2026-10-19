// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// Argv-serialization tests for the valkey-search 1.3 stable surface added to the FT.* options:
// INKEYS, FLOAT16/BFLOAT16 vector types, FT.CREATE FILTER/SCORE_FIELD/NOHL, per-field UNF,
// Count's LIMIT 0 0 serialization, and the typed reducer factories. Mirrors Node's
// GlideFtOptions.test.ts and Python's test_options.py.

func TestFtSearchOptionsToArgs_InKeysAbsent(t *testing.T) {
	opts := &FtSearchOptions{}
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.NotContains(t, args, "INKEYS")
}

func TestFtSearchOptionsToArgs_InKeysEmptySlice(t *testing.T) {
	opts := &FtSearchOptions{InKeys: []string{}}
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	idx := indexOf(args, "INKEYS")
	assert.GreaterOrEqual(t, idx, 0)
	assert.Equal(t, "0", args[idx+1])
}

func TestFtSearchOptionsToArgs_InKeysWithList(t *testing.T) {
	opts := &FtSearchOptions{InKeys: []string{"key1", "key2"}}
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	idx := indexOf(args, "INKEYS")
	assert.Equal(t, []string{"INKEYS", "2", "key1", "key2"}, args[idx:idx+4])
}

func TestFtSearchOptionsToArgs_CountMapsToLimitZeroZero(t *testing.T) {
	opts := &FtSearchOptions{Count: true}
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.NotContains(t, args, "COUNT")
	idx := indexOf(args, "LIMIT")
	assert.Equal(t, []string{"LIMIT", "0", "0"}, args[idx:idx+3])
}

func TestFtSearchOptionsToArgs_CountAndLimitAreMutuallyExclusive(t *testing.T) {
	opts := &FtSearchOptions{Count: true, Limit: &FtSearchLimit{Offset: 0, Count: 10}}
	_, err := opts.ToArgs()
	assert.ErrorContains(t, err, "mutually exclusive")
}

func TestFtSearchOptionsToArgs_LimitStillWorks(t *testing.T) {
	opts := &FtSearchOptions{Limit: &FtSearchLimit{Offset: 5, Count: 10}}
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	idx := indexOf(args, "LIMIT")
	assert.Equal(t, []string{"LIMIT", "5", "10"}, args[idx:idx+3])
}

func TestTextFieldToArgs_SortableThenUnf(t *testing.T) {
	f := NewTextField("title").SetSortable(true).SetUnf(true)
	args, err := f.ToArgs()
	assert.NoError(t, err)
	idx := indexOf(args, "SORTABLE")
	assert.GreaterOrEqual(t, idx, 0)
	assert.Equal(t, "UNF", args[idx+1])
}

func TestTextFieldToArgs_UnfWithoutSortableErrors(t *testing.T) {
	f := NewTextField("title").SetUnf(true)
	_, err := f.ToArgs()
	assert.ErrorContains(t, err, "Unf requires Sortable")
}

func TestTagFieldToArgs_SortableThenUnf(t *testing.T) {
	f := NewTagField("tags").SetSortable(true).SetUnf(true)
	args, err := f.ToArgs()
	assert.NoError(t, err)
	idx := indexOf(args, "SORTABLE")
	assert.GreaterOrEqual(t, idx, 0)
	assert.Equal(t, "UNF", args[idx+1])
}

func TestTagFieldToArgs_UnfWithoutSortableErrors(t *testing.T) {
	f := NewTagField("tags").SetUnf(true)
	_, err := f.ToArgs()
	assert.ErrorContains(t, err, "Unf requires Sortable")
}

func TestNumericFieldToArgs_SortableThenUnf(t *testing.T) {
	f := NewNumericField("price").SetSortable(true).SetUnf(true)
	args, err := f.ToArgs()
	assert.NoError(t, err)
	idx := indexOf(args, "SORTABLE")
	assert.GreaterOrEqual(t, idx, 0)
	assert.Equal(t, "UNF", args[idx+1])
}

func TestNumericFieldToArgs_UnfWithoutSortableErrors(t *testing.T) {
	f := NewNumericField("price").SetUnf(true)
	_, err := f.ToArgs()
	assert.ErrorContains(t, err, "Unf requires Sortable")
}

func TestVectorFieldFlatToArgs_Float16Type(t *testing.T) {
	f := NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2).
		SetType(constants.VectorDataTypeFloat16)
	args, err := f.ToArgs()
	assert.NoError(t, err)
	assert.Contains(t, args, "FLOAT16")
	assert.NotContains(t, args, "FLOAT32")
}

func TestVectorFieldFlatToArgs_Bfloat16Type(t *testing.T) {
	f := NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2).
		SetType(constants.VectorDataTypeBfloat16)
	args, err := f.ToArgs()
	assert.NoError(t, err)
	assert.Contains(t, args, "BFLOAT16")
}

func TestVectorFieldFlatToArgs_DefaultsToFloat32(t *testing.T) {
	f := NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2)
	args, err := f.ToArgs()
	assert.NoError(t, err)
	assert.Contains(t, args, "FLOAT32")
}

func TestFtCreateOptionsToArgs_FilterScoreFieldNoHighlight(t *testing.T) {
	opts := &FtCreateOptions{
		DataType:    constants.IndexDataTypeHash,
		Filter:      "@price > 0",
		ScoreField:  "boost",
		NoHighlight: true,
	}
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	fIdx := indexOf(args, "FILTER")
	assert.Equal(t, "@price > 0", args[fIdx+1])
	sIdx := indexOf(args, "SCORE_FIELD")
	assert.Equal(t, "boost", args[sIdx+1])
	assert.Contains(t, args, "NOHL")
}

func TestFtCreateOptionsToArgs_NoHighlightOmittedWhenUnset(t *testing.T) {
	opts := &FtCreateOptions{DataType: constants.IndexDataTypeHash}
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.NotContains(t, args, "NOHL")
}

func TestFtCreateOptionsToArgs_EmptyFilterOmitsFilter(t *testing.T) {
	// Go's convention for every other optional string field here (Language, Punctuation) is
	// "empty string means unset" -- Filter follows the same convention rather than raising, since
	// Go has no null/undefined distinction for strings the way Python/Node/Java do.
	opts := &FtCreateOptions{DataType: constants.IndexDataTypeHash, Filter: ""}
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.NotContains(t, args, "FILTER")
}

func TestNewToListReducer(t *testing.T) {
	r := NewToListReducer("@color", "colors")
	assert.Equal(t, FtAggregateReducer{Function: "TOLIST", Args: []string{"@color"}, Name: "colors"}, r)
}

func TestNewFirstValueReducer_WithoutBy(t *testing.T) {
	r, err := NewFirstValueReducer("@title", nil, "")
	assert.NoError(t, err)
	assert.Equal(t, FtAggregateReducer{Function: "FIRST_VALUE", Args: []string{"@title"}}, r)
}

func TestNewFirstValueReducer_WithByAndOrder(t *testing.T) {
	r, err := NewFirstValueReducer(
		"@title",
		&FtAggregateFirstValueOptions{By: "@price", Order: constants.FtAggregateOrderByDesc},
		"most_expensive_title",
	)
	assert.NoError(t, err)
	assert.Equal(t, FtAggregateReducer{
		Function: "FIRST_VALUE",
		Args:     []string{"@title", "BY", "@price", "DESC"},
		Name:     "most_expensive_title",
	}, r)
}

func TestNewFirstValueReducer_OrderWithoutByErrors(t *testing.T) {
	_, err := NewFirstValueReducer(
		"@title", &FtAggregateFirstValueOptions{Order: constants.FtAggregateOrderByDesc}, "",
	)
	assert.ErrorContains(t, err, "Order requires By")
}

func TestNewRandomSampleReducer(t *testing.T) {
	r := NewRandomSampleReducer("@score", 5, "score_sample")
	assert.Equal(t, FtAggregateReducer{
		Function: "RANDOM_SAMPLE",
		Args:     []string{"@score", "5"},
		Name:     "score_sample",
	}, r)
}

func indexOf(s []string, v string) int {
	for i, x := range s {
		if x == v {
			return i
		}
	}
	return -1
}
