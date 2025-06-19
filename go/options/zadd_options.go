// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

// Optional arguments to `ZAdd` in [SortedSetCommands]
type ZAddOptions struct {
	ConditionalChange constants.ConditionalSet
	UpdateOptions     UpdateOptions
	Changed           bool
	Incr              bool
	Increment         float64
	Member            string
}

func NewZAddOptions() *ZAddOptions {
	return &ZAddOptions{}
}

// `conditionalChange` defines conditions for updating or adding elements with `ZADD` command.
func (options *ZAddOptions) SetConditionalChange(c constants.ConditionalSet) *ZAddOptions {
	options.ConditionalChange = c
	return options
}

// `updateOptions` specifies conditions for updating scores with zadd command.
func (options *ZAddOptions) SetUpdateOptions(u UpdateOptions) *ZAddOptions {
	options.UpdateOptions = u
	return options
}

// `Changed` changes the return value from the number of new elements added to the total number of elements changed.
func (options *ZAddOptions) SetChanged(ch bool) (*ZAddOptions, error) {
	if options.Incr {
		return nil, errors.New("changed cannot be set when incr is true")
	}
	options.Changed = ch
	return options, nil
}

// `INCR` sets the increment value to use when incr is true.
func (options *ZAddOptions) SetIncr(incr bool, increment float64, member string) (*ZAddOptions, error) {
	if options.Changed {
		return nil, errors.New("incr cannot be set when changed is true")
	}
	options.Incr = incr
	options.Increment = increment
	options.Member = member
	return options, nil
}

// `ToArgs` converts the options to a list of arguments.
func (opts *ZAddOptions) ToArgs() ([]string, error) {
	args := []string{}
	var err error

	if opts.ConditionalChange == constants.OnlyIfExists || opts.ConditionalChange == constants.OnlyIfDoesNotExist {
		args = append(args, string(opts.ConditionalChange))
	}

	if opts.UpdateOptions == ScoreGreaterThanCurrent || opts.UpdateOptions == ScoreLessThanCurrent {
		args = append(args, string(opts.UpdateOptions))
	}

	if opts.Changed {
		args = append(args, constants.ChangedKeyword)
	}

	if opts.Incr {
		args = append(args, constants.IncrKeyword, utils.FloatToString(opts.Increment), opts.Member)
	}

	return args, err
}

type UpdateOptions string

const (
	// Only update existing elements if the new score is less than the current score. Equivalent to
	// "LT" in the Valkey API.
	ScoreLessThanCurrent UpdateOptions = "LT"
	// Only update existing elements if the new score is greater than the current score. Equivalent
	// to "GT" in the Valkey API.
	ScoreGreaterThanCurrent UpdateOptions = "GT"
)
