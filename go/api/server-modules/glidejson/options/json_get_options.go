// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package options

const (
	indentKeyword   = "INDENT"   // ValKey API string to designate INDENT
	newlineKeyword  = "NEWLINE"  // ValKey API string to designate NEWLINE
	spaceKeyword    = "SPACE"    // ValKey API string to designate SPACE
	noescapeKeyword = "NOESCAPE" // ValKey API string to designate NOESCAPE
)

// This struct represents the optional arguments for the JSON.GET command.
type JsonGetOptions struct {
	paths    []string
	indent   string
	newline  string
	space    string
	noescape bool
}

func NewJsonGetOptionsBuilder() *JsonGetOptions {
	return &JsonGetOptions{}
}

// Sets the list of paths within the JSON document.
func (jsonGetOptions *JsonGetOptions) SetPaths(paths []string) *JsonGetOptions {
	jsonGetOptions.paths = paths
	return jsonGetOptions
}

// Sets an indentation string for nested levels.
func (jsonGetOptions *JsonGetOptions) SetIndent(indent string) *JsonGetOptions {
	jsonGetOptions.indent = indent
	return jsonGetOptions
}

// Sets a string that's printed at the end of each line.
func (jsonGetOptions *JsonGetOptions) SetNewline(newline string) *JsonGetOptions {
	jsonGetOptions.newline = newline
	return jsonGetOptions
}

// Sets a string that's put between a key and a value.
func (jsonGetOptions *JsonGetOptions) SetSpace(space string) *JsonGetOptions {
	jsonGetOptions.space = space
	return jsonGetOptions
}

// Allowed to be present for legacy compatibility and has no other effect.
func (jsonGetOptions *JsonGetOptions) SetNoescape(noescape bool) *JsonGetOptions {
	jsonGetOptions.noescape = noescape
	return jsonGetOptions
}

// Converts JsonGetOptions into a []string.
func (opts JsonGetOptions) ToArgs() ([]string, error) {
	args := []string{}
	var err error

	if opts.indent != "" {
		args = append(args, indentKeyword, opts.indent)
	}

	if opts.newline != "" {
		args = append(args, newlineKeyword, opts.newline)
	}

	if opts.space != "" {
		args = append(args, spaceKeyword, opts.space)
	}

	if opts.noescape {
		args = append(args, noescapeKeyword)
	}

	if len(opts.paths) > 0 {
		args = append(args, opts.paths...)
	}
	return args, err
}
