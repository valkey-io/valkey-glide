// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
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
	Id          string
	MakeStream  triStateBool
	TrimOptions *XTrimOptions
}

// Create new empty `XAddOptions`
func NewXAddOptions() *XAddOptions {
	return &XAddOptions{}
}

// New entry will be added with this `id`.
func (xao *XAddOptions) SetId(id string) *XAddOptions {
	xao.Id = id
	return xao
}

// If set, a new stream won't be created if no stream matches the given key.
func (xao *XAddOptions) SetDontMakeNewStream() *XAddOptions {
	xao.MakeStream = triStateBoolFalse
	return xao
}

// If set, add operation will also trim the older entries in the stream.
func (xao *XAddOptions) SetTrimOptions(options *XTrimOptions) *XAddOptions {
	xao.TrimOptions = options
	return xao
}

func (xao *XAddOptions) ToArgs() ([]string, error) {
	args := []string{}
	if xao.MakeStream == triStateBoolFalse {
		args = append(args, constants.NoMakeStreamKeyword)
	}
	if xao.TrimOptions != nil {
		moreArgs, err := xao.TrimOptions.ToArgs()
		if err != nil {
			return args, err
		}
		args = append(args, moreArgs...)
	}
	if xao.Id != "" {
		args = append(args, xao.Id)
	} else {
		args = append(args, "*")
	}
	return args, nil
}

// Optional arguments for `XTrim` and `XAdd` in [StreamCommands]
type XTrimOptions struct {
	Exact     triStateBool
	Limit     int64
	Method    string
	Threshold string
}

// Option to trim the stream according to minimum ID.
func NewXTrimOptionsWithMinId(threshold string) *XTrimOptions {
	return &XTrimOptions{Threshold: threshold, Method: constants.MinIdKeyword}
}

// Option to trim the stream according to maximum stream length.
func NewXTrimOptionsWithMaxLen(threshold int64) *XTrimOptions {
	return &XTrimOptions{Threshold: utils.IntToString(threshold), Method: constants.MaxLenKeyword}
}

// Match exactly on the threshold.
func (xTrimOptions *XTrimOptions) SetExactTrimming() *XTrimOptions {
	xTrimOptions.Exact = triStateBoolTrue
	return xTrimOptions
}

// Trim in a near-exact manner, which is more efficient.
func (xTrimOptions *XTrimOptions) SetNearlyExactTrimming() *XTrimOptions {
	xTrimOptions.Exact = triStateBoolFalse
	return xTrimOptions
}

// Max number of stream entries to be trimmed for non-exact match.
func (xTrimOptions *XTrimOptions) SetNearlyExactTrimmingAndLimit(limit int64) *XTrimOptions {
	xTrimOptions.Exact = triStateBoolFalse
	xTrimOptions.Limit = limit
	return xTrimOptions
}

func (xTrimOptions *XTrimOptions) ToArgs() ([]string, error) {
	args := []string{xTrimOptions.Method}
	if xTrimOptions.Exact == triStateBoolTrue {
		args = append(args, "=")
	} else if xTrimOptions.Exact == triStateBoolFalse {
		args = append(args, "~")
	}
	args = append(args, xTrimOptions.Threshold)
	if xTrimOptions.Limit > 0 {
		args = append(args, constants.LimitKeyword, utils.IntToString(xTrimOptions.Limit))
	}
	return args, nil
}

// Optional arguments for `XAutoClaim` in [StreamCommands]
type XAutoClaimOptions struct {
	Count int64
}

func NewXAutoClaimOptions() *XAutoClaimOptions {
	return &XAutoClaimOptions{-1}
}

// Set the number of claimed entries.
func (xacp *XAutoClaimOptions) SetCount(count int64) *XAutoClaimOptions {
	xacp.Count = count
	return xacp
}

func (xacp *XAutoClaimOptions) ToArgs() ([]string, error) {
	if xacp.Count < 0 {
		return []string{}, nil
	}
	return []string{constants.CountKeyword, utils.IntToString(xacp.Count)}, nil
}

// Optional arguments for `XRead` in [StreamCommands]
type XReadOptions struct {
	Count int64
	Block time.Duration
}

// Create new empty `XReadOptions`
func NewXReadOptions() *XReadOptions {
	return &XReadOptions{-1, -1}
}

// The maximal number of elements requested. Equivalent to `COUNT` in the Valkey API.
func (xro *XReadOptions) SetCount(count int64) *XReadOptions {
	xro.Count = count
	return xro
}

// If set, the request will be blocked for the set amount of milliseconds or until the server has
// the required number of entries. A value of `0` will block indefinitely. Equivalent to `BLOCK` in the Valkey API.
func (xro *XReadOptions) SetBlock(block time.Duration) *XReadOptions {
	xro.Block = block
	return xro
}

func (xro *XReadOptions) ToArgs() ([]string, error) {
	args := []string{}
	if xro.Count >= 0 {
		args = append(args, constants.CountKeyword, utils.IntToString(xro.Count))
	}
	if xro.Block >= 0 {
		args = append(args, constants.BlockKeyword, utils.IntToString(xro.Block.Milliseconds()))
	}
	return args, nil
}

// Optional arguments for `XReadGroup` in [StreamCommands]
type XReadGroupOptions struct {
	Count int64
	Block time.Duration
	NoAck bool
}

// Create new empty `XReadOptions`
func NewXReadGroupOptions() *XReadGroupOptions {
	return &XReadGroupOptions{-1, -1, false}
}

// The maximal number of elements requested. Equivalent to `COUNT` in the Valkey API.
func (xrgo *XReadGroupOptions) SetCount(count int64) *XReadGroupOptions {
	xrgo.Count = count
	return xrgo
}

// If set, the request will be blocked for the set amount of milliseconds or until the server has
// the required number of entries. A value of `0` will block indefinitely. Equivalent to `BLOCK` in the Valkey API.
func (xrgo *XReadGroupOptions) SetBlock(block time.Duration) *XReadGroupOptions {
	xrgo.Block = block
	return xrgo
}

// If set, messages are not added to the Pending Entries List (PEL). This is equivalent to
// acknowledging the message when it is read.
func (xrgo *XReadGroupOptions) SetNoAck() *XReadGroupOptions {
	xrgo.NoAck = true
	return xrgo
}

func (xrgo *XReadGroupOptions) ToArgs() ([]string, error) {
	args := []string{}
	if xrgo.Count >= 0 {
		args = append(args, constants.CountKeyword, utils.IntToString(xrgo.Count))
	}
	if xrgo.Block >= 0 {
		args = append(args, constants.BlockKeyword, utils.IntToString(xrgo.Block.Milliseconds()))
	}
	if xrgo.NoAck {
		args = append(args, constants.NoAckKeyword)
	}
	return args, nil
}

// Optional arguments for `XPending` in [StreamCommands]
type XPendingOptions struct {
	MinIdleTime int64
	Start       string
	End         string
	Count       int64
	Consumer    string
}

// Create new empty `XPendingOptions`. The `start`, `end` and `count` arguments are required.
func NewXPendingOptions(start string, end string, count int64) *XPendingOptions {
	options := XPendingOptions{}
	options.Start = start
	options.End = end
	options.Count = count
	return &options
}

// SetMinIdleTime sets the minimum idle time for the XPendingOptions.
// minIdleTime is the amount of time (in milliseconds) that a message must be idle to be considered.
// It returns the updated XPendingOptions.
func (xpo *XPendingOptions) SetMinIdleTime(minIdleTime int64) *XPendingOptions {
	xpo.MinIdleTime = minIdleTime
	return xpo
}

// SetConsumer sets the consumer for the XPendingOptions.
// consumer is the name of the consumer to filter the pending messages.
// It returns the updated XPendingOptions.
func (xpo *XPendingOptions) SetConsumer(consumer string) *XPendingOptions {
	xpo.Consumer = consumer
	return xpo
}

func (xpo *XPendingOptions) ToArgs() ([]string, error) {
	args := []string{}

	if xpo.MinIdleTime > 0 {
		args = append(args, constants.IdleKeyword)
		args = append(args, utils.IntToString(xpo.MinIdleTime))
	}

	args = append(args, xpo.Start)
	args = append(args, xpo.End)
	args = append(args, utils.IntToString(xpo.Count))

	if xpo.Consumer != "" {
		args = append(args, xpo.Consumer)
	}

	return args, nil
}

// Optional arguments for `XGroupCreate` in [StreamCommands]
type XGroupCreateOptions struct {
	MkStream    bool
	EntriesRead int64
}

// Create new empty `XGroupCreateOptions`
func NewXGroupCreateOptions() *XGroupCreateOptions {
	return &XGroupCreateOptions{false, 0}
}

// Once set and if the stream doesn't exist, creates a new stream with a length of `0`.
func (xgco *XGroupCreateOptions) SetMakeStream() *XGroupCreateOptions {
	xgco.MkStream = true
	return xgco
}

func (xgco *XGroupCreateOptions) SetEntriesRead(entriesRead int64) *XGroupCreateOptions {
	xgco.EntriesRead = entriesRead
	return xgco
}

func (xgco *XGroupCreateOptions) ToArgs() ([]string, error) {
	var args []string

	// if minIdleTime is set, we need to add an `IDLE` argument along with the minIdleTime
	if xgco.MkStream {
		args = append(args, constants.MakeStreamKeyword)
	}

	if xgco.EntriesRead != 0 {
		args = append(args, constants.EntriesReadKeyword, utils.IntToString(xgco.EntriesRead))
	}

	return args, nil
}

// Optional arguments for `XGroupSetId` in [StreamCommands]
type XGroupSetIdOptions struct {
	EntriesRead int64
}

// Create new empty `XGroupSetIdOptions`
func NewXGroupSetIdOptionsOptions() *XGroupSetIdOptions {
	return &XGroupSetIdOptions{-1}
}

// A value representing the number of stream entries already read by the group.
//
// Since Valkey version 7.0.0.
func (xgsio *XGroupSetIdOptions) SetEntriesRead(entriesRead int64) *XGroupSetIdOptions {
	xgsio.EntriesRead = entriesRead
	return xgsio
}

func (xgsio *XGroupSetIdOptions) ToArgs() ([]string, error) {
	var args []string

	if xgsio.EntriesRead > -1 {
		args = append(args, constants.EntriesReadKeyword, utils.IntToString(xgsio.EntriesRead))
	}

	return args, nil
}

// Optional arguments for `XClaim` in [StreamCommands]
type XClaimOptions struct {
	IdleTime     int64
	IdleUnixTime int64
	RetryCount   int64
	IsForce      bool
}

func NewXClaimOptions() *XClaimOptions {
	return &XClaimOptions{}
}

// Set the idle time in milliseconds.
func (xco *XClaimOptions) SetIdleTime(idleTime int64) *XClaimOptions {
	xco.IdleTime = idleTime
	return xco
}

// Set the idle time in unix-milliseconds.
func (xco *XClaimOptions) SetIdleUnixTime(idleUnixTime int64) *XClaimOptions {
	xco.IdleUnixTime = idleUnixTime
	return xco
}

// Set the retry count.
func (xco *XClaimOptions) SetRetryCount(retryCount int64) *XClaimOptions {
	xco.RetryCount = retryCount
	return xco
}

// Set the force flag.
func (xco *XClaimOptions) SetForce() *XClaimOptions {
	xco.IsForce = true
	return xco
}

func (sco *XClaimOptions) ToArgs() ([]string, error) {
	optionArgs := []string{}

	if sco.IdleTime > 0 {
		optionArgs = append(optionArgs, constants.IdleKeyword, utils.IntToString(sco.IdleTime))
	}

	if sco.IdleUnixTime > 0 {
		optionArgs = append(optionArgs, constants.TimeKeyword, utils.IntToString(sco.IdleUnixTime))
	}

	if sco.RetryCount > 0 {
		optionArgs = append(optionArgs, constants.RetryCountKeyword, utils.IntToString(sco.RetryCount))
	}

	if sco.IsForce {
		optionArgs = append(optionArgs, constants.ForceKeyword)
	}

	return optionArgs, nil
}

// Optional arguments for `XInfoStream` in [StreamCommands]
type XInfoStreamOptions struct {
	Count int64
}

// Create new empty `XInfoStreamOptions`
func NewXInfoStreamOptionsOptions() *XInfoStreamOptions {
	return &XInfoStreamOptions{-1}
}

// Request verbose information limiting the returned PEL entries.
// If `0` is specified, returns verbose information with no limit.
func (xiso *XInfoStreamOptions) SetCount(count int64) *XInfoStreamOptions {
	xiso.Count = count
	return xiso
}

func (xiso *XInfoStreamOptions) ToArgs() ([]string, error) {
	var args []string

	if xiso.Count > -1 {
		args = append(args, "COUNT", utils.IntToString(xiso.Count))
	}

	return args, nil
}

type StreamBoundary string

// Create a new stream boundary.
//
// Note: Exclusive ranges (`isInclusive=false`) have been added since Valkey 6.2.0.
func NewStreamBoundary(streamId string, isInclusive bool) StreamBoundary {
	if !isInclusive {
		return StreamBoundary("(" + streamId)
	}
	return StreamBoundary(streamId)
}

// Create a new stream boundary defined by an infinity.
func NewInfiniteStreamBoundary(bound constants.InfBoundary) StreamBoundary {
	return StreamBoundary(string(bound))
}

// Optional arguments for `XRange` and `XRevRange` in [StreamCommands]
type XRangeOptions struct {
	Count int64
}

func NewXRangeOptions() *XRangeOptions {
	return &XRangeOptions{-1}
}

// Set the count.
func (sro *XRangeOptions) SetCount(count int64) *XRangeOptions {
	sro.Count = count
	return sro
}

func (sro *XRangeOptions) ToArgs() ([]string, error) {
	var args []string

	if sro.Count >= 0 {
		args = append(args, constants.CountKeyword, utils.IntToString(sro.Count))
	}

	return args, nil
}
