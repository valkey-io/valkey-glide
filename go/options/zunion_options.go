// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package options

// This struct represents the optional arguments for the ZUNION command.
type ZUnionOptions struct {
	aggregate Aggregate
}

func NewZUnionOptionsBuilder() *ZUnionOptions {
	return &ZUnionOptions{}
}

// SetAggregate sets the aggregate method for the ZUnion command.
func (options *ZUnionOptions) SetAggregate(aggregate Aggregate) *ZUnionOptions {
	options.aggregate = aggregate
	return options
}

func (options *ZUnionOptions) ToArgs() ([]string, error) {
	if options.aggregate != "" {
		return options.aggregate.ToArgs()
	}

	return []string{}, nil
}
