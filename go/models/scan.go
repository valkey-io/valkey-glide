// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package models

// #include "../lib.h"
import "C"

import (
	"unsafe"
)

type Cursor struct {
	cursor string
	new    bool
}

func NewCursor() Cursor {
	return Cursor{"0", true}
}

// A value of `"0"` indicates the start of the search.
// For Valkey 8.0 and above, negative cursors are treated like the initial cursor("0").
func NewCursorFromString(cursor string) Cursor {
	return Cursor{cursor, false}
}

func (cursor Cursor) IsFinished() bool {
	if cursor.new {
		return false
	}
	return cursor.cursor == "0"
}

func (cursor Cursor) String() string {
	return cursor.cursor
}

type ScanResult struct {
	Cursor Cursor
	Data   []string
}

var FINISHED_SCAN_CURSOR = "finished"

// This struct is used to keep track of the cursor of a cluster scan.
type ClusterScanCursor struct {
	cursor string
}

// Create a new ClusterScanCursor with a default value
func NewClusterScanCursor() ClusterScanCursor {
	return NewClusterScanCursorWithId("0")
}

// Create a new ClusterScanCursor with a specified value
func NewClusterScanCursorWithId(newCursor string) ClusterScanCursor {
	cStr := C.CString(newCursor)
	C.new_cluster_cursor(cStr)
	defer C.free(unsafe.Pointer(cStr))
	return ClusterScanCursor{cursor: newCursor}
}

// Returns the cursor ID associated with this cursor object.
func (clusterScanCursor ClusterScanCursor) GetCursor() string {
	return clusterScanCursor.cursor
}

// Checks to see if the cursor ID indicates the scan has finished.
func (clusterScanCursor ClusterScanCursor) IsFinished() bool {
	return clusterScanCursor.cursor == FINISHED_SCAN_CURSOR
}

type ClusterScanResult struct {
	Cursor ClusterScanCursor
	Keys   []string
}
