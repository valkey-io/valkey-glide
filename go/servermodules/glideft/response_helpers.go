// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"errors"
	"fmt"

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
// when SORTBY or WITHSORTKEYS is used.
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
		doc := models.FtSearchDocument{Key: key}

		if withSortKeys {
			pair, ok := val.([]any)
			if !ok || len(pair) < 2 {
				return models.FtSearchResult{}, fmt.Errorf(
					"expected [sortKey, fieldMap] for WITHSORTKEYS doc %q, got %T", key, val,
				)
			}
			if sk, ok := pair[0].(string); ok {
				doc.SortKey = sk
			}
			if fm, ok := pair[1].(map[string]any); ok {
				doc.Fields = fm
			} else {
				doc.Fields = map[string]any{}
			}
		} else {
			if fm, ok := val.(map[string]any); ok {
				doc.Fields = fm
			} else {
				doc.Fields = map[string]any{}
			}
		}

		docs = append(docs, doc)
	}

	sortDocuments(docs, opts, withSortKeys)
	return models.FtSearchResult{TotalResults: count, Documents: docs}, nil
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
