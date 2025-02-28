// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// The options used for performing a Cluster scan.
type ClusterScanOptions struct {
	BaseScanOptions
	scanType ObjectType
}

func NewClusterScanOptions() *ClusterScanOptions {
	return &ClusterScanOptions{}
}

func (scanOptions *ClusterScanOptions) SetMatch(m string) *ClusterScanOptions {
	scanOptions.BaseScanOptions.match = m
	return scanOptions
}

func (scanOptions *ClusterScanOptions) SetCount(c int64) *ClusterScanOptions {
	scanOptions.BaseScanOptions.count = c
	return scanOptions
}

func (scanOptions *ClusterScanOptions) SetType(t ObjectType) *ClusterScanOptions {
	scanOptions.scanType = t
	return scanOptions
}

func (opts *ClusterScanOptions) ToArgs() ([]string, error) {
	args := []string{}
	baseArgs, err := opts.BaseScanOptions.ToArgs()
	args = append(args, baseArgs...)

	if string(opts.scanType) != "" {
		args = append(args, TypeKeyword, string(opts.scanType))
	}

	return args, err
}
