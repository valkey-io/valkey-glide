// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

type ScanOptions struct {
	BaseScanOptions
	Type ObjectType
}

func NewScanOptions() *ScanOptions {
	return &ScanOptions{}
}

// SetMatch sets the match pattern for the SCAN command.
// It is possible to only iterate elements matching a given glob-style pattern,
// similarly to the behavior of the KEYS command that takes a pattern as its only argument.
func (scanOptions *ScanOptions) SetMatch(match string) *ScanOptions {
	scanOptions.BaseScanOptions.SetMatch(match)
	return scanOptions
}

// SetCount sets the count of the SCAN command.
// Basically with COUNT the user specifies the amount of work that
// should be done at every call in order to retrieve elements from the collection.
func (scanOptions *ScanOptions) SetCount(count int64) *ScanOptions {
	scanOptions.BaseScanOptions.SetCount(count)
	return scanOptions
}

// Set TYPE(string, list, set, zset, hash and stream)sets the type of the SCAN command.
// You can use the Type option to ask SCAN to only return objects that match a given type,
// allowing you to iterate through the database looking for keys of a specific type.
func (scanOptions *ScanOptions) SetType(typeOpts ObjectType) *ScanOptions {
	scanOptions.Type = typeOpts
	return scanOptions
}

func (opts *ScanOptions) ToArgs() ([]string, error) {
	args := []string{}
	baseArgs, err := opts.BaseScanOptions.ToArgs()
	if opts.Type != "" {
		args = append(args, TypeKeyword, string(opts.Type))
	}
	args = append(args, baseArgs...)
	return args, err
}
