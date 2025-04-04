// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"time"
)

type Engine struct {
	Language      string
	FunctionCount int64
	LibraryCount  int64
}

type RunningScript struct {
	Name     string
	Cmd      string
	Args     []string
	Duration time.Duration
}

type FunctionStatsResult struct {
	Engines       map[string]Engine
	RunningScript RunningScript
}
