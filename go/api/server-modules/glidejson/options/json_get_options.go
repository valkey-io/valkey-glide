// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package options

const (
	indentKeyword   = "INDENT"
	newlineKeyword  = "NEWLINE"
	spaceKeyword    = "SPACE"
	noescapeKeyword = "NOESCAPE"
)

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

func (jsonGetOptions *JsonGetOptions) SetPaths(paths []string) *JsonGetOptions {
	jsonGetOptions.paths = paths
	return jsonGetOptions
}

func (jsonGetOptions *JsonGetOptions) SetIndent(indent string) *JsonGetOptions {
	jsonGetOptions.indent = indent
	return jsonGetOptions
}

func (jsonGetOptions *JsonGetOptions) SetNewline(newline string) *JsonGetOptions {
	jsonGetOptions.newline = newline
	return jsonGetOptions
}

func (jsonGetOptions *JsonGetOptions) SetSpace(space string) *JsonGetOptions {
	jsonGetOptions.space = space
	return jsonGetOptions
}

func (jsonGetOptions *JsonGetOptions) SetNoescape(noescape bool) *JsonGetOptions {
	jsonGetOptions.noescape = noescape
	return jsonGetOptions
}

func (opts *JsonGetOptions) ToArgs() ([]string, error) {
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
