// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package models

// StreamEntry represents a single entry/element in a stream
type StreamEntry struct {
	// The unique identifier of the entry
	ID string
	// The fields associated with the entry
	Fields []FieldInfo
}

// FieldInfo represents the Field-value pairs added to the entry
type FieldInfo struct {
	// The name of the field
	FieldName string
	// The value of the field
	Value string
}

// StreamResponse represents a stream with its entries
type StreamResponse struct {
	// The entries in the stream
	Entries []StreamEntry
}

// XClaimResponse represents a claimed entry in a stream
type XClaimResponse struct {
	// The fields associated with the claimed entry
	Fields map[string]string
}

// XInfoStreamResponse represents the information about a stream
type XInfoStreamResponse struct {
	// The number of entries in the stream
	Length int64
	// The number of keys in the underlying radix data structure
	RadixTreeKeys int64
	// The number of nodes in the underlying radix data structure
	RadixTreeNodes int64
	// The number of consumer groups defined for the stream
	Groups int64
	// The ID of the least-recently entry that was added to the stream
	LastGeneratedID string
	// The maximal entry ID that was deleted from the stream
	MaxDeletedEntryID string
	// The count of all entries added to the stream during its lifetime
	EntriesAdded int64
	// The ID and field-value tuples of the first entry in the stream
	FirstEntry StreamEntry
	// The ID and field-value tuples of the last entry in the stream
	LastEntry StreamEntry
}
