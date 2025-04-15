// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"time"
)

type Engine struct {
	// The language of the engine (e.g. "LUA")
	Language string
	// The number of functions loaded in this engine
	FunctionCount int64
	// The number of libraries loaded in this engine
	LibraryCount int64
}

type RunningScript struct {
	// The name of the running script
	Name string
	// The command being executed
	Cmd string
	// The arguments passed to the command
	Args []string
	// The duration the script has been running
	Duration time.Duration
}

type FunctionStatsResult struct {
	// Map of engine name to engine statistics
	Engines map[string]Engine
	// Information about the currently running script, if any
	RunningScript RunningScript
}
