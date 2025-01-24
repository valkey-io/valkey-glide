package options

import (
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

// BitFieldSubCommand interface for all BITFIELD commands
type BitFieldSubCommand interface {
	ToArgs() ([]string, error)
}

// BitFieldROCommand for read-only operations
type BitFieldROCommand interface {
	dummy()
	ToArgs() ([]string, error)
}

type EncType string

const (
	SignedInt   EncType = "i"
	UnsignedInt EncType = "u"
)

type OverflowType string

const (
	WRAP OverflowType = "WRAP"
	SAT  OverflowType = "SAT"
	FAIL OverflowType = "FAIL"
)

// GET implementation
type BitFieldGet struct {
	EncType EncType
	Bits    int64
	Offset  int64
	UseHash bool
}

func NewBitFieldGet(encType EncType, bits int64, offset int64) *BitFieldGet {
	return &BitFieldGet{
		EncType: encType,
		Bits:    bits,
		Offset:  offset,
	}
}

func (cmd *BitFieldGet) ToArgs() ([]string, error) {
	args := []string{"GET"}
	args = append(args, string(cmd.EncType)+utils.IntToString(cmd.Bits))
	if cmd.UseHash {
		args = append(args, "#"+utils.IntToString(cmd.Offset))
	} else {
		args = append(args, utils.IntToString(cmd.Offset))
	}
	return args, nil
}

func (cmd *BitFieldGet) dummy() {}

// SET implementation
type BitFieldSet struct {
	EncType EncType
	Bits    int64
	Offset  int64
	Value   int64
	UseHash bool
}

func NewBitFieldSet(encType EncType, bits int64, offset int64, value int64) *BitFieldSet {
	return &BitFieldSet{
		EncType: encType,
		Bits:    bits,
		Offset:  offset,
		Value:   value,
	}
}

func (cmd *BitFieldSet) ToArgs() ([]string, error) {
	args := []string{"SET"}
	args = append(args, string(cmd.EncType)+utils.IntToString(cmd.Bits))
	if cmd.UseHash {
		args = append(args, "#"+utils.IntToString(cmd.Offset))
	} else {
		args = append(args, utils.IntToString(cmd.Offset))
	}
	args = append(args, utils.IntToString(cmd.Value))
	return args, nil
}

// INCRBY implementation
type BitFieldIncrBy struct {
	EncType   EncType
	Bits      int64
	Offset    int64
	Increment int64
	UseHash   bool
}

func NewBitFieldIncrBy(encType EncType, bits int64, offset int64, increment int64) *BitFieldIncrBy {
	return &BitFieldIncrBy{
		EncType:   encType,
		Bits:      bits,
		Offset:    offset,
		Increment: increment,
	}
}

func (cmd *BitFieldIncrBy) ToArgs() ([]string, error) {
	args := []string{"INCRBY"}
	args = append(args, string(cmd.EncType)+utils.IntToString(cmd.Bits))
	if cmd.UseHash {
		args = append(args, "#"+utils.IntToString(cmd.Offset))
	} else {
		args = append(args, utils.IntToString(cmd.Offset))
	}
	args = append(args, utils.IntToString(cmd.Increment))
	return args, nil
}

// OVERFLOW implementation
type BitFieldOverflow struct {
	Overflow OverflowType
}

func NewBitFieldOverflow(overflow OverflowType) *BitFieldOverflow {
	return &BitFieldOverflow{
		Overflow: overflow,
	}
}

func (cmd *BitFieldOverflow) ToArgs() ([]string, error) {
	return []string{"OVERFLOW", string(cmd.Overflow)}, nil
}
