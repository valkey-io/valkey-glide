// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import "github.com/valkey-io/valkey-glide/go/glide/utils"

// The common interface for representing all the range type for Zcount command.
type ScoreRange interface {
	ToArgs() ([]string, error)
}

type (
	InfBoundary string
)

const (
	// The highest bound in the sorted set
	PositiveInfinity InfBoundary = "+inf"
	// The lowest bound in the sorted set
	NegativeInfinity InfBoundary = "-inf"
)

// This struct represents the infinity boundary for a score range.
type InfScoreBound struct {
	value InfBoundary
}

// Create a new infinite score boundary
func NewInfScoreBoundBuilder() *InfScoreBound {
	return &InfScoreBound{}
}

// The value of the infinite score bound.
func (infScoreBound *InfScoreBound) SetValue(value InfBoundary) *InfScoreBound {
	infScoreBound.value = value
	return infScoreBound
}

func (infScoreBound *InfScoreBound) ToArgs() ([]string, error) {
	args := []string{}
	args = append(args, string(infScoreBound.value))
	return args, nil
}

// This struct represents score boundary for a bound.
type ScoreBoundary struct {
	bound       float64
	isInclusive bool
}

// Create a new score boundary.
func NewScoreBoundaryBuilder() *ScoreBoundary {
	return &ScoreBoundary{isInclusive: true}
}

// Set the bound for a score boundary.
func (scoreBoundary *ScoreBoundary) SetBound(bound float64) *ScoreBoundary {
	scoreBoundary.bound = bound
	return scoreBoundary
}

// Set if the bound for a score boundary is inclusive or not inclusive in the boundary.
func (scoreBoundary *ScoreBoundary) SetIsInclusive(isInclusive bool) *ScoreBoundary {
	scoreBoundary.isInclusive = isInclusive
	return scoreBoundary
}

func (scoreBoundary *ScoreBoundary) ToArgs() ([]string, error) {
	args := []string{}
	if !scoreBoundary.isInclusive {
		args = append(args, "("+utils.FloatToString(scoreBoundary.bound))
	} else {
		args = append(args, utils.FloatToString(scoreBoundary.bound))
	}
	return args, nil
}

// This struct represents the min and max boundary for the Zcount command.
type ZCountRange struct {
	min ScoreRange
	max ScoreRange
}

// Create a new Zcount range.
func NewZCountRangeBuilder() *ZCountRange {
	return &ZCountRange{}
}

// Set the minimum value for the Zcount command range.
func (zCountRange *ZCountRange) SetMin(min ScoreRange) *ZCountRange {
	zCountRange.min = min
	return zCountRange
}

// Set the maximum value for the Zcount command range.
func (zCountRange *ZCountRange) SetMax(max ScoreRange) *ZCountRange {
	zCountRange.max = max
	return zCountRange
}

func (zCountRange *ZCountRange) ToArgs() ([]string, error) {
	args := []string{}
	minArgs, err := zCountRange.min.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, minArgs...)
	maxArgs, err := zCountRange.max.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, maxArgs...)
	return args, nil
}
