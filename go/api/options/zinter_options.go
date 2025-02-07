// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// This struct represents the optional arguments for the ZINTER command.
type ZInterOptions struct {
	keysOrWeightedKeys KeysOrWeightedKeys
	aggregate          Aggregate
}

func NewZInterOptionsBuilder(keysOrWeightedKeys KeysOrWeightedKeys) *ZInterOptions {
	return &ZInterOptions{keysOrWeightedKeys: keysOrWeightedKeys}
}

// SetAggregate sets the aggregate method for the ZInter command.
func (options *ZInterOptions) SetAggregate(aggregate Aggregate) *ZInterOptions {
	options.aggregate = aggregate
	return options
}

func (options *ZInterOptions) ToArgs() ([]string, error) {
	args := []string{}

	if options.keysOrWeightedKeys != nil {
		keysArgs, err := options.keysOrWeightedKeys.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, keysArgs...)
	}

	if options.aggregate != "" {
		aggArgs, err := options.aggregate.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, aggArgs...)
	}

	return args, nil
}
