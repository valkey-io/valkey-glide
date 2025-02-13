// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/utils"
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
		args = append(args, NoMakeStreamKeyword)
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
	return &XTrimOptions{threshold: threshold, method: MinIdKeyword}
}

// Option to trim the stream according to maximum stream length.
func NewXTrimOptionsWithMaxLen(threshold int64) *XTrimOptions {
	return &XTrimOptions{threshold: utils.IntToString(threshold), method: MaxLenKeyword}
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
		args = append(args, LimitKeyword, utils.IntToString(xTrimOptions.limit))
	}
	return args, nil
}

// Optional arguments for `XAutoClaim` in [StreamCommands]
type XAutoClaimOptions struct {
	count *int64
}

func NewXAutoClaimOptions() *XAutoClaimOptions {
	return &XAutoClaimOptions{nil}
}

// Set the number of claimed entries.
func (xacp *XAutoClaimOptions) SetCount(count int64) *XAutoClaimOptions {
	xacp.count = &count
	return xacp
}

func (xacp *XAutoClaimOptions) ToArgs() ([]string, error) {
	if xacp.count == nil {
		return []string{}, nil
	}
	return []string{CountKeyword, utils.IntToString(*xacp.count)}, nil
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
		args = append(args, CountKeyword, utils.IntToString(xro.count))
	}
	if xro.block >= 0 {
		args = append(args, BlockKeyword, utils.IntToString(xro.block))
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
		args = append(args, CountKeyword, utils.IntToString(xrgo.count))
	}
	if xrgo.block >= 0 {
		args = append(args, BlockKeyword, utils.IntToString(xrgo.block))
	}
	if xrgo.noAck {
		args = append(args, NoAckKeyword)
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
	options := XPendingOptions{}
	options.start = start
	options.end = end
	options.count = count
	return &options
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

	if xpo.minIdleTime > 0 {
		args = append(args, IdleKeyword)
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

// Optional arguments for `XGroupCreate` in [StreamCommands]
type XGroupCreateOptions struct {
	mkStream    bool
	entriesRead *int64
}

// Create new empty `XGroupCreateOptions`
func NewXGroupCreateOptions() *XGroupCreateOptions {
	return &XGroupCreateOptions{false, nil}
}

// Once set and if the stream doesn't exist, creates a new stream with a length of `0`.
func (xgco *XGroupCreateOptions) SetMakeStream() *XGroupCreateOptions {
	xgco.mkStream = true
	return xgco
}

func (xgco *XGroupCreateOptions) SetEntriesRead(entriesRead int64) *XGroupCreateOptions {
	xgco.entriesRead = &entriesRead
	return xgco
}

func (xgco *XGroupCreateOptions) ToArgs() ([]string, error) {
	var args []string

	// if minIdleTime is set, we need to add an `IDLE` argument along with the minIdleTime
	if xgco.mkStream {
		args = append(args, MakeStreamKeyword)
	}

	if xgco.entriesRead != nil {
		args = append(args, EntriesReadKeyword, utils.IntToString(*xgco.entriesRead))
	}

	return args, nil
}

// Optional arguments for `XGroupSetId` in [StreamCommands]
type XGroupSetIdOptions struct {
	entriesRead int64
}

// Create new empty `XGroupSetIdOptions`
func NewXGroupSetIdOptionsOptions() *XGroupSetIdOptions {
	return &XGroupSetIdOptions{-1}
}

// A value representing the number of stream entries already read by the group.
//
// Since Valkey version 7.0.0.
func (xgsio *XGroupSetIdOptions) SetEntriesRead(entriesRead int64) *XGroupSetIdOptions {
	xgsio.entriesRead = entriesRead
	return xgsio
}

func (xgsio *XGroupSetIdOptions) ToArgs() ([]string, error) {
	var args []string

	if xgsio.entriesRead > -1 {
		args = append(args, EntriesReadKeyword, utils.IntToString(xgsio.entriesRead))
	}

	return args, nil
}

// Optional arguments for `XClaim` in [StreamCommands]
type XClaimOptions struct {
	idleTime     int64
	idleUnixTime int64
	retryCount   int64
	isForce      bool
}

func NewXClaimOptions() *XClaimOptions {
	return &XClaimOptions{}
}

// Set the idle time in milliseconds.
func (xco *XClaimOptions) SetIdleTime(idleTime int64) *XClaimOptions {
	xco.idleTime = idleTime
	return xco
}

// Set the idle time in unix-milliseconds.
func (xco *XClaimOptions) SetIdleUnixTime(idleUnixTime int64) *XClaimOptions {
	xco.idleUnixTime = idleUnixTime
	return xco
}

// Set the retry count.
func (xco *XClaimOptions) SetRetryCount(retryCount int64) *XClaimOptions {
	xco.retryCount = retryCount
	return xco
}

// Set the force flag.
func (xco *XClaimOptions) SetForce() *XClaimOptions {
	xco.isForce = true
	return xco
}

func (sco *XClaimOptions) ToArgs() ([]string, error) {
	optionArgs := []string{}

	if sco.idleTime > 0 {
		optionArgs = append(optionArgs, IdleKeyword, utils.IntToString(sco.idleTime))
	}

	if sco.idleUnixTime > 0 {
		optionArgs = append(optionArgs, TimeKeyword, utils.IntToString(sco.idleUnixTime))
	}

	if sco.retryCount > 0 {
		optionArgs = append(optionArgs, RetryCountKeyword, utils.IntToString(sco.retryCount))
	}

	if sco.isForce {
		optionArgs = append(optionArgs, ForceKeyword)
	}

	return optionArgs, nil
}

type StreamBoundary string

// Create a new stream boundary.
func NewStreamBoundary(streamId string, isInclusive bool) StreamBoundary {
	if !isInclusive {
		return StreamBoundary("(" + streamId)
	}
	return StreamBoundary(streamId)
}

// Create a new stream boundary defined by an infinity.
func NewInfiniteStreamBoundary(bound InfBoundary) StreamBoundary {
	return StreamBoundary(string(bound))
}

// Optional arguments for `XRange` and `XRevRange` in [StreamCommands]
type XRangeOptions struct {
	count *int64
}

func NewXRangeOptions() *XRangeOptions {
	return &XRangeOptions{}
}

// Set the count.
func (sro *XRangeOptions) SetCount(count int64) *XRangeOptions {
	sro.count = &count
	return sro
}

func (sro *XRangeOptions) ToArgs() ([]string, error) {
	var args []string

	if sro.count != nil {
		args = append(args, CountKeyword, utils.IntToString(*sro.count))
	}

	return args, nil
}
