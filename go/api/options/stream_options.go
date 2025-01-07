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

// New entry will be added with this `id“.
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
	var err error
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
	return args, err
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
func (xto *XTrimOptions) SetExactTrimming() *XTrimOptions {
	xto.exact = triStateBoolTrue
	return xto
}

// Trim in a near-exact manner, which is more efficient.
func (xto *XTrimOptions) SetNearlyExactTrimming() *XTrimOptions {
	xto.exact = triStateBoolFalse
	return xto
}

// Max number of stream entries to be trimmed for non-exact match.
func (xto *XTrimOptions) SetNearlyExactTrimmingAndLimit(limit int64) *XTrimOptions {
	xto.exact = triStateBoolFalse
	xto.limit = limit
	return xto
}

func (xto *XTrimOptions) ToArgs() ([]string, error) {
	args := []string{}
	args = append(args, xto.method)
	if xto.exact == triStateBoolTrue {
		args = append(args, "=")
	} else if xto.exact == triStateBoolFalse {
		args = append(args, "~")
	}
	args = append(args, xto.threshold)
	if xto.limit > 0 {
		args = append(args, "LIMIT", utils.IntToString(xto.limit))
	}
	var err error
	return args, err
}

// Optional arguments for `XClaim` in [StreamCommands]
type StreamClaimOptions struct {
	idleTime     int64
	idleUnixTime int64
	retryCount   int64
	isForce      bool
}

func NewStreamClaimOptions() *StreamClaimOptions {
	return &StreamClaimOptions{}
}

// Set the idle time in milliseconds.
func (sco *StreamClaimOptions) SetIdleTime(idleTime int64) *StreamClaimOptions {
	sco.idleTime = idleTime
	return sco
}

// Set the idle time in unix-milliseconds.
func (sco *StreamClaimOptions) SetIdleUnixTime(idleUnixTime int64) *StreamClaimOptions {
	sco.idleUnixTime = idleUnixTime
	return sco
}

// Set the retry count.
func (sco *StreamClaimOptions) SetRetryCount(retryCount int64) *StreamClaimOptions {
	sco.retryCount = retryCount
	return sco
}

// Valkey API keywords for stream claim options
const (
	// ValKey API string to designate IDLE time in milliseconds
	IDLE_VALKEY_API string = "IDLE"
	// ValKey API string to designate TIME time in unix-milliseconds
	TIME_VALKEY_API string = "TIME"
	// ValKey API string to designate RETRYCOUNT
	RETRY_COUNT_VALKEY_API string = "RETRYCOUNT"
	// ValKey API string to designate FORCE
	FORCE_VALKEY_API string = "FORCE"
	// ValKey API string to designate JUSTID
	JUST_ID_VALKEY_API string = "JUSTID"
)

func (sco *StreamClaimOptions) ToArgs() ([]string, error) {
	optionArgs := []string{}

	if sco.idleTime > 0 {
		optionArgs = append(optionArgs, IDLE_VALKEY_API, utils.IntToString(sco.idleTime))
	}

	if sco.idleUnixTime > 0 {
		optionArgs = append(optionArgs, TIME_VALKEY_API, utils.IntToString(sco.idleUnixTime))
	}

	if sco.retryCount > 0 {
		optionArgs = append(optionArgs, RETRY_COUNT_VALKEY_API, utils.IntToString(sco.retryCount))
	}

	if sco.isForce {
		optionArgs = append(optionArgs, FORCE_VALKEY_API)
	}

	return optionArgs, nil
}
