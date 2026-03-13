// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// FtSearchReturnField specifies a field to return in FT.SEARCH results.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.search/
type FtSearchReturnField struct {
	// FieldIdentifier is the field name to return.
	FieldIdentifier string
	// Alias overrides the field name in the result. Optional.
	Alias string
}

// FtSearchLimit provides pagination for FT.SEARCH results.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.search/
type FtSearchLimit struct {
	// Offset is the number of results to skip.
	Offset int
	// Count is the number of results to return.
	Count int
}

// FtSearchParam is a key/value pair passed as a query parameter.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.search/
type FtSearchParam struct {
	Key   string
	Value string
}

// FtSearchShardScope controls which shards participate in an FT.SEARCH query.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.search/
type FtSearchShardScope string

const (
	// FtSearchShardScopeAllShards queries all shards (default).
	FtSearchShardScopeAllShards FtSearchShardScope = "ALLSHARDS"
	// FtSearchShardScopeSomeShards queries only a subset of shards.
	FtSearchShardScopeSomeShards FtSearchShardScope = "SOMESHARDS"
)

// FtSearchConsistencyMode controls consistency requirements for an FT.SEARCH query.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.search/
type FtSearchConsistencyMode string

const (
	// FtSearchConsistencyConsistent requires consistent results across shards.
	FtSearchConsistencyConsistent FtSearchConsistencyMode = "CONSISTENT"
	// FtSearchConsistencyInconsistent allows inconsistent (faster) results.
	FtSearchConsistencyInconsistent FtSearchConsistencyMode = "INCONSISTENT"
)

// FtSearchOptions holds optional arguments for the FT.SEARCH command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.search/
type FtSearchOptions struct {
	// ReturnFields specifies which fields to return. If empty, all fields are returned.
	ReturnFields []FtSearchReturnField
	// Timeout overrides the module timeout in milliseconds.
	Timeout *int
	// Params are key/value pairs referenced from within the query expression.
	Params []FtSearchParam
	// Limit provides pagination. Only keys satisfying offset and count are returned.
	Limit *FtSearchLimit
	// NoContent returns only document IDs without field content.
	NoContent bool
	// Dialect sets the query dialect version. The only supported dialect is 2.
	Dialect *int
	// Verbatim disables stemming on text terms in the query.
	Verbatim bool
	// InOrder requires proximity matching of text terms to be in order.
	InOrder bool
	// Slop sets the slop value for proximity matching of text terms.
	Slop *int
	// SortBy is the field name to sort results by.
	SortBy string
	// SortByOrder is the sort direction. Only used when SortBy is set.
	SortByOrder constants.FtSearchSortOrder
	// WithSortKeys augments output with the sort key value when SortBy is set.
	WithSortKeys bool
	// ShardScope controls shard participation in cluster mode.
	ShardScope FtSearchShardScope
	// Consistency controls consistency requirements in cluster mode.
	Consistency FtSearchConsistencyMode
}

// ToArgs returns the command arguments for FtSearchOptions.
func (o *FtSearchOptions) ToArgs() ([]string, error) {
	if o.WithSortKeys && o.SortBy == "" {
		return nil, errors.New("WithSortKeys requires SortBy to be set")
	}
	if o.SortByOrder != "" && o.SortBy == "" {
		return nil, errors.New("SortByOrder requires SortBy to be set")
	}
	args := []string{}
	if o.ShardScope != "" {
		args = append(args, string(o.ShardScope))
	}
	if o.Consistency != "" {
		args = append(args, string(o.Consistency))
	}
	if o.NoContent {
		args = append(args, "NOCONTENT")
	}
	if o.Verbatim {
		args = append(args, "VERBATIM")
	}
	if o.InOrder {
		args = append(args, "INORDER")
	}
	if o.Slop != nil {
		args = append(args, "SLOP", strconv.Itoa(*o.Slop))
	}
	if len(o.ReturnFields) > 0 {
		fieldArgs := []string{}
		for _, rf := range o.ReturnFields {
			fieldArgs = append(fieldArgs, rf.FieldIdentifier)
			if rf.Alias != "" {
				fieldArgs = append(fieldArgs, "AS", rf.Alias)
			}
		}
		args = append(args, "RETURN", strconv.Itoa(len(fieldArgs)))
		args = append(args, fieldArgs...)
	}
	if o.SortBy != "" {
		args = append(args, "SORTBY", o.SortBy)
		if o.SortByOrder != "" {
			args = append(args, string(o.SortByOrder))
		}
	}
	if o.WithSortKeys {
		args = append(args, "WITHSORTKEYS")
	}
	if o.Timeout != nil {
		args = append(args, "TIMEOUT", strconv.Itoa(*o.Timeout))
	}
	if len(o.Params) > 0 {
		args = append(args, "PARAMS", strconv.Itoa(len(o.Params)*2))
		for _, p := range o.Params {
			args = append(args, p.Key, p.Value)
		}
	}
	if o.Limit != nil {
		args = append(args, "LIMIT", strconv.Itoa(o.Limit.Offset), strconv.Itoa(o.Limit.Count))
	}
	if o.Dialect != nil {
		args = append(args, "DIALECT", strconv.Itoa(*o.Dialect))
	}
	return args, nil
}
