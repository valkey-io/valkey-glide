// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"slices"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func TestBuildHybridArgs_CombinePlacedBetweenVsimArmAndTail(t *testing.T) {
	searchArm := options.FtHybridSearchArm{Query: "hello"}
	vsimArm := options.FtHybridVsimArm{Field: "vec", VectorParam: "$q"}
	opts := &options.FtHybridOptions{
		Combine: &options.FtHybridRrf{},
		Params:  []options.FtAggregateParam{{Key: "q", Value: "rawbytesplaceholder"}},
	}

	args, err := buildHybridArgs("idx", searchArm, vsimArm, opts)
	require.NoError(t, err)

	assert.Equal(t, []string{
		"FT.HYBRID", "idx",
		"SEARCH", "hello",
		"VSIM", "@vec", "$q",
		"COMBINE", "RRF", "0",
		"PARAMS", "2", "q", "rawbytesplaceholder",
	}, args)
}

func TestBuildHybridArgs_GeneratedParamNameHasNoDashes(t *testing.T) {
	searchArm := options.FtHybridSearchArm{Query: "hello"}
	vsimArm := options.FtHybridVsimArm{Field: "vec", Vector: []byte{0x01, 0x02, 0x03, 0x04}}

	args, err := buildHybridArgs("idx", searchArm, vsimArm, nil)
	require.NoError(t, err)

	vsimIdx := slices.Index(args, "VSIM")
	require.NotEqual(t, -1, vsimIdx)
	require.Greater(t, len(args), vsimIdx+2)
	paramRef := args[vsimIdx+2]
	require.True(t, strings.HasPrefix(paramRef, "$"), "generated param reference must start with $, got %q", paramRef)
	assert.NotContains(t, paramRef[1:], "-", "generated PARAMS name must have dashes stripped (server rejects a dash)")
}

func TestBuildHybridArgs_GeneratedKeyPassedToToArgsWithoutDollarPrefix(t *testing.T) {
	searchArm := options.FtHybridSearchArm{Query: "hello"}
	vsimArm := options.FtHybridVsimArm{Field: "vec", Vector: []byte("rawvec")}

	args, err := buildHybridArgs("idx", searchArm, vsimArm, nil)
	require.NoError(t, err)

	vsimIdx := slices.Index(args, "VSIM")
	require.NotEqual(t, -1, vsimIdx)
	vsimParamRef := args[vsimIdx+2]
	require.True(t, strings.HasPrefix(vsimParamRef, "$"))
	generatedName := strings.TrimPrefix(vsimParamRef, "$")

	paramsIdx := slices.Index(args, "PARAMS")
	require.NotEqual(t, -1, paramsIdx, "PARAMS must be emitted for a generated raw-vector param")
	require.Greater(t, len(args), paramsIdx+2)
	paramsKey := args[paramsIdx+2]
	assert.Equal(t, generatedName, paramsKey, "PARAMS key must be the generated name without its $ prefix")
	assert.False(t, strings.HasPrefix(paramsKey, "$"), "PARAMS key must not carry the $ prefix")
}

func TestBuildHybridArgs_DollarLeadingByteVectorReachesParamsAsValue(t *testing.T) {
	rawVector := []byte("$notareference-raw-bytes")
	searchArm := options.FtHybridSearchArm{Query: "hello"}
	vsimArm := options.FtHybridVsimArm{Field: "vec", Vector: rawVector}

	args, err := buildHybridArgs("idx", searchArm, vsimArm, nil)
	require.NoError(t, err)

	paramsIdx := slices.Index(args, "PARAMS")
	require.NotEqual(t, -1, paramsIdx)
	require.Greater(t, len(args), paramsIdx+3)
	paramsValue := args[paramsIdx+3]
	assert.Equal(t, string(rawVector), paramsValue, "the raw vector bytes must reach PARAMS unchanged as the value")
}

func TestBuildHybridArgs_PropagatesVsimArmValidationError(t *testing.T) {
	searchArm := options.FtHybridSearchArm{Query: "hello"}
	vsimArm := options.FtHybridVsimArm{Field: "vec"}

	_, err := buildHybridArgs("idx", searchArm, vsimArm, nil)
	assert.Error(t, err)
}

func TestBuildCursorReadArgs_WithCount(t *testing.T) {
	count := 25
	args := buildCursorReadArgs("idx", 12345, &count)
	assert.Equal(t, []string{"FT.CURSOR", "READ", "idx", "12345", "COUNT", "25"}, args)
}

func TestBuildCursorReadArgs_WithoutCount(t *testing.T) {
	args := buildCursorReadArgs("idx", 12345, nil)
	assert.Equal(t, []string{"FT.CURSOR", "READ", "idx", "12345"}, args)
}

func TestBuildCursorDelArgs(t *testing.T) {
	args := buildCursorDelArgs("idx", 999)
	assert.Equal(t, []string{"FT.CURSOR", "DEL", "idx", "999"}, args)
}
