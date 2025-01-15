// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"strconv"
)

// Optional arguments to `XAdd` in [StreamCommands]
type XPendingOptions struct {
	minIdleTime int64
	start       string
	end         string
	count       int64
	consumer    string
}

// Create new empty `XPendingOptions`
func NewXPendingOptions(start string, end string, count int64) *XPendingOptions {
	options := &XPendingOptions{}
	options.start = start
	options.end = end
	options.count = count
	return options
}

func (xpo *XPendingOptions) SetMinIdleTime(minIdleTime int64) *XPendingOptions {
	xpo.minIdleTime = minIdleTime
	return xpo
}

func (xpo *XPendingOptions) SetConsumer(consumer string) *XPendingOptions {
	xpo.consumer = consumer
	return xpo
}

func (xpo *XPendingOptions) ToArgs() ([]string, error) {
	args := []string{}

	if xpo.minIdleTime > 0 {
		args = append(args, "IDLE")
		args = append(args, strconv.FormatInt(xpo.minIdleTime, 10))
	}

	args = append(args, xpo.start)
	args = append(args, xpo.end)
	args = append(args, strconv.FormatInt(xpo.count, 10))

	if xpo.consumer != "" {
		args = append(args, xpo.consumer)
	}

	return args, nil
}
