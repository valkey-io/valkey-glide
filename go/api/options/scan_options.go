// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

type ScanOptions struct {
	BaseScanOptions
	Type string
}

func NewScanOptions() *ScanOptions {
	return &ScanOptions{}
}

// SetMatch sets the match pattern for the SCAN command.
func (scanOptions *ScanOptions) SetMatch(match string) *ScanOptions {
	scanOptions.BaseScanOptions.SetMatch(match)
	return scanOptions
}

// SetCount sets the count of the SCAN command.
func (scanOptions *ScanOptions) SetCount(count int64) *ScanOptions {
	scanOptions.BaseScanOptions.SetCount(count)
	return scanOptions
}

func (opts *ScanOptions) ToArgs() ([]string, error) {
	args := []string{}
	baseArgs, err := opts.BaseScanOptions.ToArgs()

	if opts.Type != "" {
		args = append(args, opts.Type)
	}
	args = append(args, baseArgs...)

	return args, err
}
