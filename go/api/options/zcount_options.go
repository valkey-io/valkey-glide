// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import "github.com/valkey-io/valkey-glide/go/glide/utils"

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

type InfScoreBound struct {
	value InfBoundary
}

func NewInfScoreBoundBuilder() *InfScoreBound {
	return &InfScoreBound{}
}

func (infScoreBound *InfScoreBound) SetValue(value InfBoundary) *InfScoreBound {
	infScoreBound.value = value
	return infScoreBound
}

func (infScoreBound *InfScoreBound) ToArgs() ([]string, error) {
	args := []string{}
	args = append(args, string(infScoreBound.value))
	return args, nil
}

type ScoreBoundary struct {
	bound       float64
	isInclusive bool
}

func NewScoreBoundaryBuilder() *ScoreBoundary {
	return &ScoreBoundary{}
}

func (scoreBoundary *ScoreBoundary) SetBound(bound float64) *ScoreBoundary {
	scoreBoundary.bound = bound
	return scoreBoundary
}

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

type ZCountRange struct {
	min ScoreRange
	max ScoreRange
}

func NewZCountRangeBuilder() *ZCountRange {
	return &ZCountRange{}
}

func (zCountRange *ZCountRange) SetMin(min ScoreRange) *ZCountRange {
	zCountRange.min = min
	return zCountRange
}

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
