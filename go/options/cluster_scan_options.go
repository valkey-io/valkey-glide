// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// The options used for performing a Cluster scan.
type ClusterScanOptions struct {
	BaseScanOptions
	ScanType             constants.ObjectType
	AllowNonCoveredSlots bool
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

// SetAllowNonCoveredSlots sets whether to allow scanning even if some slots are not covered by any node.
// If set to true, the scan will perform even if some slots are not covered by any node in the cluster.
// This is useful when the cluster is not fully configured or some nodes are down.
func (scanOptions *ClusterScanOptions) SetAllowNonCoveredSlots(allow bool) *ClusterScanOptions {
	scanOptions.AllowNonCoveredSlots = allow
	return scanOptions
}

func (opts *ClusterScanOptions) ToArgs() ([]string, error) {
	args := []string{}
	baseArgs, err := opts.BaseScanOptions.ToArgs()
	args = append(args, baseArgs...)

	if string(opts.ScanType) != "" {
		args = append(args, constants.TypeKeyword, string(opts.ScanType))
	}

	if opts.AllowNonCoveredSlots {
		args = append(args, "ALLOW_NON_COVERED_SLOTS")
	}

	return args, err
}
