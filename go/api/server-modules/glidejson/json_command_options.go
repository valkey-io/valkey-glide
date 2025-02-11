// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package glidejson

import (
	"github.com/valkey-io/valkey-glide/go/api"
)

type JsonSetOptions struct {
	ConditionalSet api.ConditionalSet
}

func NewJsonSetOptionsBuilder() *JsonSetOptions {
	return &JsonSetOptions{}
}

func (jsonSetOptions *JsonSetOptions) SetConditionalSet(conditionalSet api.ConditionalSet) *JsonSetOptions {
	jsonSetOptions.ConditionalSet = conditionalSet
	return jsonSetOptions
}

func (opts *JsonSetOptions) toArgs() ([]string, error) {
	args := []string{}
	var err error
	if opts.ConditionalSet != "" {
		args = append(args, string(opts.ConditionalSet))
	}
	return args, err
}
