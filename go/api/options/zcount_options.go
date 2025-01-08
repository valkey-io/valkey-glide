// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import "github.com/valkey-io/valkey-glide/go/glide/utils"

type ScoreRange interface {
	ToArgs() []string
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

func NewInfScoreBuilder() *InfScoreBound {
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

func (zCountRange *ZCountRange) ToArgs() ([]string, error) {
	args := []string{}
	args = append(args, zCountRange.min.ToArgs()...)
	args = append(args, zCountRange.max.ToArgs()...)
	return args, nil
}
