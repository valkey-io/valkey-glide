// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/api/errors"
)

type BitOpType string

const (
	AND BitOpType = "AND"
	OR  BitOpType = "OR"
	XOR BitOpType = "XOR"
	NOT BitOpType = "NOT"
)

// BitOp represents a BITOP operation.
type BitOp struct {
	Operation BitOpType
	DestKey   string
	SrcKeys   []string
}

// NewBitOp validates and creates a new BitOp command.
func NewBitOp(operation BitOpType, destKey string, srcKeys []string) (*BitOp, error) {
	if operation == NOT {
		if len(srcKeys) != 1 {
			return nil, &errors.RequestError{Msg: "BITOP NOT requires exactly 1 source key"}
		}
	} else {
		if len(srcKeys) < 2 {
			return nil, &errors.RequestError{Msg: "BITOP requires at least 2 source keys"}
		}
	}

	return &BitOp{
		Operation: operation,
		DestKey:   destKey,
		SrcKeys:   srcKeys,
	}, nil
}

// ToArgs converts the BitOp command to arguments.
func (cmd *BitOp) ToArgs() ([]string, error) {
	args := []string{string(cmd.Operation), cmd.DestKey}
	args = append(args, cmd.SrcKeys...)
	return args, nil
}
