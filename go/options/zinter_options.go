// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// This struct represents the optional arguments for the ZINTER command.
type ZInterOptions struct {
	aggregate Aggregate
}

func NewZInterOptions() *ZInterOptions {
	return &ZInterOptions{}
}

// SetAggregate sets the aggregate method for the ZInter command.
func (options *ZInterOptions) SetAggregate(aggregate Aggregate) *ZInterOptions {
	options.aggregate = aggregate
	return options
}

func (options *ZInterOptions) ToArgs() ([]string, error) {
	if options.aggregate != "" {
		return options.aggregate.ToArgs()
	}

	return []string{}, nil
}
