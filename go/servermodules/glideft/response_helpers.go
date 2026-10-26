// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"errors"
	"fmt"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func toStringResult(result any, err error) (string, error) {
	if err != nil {
		return models.DefaultStringResponse, err
	}
	if result == nil {
		return models.DefaultStringResponse, nil
	}
	s, ok := result.(string)
	if !ok {
		return models.DefaultStringResponse, fmt.Errorf("unexpected response type: %T, expected string", result)
	}
	return s, nil
}

func toStringSliceResult(result any, err error) ([]string, error) {
	if err != nil {
		return nil, err
	}
	if result == nil {
		return nil, nil
	}
	arr, ok := result.([]any)
	if !ok {
		return nil, fmt.Errorf("unexpected response type: %T, expected []any", result)
	}
	strs := make([]string, 0, len(arr))
	for _, v := range arr {
		s, ok := v.(string)
		if !ok {
			return nil, fmt.Errorf("unexpected element type: %T, expected string", v)
		}
		strs = append(strs, s)
	}
	return strs, nil
}

func toStringMapResult(result any, err error) (map[string]string, error) {
	if err != nil {
		return nil, err
	}
	if result == nil {
		return nil, nil
	}
	m, ok := result.(map[string]any)
	if !ok {
		return nil, fmt.Errorf("unexpected response type: %T, expected map[string]any", result)
	}
	strMap := make(map[string]string, len(m))
	for k, v := range m {
		s, ok := v.(string)
		if !ok {
			return nil, fmt.Errorf("unexpected map value type for key %q: %T, expected string", k, v)
		}
		strMap[k] = s
	}
	return strMap, nil
}

func toMapResult(result any, err error) (map[string]any, error) {
	if err != nil {
		return nil, err
	}
	if result == nil {
		return nil, nil
	}
	if m, ok := result.(map[string]any); ok {
		return m, nil
	}
	arr, ok := result.([]any)
	if !ok {
		return nil, fmt.Errorf("unexpected response type: %T, expected map or array", result)
	}
	return models.FlatArrayToMap(arr), nil
}

// parseFtSearchResponse parses the generic response from FT.SEARCH into an FtSearchResult.
// Go maps lose insertion order, so sortDocuments is called to restore the expected order
// when SORTBY or WITHSORTKEYS is used, or (SortBy empty) to restore relevance order by Score
// descending when WITHSCORES is used.
//
// Each document's value shape depends on which of WithScores/WithSortKeys were requested, per
// glide-core's value_conversion.rs reshaping (which this function only unwraps, never
// re-derives from a raw/un-reshaped reply):
//
//   - neither: plain field map
//   - WithSortKeys only: [sortKey, fieldMap]
//   - WithScores only: [score, fieldMap] (fieldMap empty under NOCONTENT)
//   - both, with content: [score, sortKey, fieldMap]
//   - both, with NOCONTENT: [score, fieldMap] -- the server drops the sort key entirely in
//     this specific combination, reverting to the WithScores-only shape; SortKey stays empty.
func parseFtSearchResponse(
	result any,
	err error,
	withSortKeys bool,
	opts *options.FtSearchOptions,
) (models.FtSearchResult, error) {
	if err != nil {
		return models.FtSearchResult{}, err
	}
	if result == nil {
		return models.FtSearchResult{}, nil
	}

	withScores := opts != nil && opts.WithScores

	arr, ok := result.([]any)
	if !ok {
		return models.FtSearchResult{}, fmt.Errorf("unexpected FT.SEARCH response type: %T, expected []any", result)
	}
	if len(arr) == 0 {
		return models.FtSearchResult{}, errors.New("unexpected FT.SEARCH response: empty array")
	}

	// Element [0]: total count
	count, ok := arr[0].(int64)
	if !ok {
		return models.FtSearchResult{}, fmt.Errorf("unexpected FT.SEARCH count type: %T, expected int64", arr[0])
	}

	// LIMIT 0 0: single-element array [count]
	if len(arr) == 1 {
		return models.FtSearchResult{TotalResults: count}, nil
	}

	// Element [1]: documents map
	docsMap, ok := arr[1].(map[string]any)
	if !ok {
		return models.FtSearchResult{}, fmt.Errorf("unexpected FT.SEARCH documents type: %T, expected map[string]any", arr[1])
	}

	docs := make([]models.FtSearchDocument, 0, len(docsMap))
	for key, val := range docsMap {
		doc, err := parseFtSearchDocumentValue(key, val, withScores, withSortKeys)
		if err != nil {
			return models.FtSearchResult{}, err
		}
		docs = append(docs, doc)
	}

	sortDocuments(docs, opts, withSortKeys)
	return models.FtSearchResult{TotalResults: count, Documents: docs}, nil
}

// parseFtSearchDocumentValue unwraps a single document's value from FT.SEARCH's reply, whose
// shape depends on withScores/withSortKeys per parseFtSearchResponse's docstring.
func parseFtSearchDocumentValue(key string, val any, withScores, withSortKeys bool) (models.FtSearchDocument, error) {
	doc := models.FtSearchDocument{Key: key}

	if !withScores && !withSortKeys {
		if fm, ok := val.(map[string]any); ok {
			doc.Fields = fm
		} else {
			doc.Fields = map[string]any{}
		}
		return doc, nil
	}

	tuple, ok := val.([]any)
	if !ok {
		return models.FtSearchDocument{}, fmt.Errorf(
			"expected a tuple-shaped value for doc %q (WithScores=%v, WithSortKeys=%v), got %T",
			key, withScores, withSortKeys, val,
		)
	}

	switch len(tuple) {
	case 2:
		// [sortKey, fieldMap] (WithSortKeys only), [score, fieldMap] (WithScores only), or
		// [score, fieldMap] (both, but NOCONTENT dropped the sort key -- indistinguishable from
		// the WithScores-only shape on the wire, so WithScores takes priority when both are set,
		// matching glide-core's own fallback in that specific combination).
		if withScores {
			score, err := parseFtScore(key, tuple[0])
			if err != nil {
				return models.FtSearchDocument{}, err
			}
			doc.Score = &score
		} else if sk, ok := tuple[0].(string); ok {
			doc.SortKey = sk
		}
		if fm, ok := tuple[1].(map[string]any); ok {
			doc.Fields = fm
		} else {
			doc.Fields = map[string]any{}
		}
	case 3:
		// [score, sortKey, fieldMap] (both, with content present)
		score, err := parseFtScore(key, tuple[0])
		if err != nil {
			return models.FtSearchDocument{}, err
		}
		doc.Score = &score
		if sk, ok := tuple[1].(string); ok {
			doc.SortKey = sk
		}
		if fm, ok := tuple[2].(map[string]any); ok {
			doc.Fields = fm
		} else {
			doc.Fields = map[string]any{}
		}
	default:
		return models.FtSearchDocument{}, fmt.Errorf(
			"unexpected tuple length %d for doc %q (WithScores=%v, WithSortKeys=%v)",
			len(tuple), key, withScores, withSortKeys,
		)
	}
	return doc, nil
}

// parseFtScore parses a WITHSCORES score value. glide-core passes the score through as the
// bulk-string decimal text valkey-search returns (e.g. "0.287682086229"), not a float -- this is
// the one place that text is parsed into a Go float64.
func parseFtScore(key string, v any) (float64, error) {
	s, ok := v.(string)
	if !ok {
		return 0, fmt.Errorf("unexpected score type for doc %q: %T, expected string", key, v)
	}
	score, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return 0, fmt.Errorf("unparseable score %q for doc %q: %w", s, key, err)
	}
	return score, nil
}

// parseFtAggregateResponse parses the generic response from FT.AGGREGATE.
func parseFtAggregateResponse(result any, err error) ([]map[string]any, error) {
	if err != nil {
		return nil, err
	}
	if result == nil {
		return nil, nil
	}

	arr, ok := result.([]any)
	if !ok {
		return nil, fmt.Errorf("unexpected FT.AGGREGATE response type: %T, expected []any", result)
	}

	if len(arr) == 0 {
		return nil, nil
	}

	results := make([]map[string]any, 0, len(arr))
	for _, row := range arr {
		m, ok := row.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("unexpected FT.AGGREGATE row type: %T", row)
		}
		results = append(results, m)
	}
	return results, nil
}

// parseCursorWrappedAggregateResponse parses the WITHCURSOR reply shape shared by
// FT.AGGREGATE/FT.HYBRID (when opts.WithCursor is set) and FT.CURSOR READ: a two-element array
// of [rows, cursorId], where rows is the same per-document mapping shape
// parseFtAggregateResponse unwraps, and cursorId is 0 once the cursor is exhausted.
func parseCursorWrappedAggregateResponse(result any, err error) ([]map[string]any, int64, error) {
	if err != nil {
		return nil, 0, err
	}
	if result == nil {
		return nil, 0, errors.New("unexpected WITHCURSOR response: nil")
	}

	arr, ok := result.([]any)
	if !ok {
		return nil, 0, fmt.Errorf("unexpected WITHCURSOR response type: %T, expected []any", result)
	}
	if len(arr) != 2 {
		return nil, 0, fmt.Errorf(
			"unexpected WITHCURSOR response: expected a 2-element [rows, cursorId] array, got %d elements",
			len(arr),
		)
	}

	cursorID, ok := arr[1].(int64)
	if !ok {
		return nil, 0, fmt.Errorf("unexpected WITHCURSOR cursorId type: %T, expected int64", arr[1])
	}

	rows, err := parseFtAggregateResponse(arr[0], nil)
	if err != nil {
		return nil, 0, err
	}
	return rows, cursorID, nil
}
