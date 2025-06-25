// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// The options used for performing a Cluster scan.
type ClusterScanOptions struct {
	BaseScanOptions
	ScanType constants.ObjectType
}

// Creates a options struct to be used in the Cluster Scan.
func NewClusterScanOptions() *ClusterScanOptions {
	return &ClusterScanOptions{}
}

// SetMatch sets the match pattern for Cluster Scan command.
func (scanOptions *ClusterScanOptions) SetMatch(m string) *ClusterScanOptions {
	scanOptions.BaseScanOptions.Match = m
	return scanOptions
}

// SetCount sets the count for the Cluster Scan command.
func (scanOptions *ClusterScanOptions) SetCount(c int64) *ClusterScanOptions {
	scanOptions.BaseScanOptions.Count = c
	return scanOptions
}

// SetType sets the type to look for during the Cluster Scan.
func (scanOptions *ClusterScanOptions) SetType(t constants.ObjectType) *ClusterScanOptions {
	scanOptions.ScanType = t
	return scanOptions
}

func (opts *ClusterScanOptions) ToArgs() ([]string, error) {
	args := []string{}
	baseArgs, err := opts.BaseScanOptions.ToArgs()
	args = append(args, baseArgs...)

	if string(opts.ScanType) != "" {
		args = append(args, constants.TypeKeyword, string(opts.ScanType))
	}

	return args, err
}
