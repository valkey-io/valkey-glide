// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

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
