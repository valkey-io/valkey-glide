// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// #include "../../lib.h"
import "C"

import "unsafe"

var FINISHED_SCAN_CURSOR = "finished"

// This struct is used to keep track of the cursor of a cluster scan.
type ClusterScanCursor struct {
	cursor string
}

// Create a new ClusterScanCursor with a default value
func NewClusterScanCursor() *ClusterScanCursor {
	cStr := C.CString("0")
	C.new_cluster_cursor(cStr)
	defer C.free(unsafe.Pointer(cStr))
	return &ClusterScanCursor{cursor: "0"}
}

// Create a new ClusterScanCursor with a specified value
func NewClusterScanCursorWithId(newCursor string) *ClusterScanCursor {
	cStr := C.CString(newCursor)
	C.new_cluster_cursor(cStr)
	defer C.free(unsafe.Pointer(cStr))
	return &ClusterScanCursor{cursor: newCursor}
}

// Returns the cursor ID associated with this cursor object.
func (clusterScanCursor *ClusterScanCursor) GetCursor() string {
	return clusterScanCursor.cursor
}

// Checks to see if the cursor ID indicates the scan has finished.
func (clusterScanCursor *ClusterScanCursor) HasFinished() bool {
	return clusterScanCursor.cursor == FINISHED_SCAN_CURSOR
}

// The options used for performing a Cluster scan.
type ClusterScanOptions struct {
	BaseScanOptions
	scanType ObjectType
}

// Creates a options struct to be used in the Cluster Scan.
func NewClusterScanOptions() *ClusterScanOptions {
	return &ClusterScanOptions{}
}

// SetMatch sets the match pattern for Cluster Scan command.
func (scanOptions *ClusterScanOptions) SetMatch(m string) *ClusterScanOptions {
	scanOptions.BaseScanOptions.match = m
	return scanOptions
}

// SetCount sets the count for the Cluster Scan command.
func (scanOptions *ClusterScanOptions) SetCount(c int64) *ClusterScanOptions {
	scanOptions.BaseScanOptions.count = c
	return scanOptions
}

// SetType sets the type to look for during the Cluster Scan.
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
