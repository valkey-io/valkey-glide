// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// This struct represents the optional arguments for the ZINTER command.
type ZInterOptions struct {
	Aggregate Aggregate
}

func NewZInterOptions() *ZInterOptions {
	return &ZInterOptions{}
}

// SetAggregate sets the aggregate method for the ZInter command.
func (options *ZInterOptions) SetAggregate(aggregate Aggregate) *ZInterOptions {
	options.Aggregate = aggregate
	return options
}

func (options *ZInterOptions) ToArgs() ([]string, error) {
	if options.Aggregate != "" {
		return options.Aggregate.ToArgs()
	}

	return []string{}, nil
}
