// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import "strconv"

const (
	jsonIndentKeyword  = "INDENT"
	jsonNewlineKeyword = "NEWLINE"
	jsonSpaceKeyword   = "SPACE"
)

// JsonGetOptions represents optional arguments for the JSON.GET command.
type JsonGetOptions struct {
	indent  *string
	newline *string
	space   *string
}

// NewJsonGetOptions creates a new empty JsonGetOptions.
func NewJsonGetOptions() *JsonGetOptions {
	return &JsonGetOptions{}
}

// SetIndent sets an indentation string for nested levels.
func (o *JsonGetOptions) SetIndent(indent string) *JsonGetOptions {
	o.indent = &indent
	return o
}

// SetNewline sets a string that's printed at the end of each line.
func (o *JsonGetOptions) SetNewline(newline string) *JsonGetOptions {
	o.newline = &newline
	return o
}

// SetSpace sets a string that's put between a key and a value.
func (o *JsonGetOptions) SetSpace(space string) *JsonGetOptions {
	o.space = &space
	return o
}

// ToArgs converts the options to a string slice for command arguments.
func (o *JsonGetOptions) ToArgs() []string {
	var args []string
	if o.indent != nil {
		args = append(args, jsonIndentKeyword, *o.indent)
	}
	if o.newline != nil {
		args = append(args, jsonNewlineKeyword, *o.newline)
	}
	if o.space != nil {
		args = append(args, jsonSpaceKeyword, *o.space)
	}
	return args
}

// JsonArrIndexOptions represents optional arguments for the JSON.ARRINDEX command.
type JsonArrIndexOptions struct {
	start *int64
	end   *int64
}

// NewJsonArrIndexOptions creates options with a start index (inclusive).
// Indices that exceed the array bounds are automatically adjusted.
func NewJsonArrIndexOptions(start int64) *JsonArrIndexOptions {
	return &JsonArrIndexOptions{start: &start}
}

// SetEnd sets the end index (exclusive).
// If start > end, the command returns -1 (not found).
func (o *JsonArrIndexOptions) SetEnd(end int64) *JsonArrIndexOptions {
	o.end = &end
	return o
}

// ToArgs converts the options to a string slice for command arguments.
func (o *JsonArrIndexOptions) ToArgs() []string {
	var args []string
	if o.start != nil {
		args = append(args, strconv.FormatInt(*o.start, 10))
		if o.end != nil {
			args = append(args, strconv.FormatInt(*o.end, 10))
		}
	}
	return args
}
