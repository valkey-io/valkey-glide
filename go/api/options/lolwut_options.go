// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/utils"
)

type VersionString string

const (
	VersionStr VersionString = "VERSION"
)

// Optional arguments to `Lolwut` for standalone client
type LolwutOptions struct {
	Version int64
	Args    *[]int
}

// Optional arguments to `Lolwut` for cluster client
type ClusterLolwutOptions struct {
	*LolwutOptions
	*RouteOption
}

// NewLolwutOptions creates a new LolwutOptions with the specified version.
func NewLolwutOptions(version int64) *LolwutOptions {
	return &LolwutOptions{Version: version}
}

// SetArgs sets the additional numeric arguments for the LOLWUT command.
// These arguments customize the dimensions or parameters of the ASCII art
// based on the version.
func (options *LolwutOptions) SetArgs(args []int) *LolwutOptions {
	options.Args = &args
	return options
}

func (options *LolwutOptions) ToArgs() ([]string, error) {
	if options == nil {
		return []string{}, nil
	}
	args := []string{string(VersionStr), utils.IntToString(options.Version)}

	if options.Args != nil {
		for _, arg := range *options.Args {
			args = append(args, utils.IntToString(int64(arg)))
		}
	}

	return args, nil
}
