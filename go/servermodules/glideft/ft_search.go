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

// isPureVectorKnnQuery reports whether query is a KNN vector query with no sibling text/tag/
// numeric pre-filter -- the one case (confirmed against valkey-search's
// query::SearchParameters::PreParseQueryString, search.cc) where the server's own Neighbor.score
// is a KNN distance (lower is closer) rather than a BM25-style relevance score (higher is
// better), and where the server keeps the reply in ascending (closest-first) order instead of
// resorting it by descending relevance the way a hybrid `text=>[KNN ...]` query is. Mirrors the
// server's own FindVectorDelimiter (search.cc, confirmed as of commit 51db89d, 1.3.0-rc2) byte
// for byte: scans for "=>" followed by optional whitespace then "[", skipping a QMA-style
// "=> {" block or a literal "=>" inside free text that isn't followed by "[", continuing the
// scan rather than matching on the first occurrence blindly. FindVectorDelimiter today does
// this scan with no awareness of tag braces, quoted strings, or escape sequences -- a `=>[`
// sequence inside any of those is still read as the real delimiter server-side, so this
// function matching that exactly is correct against today's server. If FindVectorDelimiter is
// later changed to skip `=>[` inside braces/quotes/escapes, this function must change to match
// it, or query strings containing an escaped/quoted/braced `=>[` will silently pick the wrong
// sort direction. A pre-filter (everything before the delimiter) that is empty or exactly "*"
// (match-all, optionally surrounded by whitespace) means no text/tag/numeric predicate competes
// with the vector clause.
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

// knnClauseRegexp matches a whole `[KNN <k> @<field> ...]` clause body (the bracket interior),
// so the optional trailing parameters (EF_RUNTIME, HYBRID_POLICY, AS -- any order, per
// docs/topics/search-query.md's `[ KNN <K> @<field> $<parameter> [EF_RUNTIME <ef-value>]
// [HYBRID_POLICY <policy>] [AS <name>] ]` grammar) can be searched for an AS token separately,
// rather than assuming AS is the very next token after the field reference.
var knnClauseRegexp = regexp.MustCompile(`(?i)KNN\s+\d+\s+@(\w+)\s+\$\w+([^\]]*)\]`)

// knnAsAliasRegexp matches a bare `AS <name>` token within a KNN clause's optional-parameter
// tail (knnClauseRegexp's second capture group), case-insensitively, the way the server's own
// MatchInsensitive("AS") token scan does.
var knnAsAliasRegexp = regexp.MustCompile(`(?i)\bAS\s+(\w+)\b`)

// knnDistanceFieldName returns the Fields key holding a pure-vector KNN query's own distance
// value. Per docs/topics/search-query.md: the server reports it under the query's own `AS
// <name>` if the KNN clause carries one, or under the default `__<field>_score` otherwise --
// and in the default case, the caller's own RETURN ... AS alias for that identifier (see
// options.FtSearchReturnField) renames it again, same as any other returned field. An AS
// inside the KNN clause itself already is the name the server sends on the wire, so a RETURN
// remap for the default `__<field>_score` identifier does not apply once the query supplies its
// own AS -- the default identifier doesn't exist on the wire to remap. Returns "" if query has
// no KNN clause to extract a field from (callers only reach this when isPureVectorKnnQuery(query)
// is already true, so this should not normally happen).
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
//
// When SortBy is empty but WithScores is set, the server's own relevance order is what Python
// callers see (dict insertion order is preserved there). Go's map-based Documents loses that
// order, so it's restored here by sorting on Score, with the document key as a deterministic
// tiebreak (the server's own tie-break for an equivalent case, search.cc, is also the key,
// ascending). For a pure-vector KNN query (see isPureVectorKnnQuery), WITHSCORES itself always
// reports 0 (ft_search.cc: `has_relevance ? neighbor.score : 0.0f`, confirmed by the server's
// own pure_vector_with_scores_reports_zero test) rather than the KNN distance, so sorting on
// Score would compare equal across every document and fall through to the key tiebreak --
// silently replacing the server's nearest-first order with key order. The real distance is
// reported separately via the `score_as` field (knnDistanceFieldName: `__<field>_score`, or its
// RETURN ... AS alias), so this branch sorts on that field instead, ascending (lower distance is
// closer). Every other case (BM25 text relevance, or a hybrid text+KNN query, which the server
// itself re-ranks to relevance descending) sorts descending on the real Score, best match
// first. This only applies when SortBy is empty: once SortBy and WithScores combine, the
// existing port rule below keeps sorting by SortBy/SortKey and never falls back to Score.
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
