// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package models

// StreamEntry represents a single entry/element in a stream
type StreamEntry struct {
	// The unique identifier of the entry
	ID string
	// The fields associated with the entry
	Fields map[string]string
}

// StreamResponse represents a stream with its entries
type StreamResponse struct {
	// The entries in the stream
	Entries []StreamEntry
}
