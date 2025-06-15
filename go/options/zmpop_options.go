// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

// Optional arguments for `ZMPop` and `BZMPop` in [SortedSetCommands]
type ZMPopOptions struct {
	Count int64
}

func NewZMPopOptions() *ZMPopOptions {
	return &ZMPopOptions{}
}

// Set the count.
func (zmpo *ZMPopOptions) SetCount(count int64) *ZMPopOptions {
	zmpo.Count = count
	return zmpo
}

func (zmpo *ZMPopOptions) ToArgs() ([]string, error) {
	var args []string

	if zmpo.Count != 0 {
		args = append(args, constants.CountKeyword, utils.IntToString(zmpo.Count))
	}

	return args, nil
}
