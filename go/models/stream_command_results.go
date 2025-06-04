// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package models

// StreamEntry represents a single entry/element in a stream
type StreamEntry struct {
	// The unique identifier of the entry
	ID string
	// The fields associated with the entry
	Fields map[string]string
}

// StreamResponse represents 
type StreamResponse struct {
	// The name of the stream or the key of the stream
	StreamName string
	// The entries in the stream
	Entries []StreamEntry
}
