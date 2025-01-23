// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

type EncType string

const (
	SignedInt   EncType = "i"
	UnsignedInt EncType = "u"
)

type BitFieldGetOpts struct {
	encType EncType
	bits    *int64
	offset  *int64
	useHash bool
}

// BitFieldOptions represents options for BITFIELD command
type BitFieldOptions struct {
	gets     []BitFieldGetOpts
	overflow OverflowType
	sets     []BitFieldSetOpts
	incrs    []BitFieldIncrOpts
}

// BitFieldSetOpts represents options for SET subcommand
type BitFieldSetOpts struct {
	encType EncType
	bits    *int64
	offset  *int64
	value   *int64
	useHash bool
}

// BitFieldIncrOpts represents options for INCRBY subcommand
type BitFieldIncrOpts struct {
	encType   EncType
	bits      *int64
	offset    *int64
	increment *int64
	useHash   bool
}

type OverflowType string

const (
	WRAP OverflowType = "WRAP"
	SAT  OverflowType = "SAT"
	FAIL OverflowType = "FAIL"
)

func NewBitFieldOptionsBuilder() *BitFieldOptions {
	return &BitFieldOptions{}
}

// AddGet adds a GET subcommand
func (opts *BitFieldOptions) AddGet(encType EncType, bits int64, offset int64) *BitFieldOptions {
	opts.gets = append(opts.gets, BitFieldGetOpts{
		encType: encType,
		bits:    &bits,
		offset:  &offset,
	})
	return opts
}

// SetOverflow sets overflow behavior for following SET and INCRBY operations
func (opts *BitFieldOptions) SetOverflow(overflow OverflowType) *BitFieldOptions {
	opts.overflow = overflow
	return opts
}

// AddSet adds a SET subcommand
func (opts *BitFieldOptions) AddSet(encType EncType, bits int64, offset int64, value int64) *BitFieldOptions {
	opts.sets = append(opts.sets, BitFieldSetOpts{
		encType: encType,
		bits:    &bits,
		offset:  &offset,
		value:   &value,
	})
	return opts
}

// AddIncrBy adds an INCRBY subcommand
func (opts *BitFieldOptions) AddIncrBy(encType EncType, bits int64, offset int64, increment int64) *BitFieldOptions {
	opts.incrs = append(opts.incrs, BitFieldIncrOpts{
		encType:   encType,
		bits:      &bits,
		offset:    &offset,
		increment: &increment,
	})
	return opts
}

// UseHashNotation sets hash notation for the last added operation
func (opts *BitFieldOptions) UseHashNotation() *BitFieldOptions {
	if len(opts.gets) > 0 {
		last := len(opts.gets) - 1
		opts.gets[last].useHash = true
	}
	if len(opts.sets) > 0 {
		last := len(opts.sets) - 1
		opts.sets[last].useHash = true
	}
	if len(opts.incrs) > 0 {
		last := len(opts.incrs) - 1
		opts.incrs[last].useHash = true
	}
	return opts
}

func (opts *BitFieldOptions) ToArgs() []string {
	var args []string

	for _, get := range opts.gets {
		args = append(args, "GET")
		args = append(args, string(get.encType)+utils.IntToString(*get.bits))
		if get.useHash {
			args = append(args, "#"+utils.IntToString(*get.offset))
		} else {
			args = append(args, utils.IntToString(*get.offset))
		}
	}

	if opts.overflow != "" {
		args = append(args, "OVERFLOW", string(opts.overflow))
	}

	for _, set := range opts.sets {
		args = append(args, "SET")
		args = append(args, string(set.encType)+utils.IntToString(*set.bits))
		if set.useHash {
			args = append(args, "#"+utils.IntToString(*set.offset))
		} else {
			args = append(args, utils.IntToString(*set.offset))
		}
		args = append(args, utils.IntToString(*set.value))
	}

	for _, incr := range opts.incrs {
		args = append(args, "INCRBY")
		args = append(args, string(incr.encType)+utils.IntToString(*incr.bits))
		if incr.useHash {
			args = append(args, "#"+utils.IntToString(*incr.offset))
		} else {
			args = append(args, utils.IntToString(*incr.offset))
		}
		args = append(args, utils.IntToString(*incr.increment))
	}

	return args
}

// BitFieldROOptions represents options for BITFIELD_RO command
type BitFieldROOptions struct {
	gets []BitFieldGetOpts
}

func NewBitFieldROOptionsBuilder() *BitFieldROOptions {
	return &BitFieldROOptions{}
}

// AddGet adds a GET subcommand for BITFIELD_RO
func (opts *BitFieldROOptions) AddGet(encType EncType, bits int64, offset int64) *BitFieldROOptions {
	opts.gets = append(opts.gets, BitFieldGetOpts{
		encType: encType,
		bits:    &bits,
		offset:  &offset,
	})
	return opts
}

// UseHashNotation sets hash notation for the last added GET operation
func (opts *BitFieldROOptions) UseHashNotation() *BitFieldROOptions {
	if len(opts.gets) > 0 {
		last := len(opts.gets) - 1
		opts.gets[last].useHash = true
	}
	return opts
}

func (opts *BitFieldROOptions) ToArgs() []string {
	var args []string

	for _, get := range opts.gets {
		args = append(args, "GET")
		args = append(args, string(get.encType)+utils.IntToString(*get.bits))
		if get.useHash {
			args = append(args, "#"+utils.IntToString(*get.offset))
		} else {
			args = append(args, utils.IntToString(*get.offset))
		}
	}

	return args
}
