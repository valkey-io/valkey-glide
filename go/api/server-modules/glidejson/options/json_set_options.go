// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package options

import (
	"github.com/valkey-io/valkey-glide/go/api/options"
)

type JsonSetOptions struct {
	ConditionalSet options.ConditionalSet
}

func NewJsonSetOptionsBuilder() *JsonSetOptions {
	return &JsonSetOptions{}
}

func (jsonSetOptions *JsonSetOptions) SetConditionalSet(conditionalSet options.ConditionalSet) *JsonSetOptions {
	jsonSetOptions.ConditionalSet = conditionalSet
	return jsonSetOptions
}

func (opts *JsonSetOptions) ToArgs() ([]string, error) {
	args := []string{}
	var err error
	if opts.ConditionalSet != "" {
		args = append(args, string(opts.ConditionalSet))
	}
	return args, err
}
