// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

type triStateBool int

// Tri-state bool for use option builders. We cannot rely on the default value of an non-initialized variable.
const (
	triStateBoolUndefined triStateBool = iota
	triStateBoolTrue
	triStateBoolFalse
)

// Optional arguments to `XAdd` in [StreamCommands]
type XAddOptions struct {
	id          string
	makeStream  triStateBool
	trimOptions *XTrimOptions
}

// Create new empty `XAddOptions`
func NewXAddOptions() *XAddOptions {
	return &XAddOptions{}
}

// New entry will be added with this `id`.
func (xao *XAddOptions) SetId(id string) *XAddOptions {
	xao.id = id
	return xao
}

// If set, a new stream won't be created if no stream matches the given key.
func (xao *XAddOptions) SetDontMakeNewStream() *XAddOptions {
	xao.makeStream = triStateBoolFalse
	return xao
}

// If set, add operation will also trim the older entries in the stream.
func (xao *XAddOptions) SetTrimOptions(options *XTrimOptions) *XAddOptions {
	xao.trimOptions = options
	return xao
}

func (xao *XAddOptions) ToArgs() ([]string, error) {
	args := []string{}
	if xao.makeStream == triStateBoolFalse {
		args = append(args, "NOMKSTREAM")
	}
	if xao.trimOptions != nil {
		moreArgs, err := xao.trimOptions.ToArgs()
		if err != nil {
			return args, err
		}
		args = append(args, moreArgs...)
	}
	if xao.id != "" {
		args = append(args, xao.id)
	} else {
		args = append(args, "*")
	}
	return args, nil
}

// Optional arguments for `XTrim` and `XAdd` in [StreamCommands]
type XTrimOptions struct {
	exact     triStateBool
	limit     int64
	method    string
	threshold string
}

// Option to trim the stream according to minimum ID.
func NewXTrimOptionsWithMinId(threshold string) *XTrimOptions {
	return &XTrimOptions{threshold: threshold, method: "MINID"}
}

// Option to trim the stream according to maximum stream length.
func NewXTrimOptionsWithMaxLen(threshold int64) *XTrimOptions {
	return &XTrimOptions{threshold: utils.IntToString(threshold), method: "MAXLEN"}
}

// Match exactly on the threshold.
func (xTrimOptions *XTrimOptions) SetExactTrimming() *XTrimOptions {
	xTrimOptions.exact = triStateBoolTrue
	return xTrimOptions
}

// Trim in a near-exact manner, which is more efficient.
func (xTrimOptions *XTrimOptions) SetNearlyExactTrimming() *XTrimOptions {
	xTrimOptions.exact = triStateBoolFalse
	return xTrimOptions
}

// Max number of stream entries to be trimmed for non-exact match.
func (xTrimOptions *XTrimOptions) SetNearlyExactTrimmingAndLimit(limit int64) *XTrimOptions {
	xTrimOptions.exact = triStateBoolFalse
	xTrimOptions.limit = limit
	return xTrimOptions
}

func (xTrimOptions *XTrimOptions) ToArgs() ([]string, error) {
	args := []string{xTrimOptions.method}
	if xTrimOptions.exact == triStateBoolTrue {
		args = append(args, "=")
	} else if xTrimOptions.exact == triStateBoolFalse {
		args = append(args, "~")
	}
	args = append(args, xTrimOptions.threshold)
	if xTrimOptions.limit > 0 {
		args = append(args, "LIMIT", utils.IntToString(xTrimOptions.limit))
	}
	return args, nil
}

// Optional arguments for `XRead` in [StreamCommands]
type XReadOptions struct {
	count, block int64
}

// Create new empty `XReadOptions`
func NewXReadOptions() *XReadOptions {
	return &XReadOptions{-1, -1}
}

// The maximal number of elements requested. Equivalent to `COUNT` in the Valkey API.
func (xro *XReadOptions) SetCount(count int64) *XReadOptions {
	xro.count = count
	return xro
}

// If set, the request will be blocked for the set amount of milliseconds or until the server has
// the required number of entries. A value of `0` will block indefinitely. Equivalent to `BLOCK` in the Valkey API.
func (xro *XReadOptions) SetBlock(block int64) *XReadOptions {
	xro.block = block
	return xro
}

func (xro *XReadOptions) ToArgs() ([]string, error) {
	args := []string{}
	if xro.count >= 0 {
		args = append(args, "COUNT", utils.IntToString(xro.count))
	}
	if xro.block >= 0 {
		args = append(args, "BLOCK", utils.IntToString(xro.block))
	}
	return args, nil
}

// Optional arguments for `XReadGroup` in [StreamCommands]
type XReadGroupOptions struct {
	count, block int64
	noAck        bool
}

// Create new empty `XReadOptions`
func NewXReadGroupOptions() *XReadGroupOptions {
	return &XReadGroupOptions{-1, -1, false}
}

// The maximal number of elements requested. Equivalent to `COUNT` in the Valkey API.
func (xrgo *XReadGroupOptions) SetCount(count int64) *XReadGroupOptions {
	xrgo.count = count
	return xrgo
}

// If set, the request will be blocked for the set amount of milliseconds or until the server has
// the required number of entries. A value of `0` will block indefinitely. Equivalent to `BLOCK` in the Valkey API.
func (xrgo *XReadGroupOptions) SetBlock(block int64) *XReadGroupOptions {
	xrgo.block = block
	return xrgo
}

// If set, messages are not added to the Pending Entries List (PEL). This is equivalent to
// acknowledging the message when it is read.
func (xrgo *XReadGroupOptions) SetNoAck() *XReadGroupOptions {
	xrgo.noAck = true
	return xrgo
}

func (xrgo *XReadGroupOptions) ToArgs() ([]string, error) {
	args := []string{}
	if xrgo.count >= 0 {
		args = append(args, "COUNT", utils.IntToString(xrgo.count))
	}
	if xrgo.block >= 0 {
		args = append(args, "BLOCK", utils.IntToString(xrgo.block))
	}
	if xrgo.noAck {
		args = append(args, "NOACK")
	}
	return args, nil
}

// Optional arguments for `XPending` in [StreamCommands]
type XPendingOptions struct {
	minIdleTime int64
	start       string
	end         string
	count       int64
	consumer    string
}

// Create new empty `XPendingOptions`. The `start`, `end` and `count` arguments are required.
func NewXPendingOptions(start string, end string, count int64) *XPendingOptions {
	options := &XPendingOptions{}
	options.start = start
	options.end = end
	options.count = count
	return options
}

// SetMinIdleTime sets the minimum idle time for the XPendingOptions.
// minIdleTime is the amount of time (in milliseconds) that a message must be idle to be considered.
// It returns the updated XPendingOptions.
func (xpo *XPendingOptions) SetMinIdleTime(minIdleTime int64) *XPendingOptions {
	xpo.minIdleTime = minIdleTime
	return xpo
}

// SetConsumer sets the consumer for the XPendingOptions.
// consumer is the name of the consumer to filter the pending messages.
// It returns the updated XPendingOptions.
func (xpo *XPendingOptions) SetConsumer(consumer string) *XPendingOptions {
	xpo.consumer = consumer
	return xpo
}

func (xpo *XPendingOptions) ToArgs() ([]string, error) {
	args := []string{}

	// if minIdleTime is set, we need to add an `IDLE` argument along with the minIdleTime
	if xpo.minIdleTime > 0 {
		args = append(args, "IDLE")
		args = append(args, utils.IntToString(xpo.minIdleTime))
	}

	args = append(args, xpo.start)
	args = append(args, xpo.end)
	args = append(args, utils.IntToString(xpo.count))

	if xpo.consumer != "" {
		args = append(args, xpo.consumer)
	}

	return args, nil
}
