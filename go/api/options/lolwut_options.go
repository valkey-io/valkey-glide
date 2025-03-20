// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/utils"
)

type VersionString string

const (
	VersionStr VersionString = "VERSION"
)

type LolwutOptions struct {
	Version int64
	Args    *[]int
}

type ClusterLolwutOptions struct {
	*LolwutOptions
	*RouteOption
}

func NewLolwutOptions(version int64) *LolwutOptions {
	return &LolwutOptions{Version: version}
}

func (options *LolwutOptions) SetArgs(args []int) *LolwutOptions {
	options.Args = &args
	return options
}

func (options *LolwutOptions) ToArgs() ([]string, error) {
	if options == nil {
		return []string{}, nil
	}
	// var err error
	args := []string{string(VersionStr), utils.IntToString(options.Version)}

	if options.Args != nil {
		for _, arg := range *options.Args {
			args = append(args, utils.IntToString(int64(arg)))
		}
	}

	return args, nil
}
