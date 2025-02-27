// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package options

import (
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// This struct represents the optional arguments for the JSON.SET command.
type JsonSetOptions struct {
	ConditionalSet options.ConditionalSet
}

func NewJsonSetOptionsBuilder() *JsonSetOptions {
	return &JsonSetOptions{}
}

// Sets the value only if the given condition is met (within the key or path).
func (jsonSetOptions *JsonSetOptions) SetConditionalSet(conditionalSet options.ConditionalSet) *JsonSetOptions {
	jsonSetOptions.ConditionalSet = conditionalSet
	return jsonSetOptions
}

// Converts JsonGetOptions into a []string.
func (opts JsonSetOptions) ToArgs() ([]string, error) {
	args := []string{}
	var err error
	if opts.ConditionalSet != "" {
		args = append(args, string(opts.ConditionalSet))
	}
	return args, err
}
