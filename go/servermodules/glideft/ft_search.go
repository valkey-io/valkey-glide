// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"
	"regexp"
	"sort"
	"strconv"
	"strings"

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
	return parseFtSearchResponse(result, execErr, withSortKeys, opts, query)
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
	return parseFtSearchResponse(result, execErr, withSortKeys, opts, query)
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

func isPureVectorKnnQuery(query string) bool {
	delimiterStart := -1
	searchFrom := 0

	for {
		idx := strings.Index(query[searchFrom:], "=>")
		if idx < 0 {
			break
		}

		absIdx := searchFrom + idx
		after := absIdx + len("=>")
		for after < len(query) && (query[after] == ' ' || query[after] == '\t' ||
			query[after] == '\n' || query[after] == '\r') {
			after++
		}

		if after < len(query) && query[after] == '[' {
			delimiterStart = absIdx
			break
		}

		searchFrom = absIdx + len("=>")
	}

	if delimiterStart < 0 {
		return false
	}

	preFilter := strings.TrimSpace(query[:delimiterStart])
	return preFilter == "" || preFilter == "*"
}

var knnClauseRegexp = regexp.MustCompile(`(?i)KNN\s+\d+\s+@(\w+)\s+\$\w+([^\]]*)\]`)

var knnAsAliasRegexp = regexp.MustCompile(`(?i)\bAS\s+(\w+)\b`)

func knnDistanceFieldName(query string, opts *options.FtSearchOptions) string {
	m := knnClauseRegexp.FindStringSubmatch(query)
	if m == nil {
		return ""
	}
	if as := knnAsAliasRegexp.FindStringSubmatch(m[2]); as != nil {
		return as[1]
	}
	defaultName := "__" + m[1] + "_score"
	if opts == nil {
		return defaultName
	}
	for _, rf := range opts.ReturnFields {
		if rf.FieldIdentifier == defaultName && rf.Alias != "" {
			return rf.Alias
		}
	}
	return defaultName
}

// TODO (#5853): We should find a way to NOT have to do it client-side. The current
// limitations are that we only have access to CustomCommand and we are forced to not
// "leak" out of the glideft folder which limits our ability to parse from CustomCommand.
// It's a bigger design question that should be considered.
//
// sortDocuments re-sorts the documents slice to match the server's SORTBY order.
// Go maps lose insertion order, so we reconstruct the sort using the sort field
// value from each document's Fields map, or the SortKey when WITHSORTKEYS is used.
func sortDocuments(docs []models.FtSearchDocument, opts *options.FtSearchOptions, withSortKeys bool, query string) {
	if len(docs) <= 1 {
		return
	}
	if opts == nil || opts.SortBy == "" {
		if opts != nil && opts.WithScores {
			if isPureVectorKnnQuery(query) {
				distanceField := knnDistanceFieldName(query, opts)
				sort.SliceStable(docs, func(i, j int) bool {
					di, dj := fieldAsString(docs[i].Fields, distanceField), fieldAsString(docs[j].Fields, distanceField)
					if di != dj {
						diNum, diErr := strconv.ParseFloat(di, 64)
						djNum, djErr := strconv.ParseFloat(dj, 64)
						if diErr == nil && djErr == nil {
							return diNum < djNum
						}
						return di < dj
					}
					return docs[i].Key < docs[j].Key
				})
				return
			}
			sort.SliceStable(docs, func(i, j int) bool {
				si, sj := docs[i].Score, docs[j].Score
				if si == nil || sj == nil {
					return false
				}
				if *si != *sj {
					return *si > *sj
				}
				return docs[i].Key < docs[j].Key
			})
		}
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
