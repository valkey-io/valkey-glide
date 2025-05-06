// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"time"

	"github.com/valkey-io/valkey-glide/go/api/options"
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

type FunctionListQuery struct {
	// The name of the library to query, use empty string for all libraries
	LibraryName string
	// Whether to include the code of the library
	WithCode bool
}

func (query FunctionListQuery) ToArgs() []string {
	args := []string{}
	if query.LibraryName != "" {
		args = append(args, options.LibraryNameKeyword, query.LibraryName)
	}
	if query.WithCode {
		args = append(args, options.WithCodeKeyword)
	}
	return args
}

type FunctionInfo struct {
	Name        string
	Description string
	Flags       []string
}

type LibraryInfo struct {
	Name      string
	Engine    string
	Functions []FunctionInfo
	Code      string
}
