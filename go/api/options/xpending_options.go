// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import "strconv"

// Optional arguments to `XAdd` in [StreamCommands]
type XPendingOptions struct {
	minIdleTime int64
	start       string
	end         string
	count       int64
	consumer    string
}

// Create new empty `XPendingOptions`
func NewXPendingOptions() *XPendingOptions {
	return &XPendingOptions{}
}

func (xpo *XPendingOptions) SetMinIdleTime(minIdleTime int64) *XPendingOptions {
	xpo.minIdleTime = minIdleTime
	return xpo
}

func (xpo *XPendingOptions) SetRange(start string, end string, count int64) *XPendingOptions {
	xpo.start = start
	xpo.end = end
	xpo.count = count
	return xpo
}

func (xpo *XPendingOptions) ToArgs() ([]string, error) {
	args := []string{}

	if xpo.minIdleTime > 0 {
		args = append(args, "IDLE")
		args = append(args, "MINIDLETIME")
	}
	if xpo.start != "" && xpo.end != "" && xpo.count > 0 {
		args = append(args, xpo.start)
		args = append(args, xpo.end)
		args = append(args, strconv.FormatInt(xpo.count, 10))
	}
	if xpo.consumer != "" {
		args = append(args, xpo.consumer)
	}

	return args, nil
}
