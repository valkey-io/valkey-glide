// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package models

import "strconv"

// FlatArrayToMap converts a flat [k1, v1, k2, v2, ...] array into a map.
// Non-string keys are converted to their positional index.
func FlatArrayToMap(arr []any) map[string]any {
	m := make(map[string]any, len(arr)/2)
	for i := 0; i+1 < len(arr); i += 2 {
		key, ok := arr[i].(string)
		if !ok {
			key = strconv.Itoa(i)
		}
		m[key] = arr[i+1]
	}
	return m
}

// FtSearchDocument represents a single document returned by FT.SEARCH.
// The order of documents in [FtSearchResult.Documents] matches the order
// returned by the server, which is significant when SORTBY is used.
type FtSearchDocument struct {
	// Key is the document key (e.g. the Redis/Valkey key name).
	Key string
	// Fields contains the document's field data as key-value pairs.
	// Empty when NOCONTENT is used.
	Fields map[string]any
	// SortKey is the sort key value returned when WITHSORTKEYS is used.
	// Empty string when WITHSORTKEYS is not requested.
	SortKey string
}

// FtSearchResult holds the parsed response from an FT.SEARCH command.
//
// Documents are returned as an ordered slice that preserves the server's
// iteration order, which matters when SORTBY is specified.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.search/
type FtSearchResult struct {
	// TotalResults is the total number of documents matching the query.
	TotalResults int64
	// Documents is an ordered slice of search result documents.
	// The slice preserves the order returned by the server.
	Documents []FtSearchDocument
}
