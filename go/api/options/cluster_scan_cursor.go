// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// #include "../../lib.h"
import "C"

import (
	"unsafe"
)

var FINISHED_SCAN_CURSOR = "finished"

// TODO: Clean this up into a better file (currently has a problem where golang
// treates the C type differently)
type ClusterScanCursor struct {
	cursor string
}

// Create a new ClusterScanCursor
func NewClusterScanCursor(new_cursor string) *ClusterScanCursor {
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
