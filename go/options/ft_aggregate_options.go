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

// NewToListReducer creates a TOLIST reducer, which collects all values for field across the
// records in a group into a single array, removing duplicates and nil values. If a value is
// itself an array, it is flattened one level before being added. Values are returned in the
// order they were first encountered.
func NewToListReducer(field string, name string) FtAggregateReducer {
	return FtAggregateReducer{Function: "TOLIST", Args: []string{field}, Name: name}
}

// FtAggregateFirstValueOptions holds the optional By/Order parameters for NewFirstValueReducer.
type FtAggregateFirstValueOptions struct {
	// By is the property used to choose which record's value of Field is returned. Required if
	// Order is set.
	By string
	// Order is the direction used to pick the winning record when By is set.
	Order constants.FtAggregateOrderBy
}

// NewFirstValueReducer creates a FIRST_VALUE reducer, which returns the value of field from a
// single record in the group. Without options.By, the first record encountered wins. With
// options.By, the record whose By property is smallest (the default) or largest (per
// options.Order) wins; ties keep the first-encountered record. Returns an error if Order is set
// without By.
func NewFirstValueReducer(
	field string, options *FtAggregateFirstValueOptions, name string,
) (FtAggregateReducer, error) {
	args := []string{field}
	if options != nil {
		if options.Order != "" && options.By == "" {
			return FtAggregateReducer{}, errors.New("FirstValue: Order requires By to be set")
		}
		if options.By != "" {
			args = append(args, "BY", options.By)
			if options.Order != "" {
				args = append(args, string(options.Order))
			}
		}
	}
	return FtAggregateReducer{Function: "FIRST_VALUE", Args: args, Name: name}, nil
}

// NewRandomSampleReducer creates a RANDOM_SAMPLE reducer, which returns a uniform random sample
// of up to size values of field from the records in a group, using reservoir sampling. size must
// be a non-negative integer no greater than 1000; the server rejects values outside this range.
func NewRandomSampleReducer(field string, size int, name string) FtAggregateReducer {
	return FtAggregateReducer{
		Function: "RANDOM_SAMPLE",
		Args:     []string{field, strconv.Itoa(size)},
		Name:     name,
	}
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
