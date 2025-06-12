// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import "github.com/valkey-io/valkey-glide/go/v2/constants"

// This struct represents the optional arguments for the HSCAN command.
type HashScanOptions struct {
	BaseScanOptions
	NoValues bool
}

func NewHashScanOptions() *HashScanOptions {
	return &HashScanOptions{}
}

// If this value is set to true, the HSCAN command will be called with NOVALUES option.
// In the NOVALUES option, values are not included in the response.
// Supported from Valkey 8.0.0 and above.
func (hashScanOptions *HashScanOptions) SetNoValues(noValues bool) *HashScanOptions {
	hashScanOptions.NoValues = noValues
	return hashScanOptions
}

func (hashScanOptions *HashScanOptions) SetMatch(match string) *HashScanOptions {
	hashScanOptions.BaseScanOptions.SetMatch(match)
	return hashScanOptions
}

func (hashScanOptions *HashScanOptions) SetCount(count int64) *HashScanOptions {
	hashScanOptions.BaseScanOptions.SetCount(count)
	return hashScanOptions
}

func (options *HashScanOptions) ToArgs() ([]string, error) {
	args := []string{}
	baseArgs, err := options.BaseScanOptions.ToArgs()
	args = append(args, baseArgs...)

	if options.NoValues {
		args = append(args, constants.NoValuesKeyword)
	}
	return args, err
}
