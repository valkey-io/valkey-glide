// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// #include "../../lib.h"
import "C"

import (
	"unsafe"
)

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
func NewClusterScanCursorWithId(new_cursor string) *ClusterScanCursor {
	cStr := C.CString(new_cursor)
	C.new_cluster_cursor(cStr)
	defer C.free(unsafe.Pointer(cStr))
	return &ClusterScanCursor{cursor: new_cursor}
}

func (clusterScanCursor *ClusterScanCursor) GetCursor() string {
	return clusterScanCursor.cursor
}

func (clusterScanCursor *ClusterScanCursor) HasFinished() bool {
	return clusterScanCursor.cursor == FINISHED_SCAN_CURSOR
}
