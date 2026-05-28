// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"
	"sort"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// FtSearch executes a search query against an index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index to search.
//	query     - The search query string.
//	opts      - Optional search parameters. Pass nil to use defaults.
//
// Return value:
//
//	An [models.FtSearchResult] containing the total count and a slice
//	of [models.FtSearchDocument].
//
// [valkey.io]: https://valkey.io/commands/ft.search/
func FtSearch(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	query string,
	opts *options.FtSearchOptions,
) (models.FtSearchResult, error) {
	args, withSortKeys, err := buildSearchArgs(indexName, query, opts)
	if err != nil {
		return models.FtSearchResult{}, err
	}
	result, execErr := execStandalone(client, ctx, args)
	return parseFtSearchResponse(result, execErr, withSortKeys, opts)
}

// ClusterFtSearch is the cluster variant of [FtSearch].
func ClusterFtSearch(
	ctx context.Context,
	client clusterClient,
	indexName string,
	query string,
	opts *options.FtSearchOptions,
) (models.FtSearchResult, error) {
	args, withSortKeys, err := buildSearchArgs(indexName, query, opts)
	if err != nil {
		return models.FtSearchResult{}, err
	}
	result, execErr := execCluster(client, ctx, args)
	return parseFtSearchResponse(result, execErr, withSortKeys, opts)
}

func buildSearchArgs(indexName, query string, opts *options.FtSearchOptions) ([]string, bool, error) {
	args := []string{ftSearchCommand, indexName, query}

	withSortKeys := false
	if opts != nil {
		withSortKeys = opts.WithSortKeys
		optArgs, err := opts.ToArgs()
		if err != nil {
			return nil, false, err
		}
		args = append(args, optArgs...)
	}

	return args, withSortKeys, nil
}

// TODO (#5853): We should find a way to NOT have to do it client-side. The current
// limitations are that we only have access to CustomCommand and we are forced to not
// "leak" out of the glideft folder which limits our ability to parse from CustomCommand.
// It's a bigger design question that should be considered.
//
// sortDocuments re-sorts the documents slice to match the server's SORTBY order.
// Go maps lose insertion order, so we reconstruct the sort using the sort field
// value from each document's Fields map, or the SortKey when WITHSORTKEYS is used.
func sortDocuments(docs []models.FtSearchDocument, opts *options.FtSearchOptions, withSortKeys bool) {
	if len(docs) <= 1 {
		return
	}
	if opts == nil || opts.SortBy == "" {
		return
	}

	// When WITHSORTKEYS is not set, sorting relies on the sort field being present
	// in each document's Fields map. If the sort field was excluded via RETURN, the
	// values will all be empty strings and the original (arbitrary map iteration)
	// order is preserved by SliceStable — which is the best we can do without
	// server-side ordering guarantees.
	desc := opts.SortByOrder == constants.FtSearchSortOrderDesc

	sort.SliceStable(docs, func(i, j int) bool {
		var a, b string
		if withSortKeys {
			a = docs[i].SortKey
			b = docs[j].SortKey
		} else {
			a = fieldAsString(docs[i].Fields, opts.SortBy)
			b = fieldAsString(docs[j].Fields, opts.SortBy)
		}

		// Try numeric comparison first
		aNum, aErr := strconv.ParseFloat(a, 64)
		bNum, bErr := strconv.ParseFloat(b, 64)
		if aErr == nil && bErr == nil {
			if desc {
				return aNum > bNum
			}
			return aNum < bNum
		}

		// Fall back to string comparison
		if desc {
			return a > b
		}
		return a < b
	})
}

func fieldAsString(fields map[string]any, key string) string {
	if fields == nil {
		return models.DefaultStringResponse
	}
	v, ok := fields[key]
	if !ok {
		return models.DefaultStringResponse
	}
	switch val := v.(type) {
	case string:
		return val
	case int64:
		return strconv.FormatInt(val, 10)
	case float64:
		return strconv.FormatFloat(val, 'f', -1, 64)
	default:
		return models.DefaultStringResponse
	}
}
