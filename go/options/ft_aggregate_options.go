// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// FtAggregateClause is implemented by all FT.AGGREGATE pipeline clauses.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.aggregate/
type FtAggregateClause interface {
	ftAggregateClauseArgs() []string
}

// FtAggregateLimit limits the number of retained records in the pipeline.
type FtAggregateLimit struct {
	Offset int
	Count  int
}

func (c *FtAggregateLimit) ftAggregateClauseArgs() []string {
	return []string{"LIMIT", strconv.Itoa(c.Offset), strconv.Itoa(c.Count)}
}

// FtAggregateFilter filters results using a predicate expression applied post-query.
type FtAggregateFilter struct {
	Expression string
}

func (c *FtAggregateFilter) ftAggregateClauseArgs() []string {
	return []string{"FILTER", c.Expression}
}

// FtAggregateReducer reduces matching results in a group using a reduction function.
type FtAggregateReducer struct {
	// Function is the reduction function name (e.g. "COUNT", "SUM", "TOLIST").
	Function string
	// Args are the arguments for the reducer function.
	Args []string
	// Name is the user-defined output property name. Optional.
	Name string
}

func (r *FtAggregateReducer) toArgs() []string {
	args := []string{"REDUCE", r.Function, strconv.Itoa(len(r.Args))}
	args = append(args, r.Args...)
	if r.Name != "" {
		args = append(args, "AS", r.Name)
	}
	return args
}

// FtAggregateGroupBy groups pipeline results by one or more properties.
type FtAggregateGroupBy struct {
	// Properties are the fields to group by (e.g. "@condition").
	Properties []string
	// Reducers are the aggregate functions applied to each group.
	Reducers []FtAggregateReducer
}

func (c *FtAggregateGroupBy) ftAggregateClauseArgs() []string {
	args := []string{"GROUPBY", strconv.Itoa(len(c.Properties))}
	args = append(args, c.Properties...)
	for _, r := range c.Reducers {
		args = append(args, r.toArgs()...)
	}
	return args
}

// FtAggregateSortProperty is a single sort property with direction for FtAggregateSortBy.
type FtAggregateSortProperty struct {
	Property string
	Order    constants.FtAggregateOrderBy
}

// FtAggregateSortBy sorts the pipeline by a list of properties.
type FtAggregateSortBy struct {
	// Properties are the fields and their sort directions.
	Properties []FtAggregateSortProperty
	// Max optimizes sorting by only sorting the n-largest elements. Optional.
	Max *int
}

func (c *FtAggregateSortBy) ftAggregateClauseArgs() []string {
	args := []string{"SORTBY", strconv.Itoa(len(c.Properties) * 2)}
	for _, p := range c.Properties {
		args = append(args, p.Property, string(p.Order))
	}
	if c.Max != nil {
		args = append(args, "MAX", strconv.Itoa(*c.Max))
	}
	return args
}

// FtAggregateApply applies a 1-to-1 transformation on properties and stores the result as a new property.
type FtAggregateApply struct {
	// Expression is the transformation expression.
	Expression string
	// Name is the output property name.
	Name string
}

func (c *FtAggregateApply) ftAggregateClauseArgs() []string {
	return []string{"APPLY", c.Expression, "AS", c.Name}
}

// FtAggregateParam is a key/value pair passed as a query parameter.
type FtAggregateParam struct {
	Key   string
	Value string
}

// FtAggregateOptions holds optional arguments for the FT.AGGREGATE command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.aggregate/
type FtAggregateOptions struct {
	// LoadAll loads all fields declared in the index. Mutually exclusive with LoadFields.
	LoadAll bool
	// LoadFields loads only the specified fields. Mutually exclusive with LoadAll.
	LoadFields []string
	// Timeout overrides the module timeout in milliseconds.
	Timeout *int
	// Params are key/value pairs referenced from within the query expression.
	Params []FtAggregateParam
	// Clauses are FILTER, LIMIT, GROUPBY, SORTBY, and APPLY clauses applied in order.
	Clauses []FtAggregateClause
	// Verbatim disables stemming on term searches.
	Verbatim bool
	// InOrder requires proximity matching of terms to be in order.
	InOrder bool
	// Slop sets the slop value for proximity matching.
	Slop *int
	// Dialect sets the query dialect version.
	Dialect *int
}

// ToArgs returns the command arguments for FtAggregateOptions.
func (o *FtAggregateOptions) ToArgs() ([]string, error) {
	if o.LoadAll && len(o.LoadFields) > 0 {
		return nil, errors.New("LoadAll and LoadFields are mutually exclusive")
	}
	args := []string{}
	if o.Verbatim {
		args = append(args, "VERBATIM")
	}
	if o.InOrder {
		args = append(args, "INORDER")
	}
	if o.Slop != nil {
		args = append(args, "SLOP", strconv.Itoa(*o.Slop))
	}
	if o.LoadAll {
		args = append(args, "LOAD", "*")
	} else if len(o.LoadFields) > 0 {
		args = append(args, "LOAD", strconv.Itoa(len(o.LoadFields)))
		args = append(args, o.LoadFields...)
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
	for _, clause := range o.Clauses {
		args = append(args, clause.ftAggregateClauseArgs()...)
	}
	if o.Dialect != nil {
		args = append(args, "DIALECT", strconv.Itoa(*o.Dialect))
	}
	return args, nil
}
