// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"
	"strconv"
	"strings"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// FtHybridVsimFilter is a pre-filter on the VSIM arm's vector search, written in the same query
// language the SEARCH arm uses (e.g. "@title:hello"). With a filter present, the VSIM arm
// becomes an ordinary FT.SEARCH-style vector query under the hood, so the filter decides
// membership and the arm's score stays the vector distance -- it is never replaced by a
// text-relevance score the way a plain hybrid text query's score would be.
type FtHybridVsimFilter struct {
	// Expression is the filter expression, in the same query language FT.SEARCH uses.
	Expression string
	// Policy controls how the filter is applied. See [constants.HybridFilterPolicy]. Setting
	// this forces the wire-level counted VSIM FILTER form, required for the server to recognise
	// POLICY at all.
	Policy constants.HybridFilterPolicy
	// BatchSize tunes how many candidates POLICY BATCHES draws per batch. Requires
	// Policy=[constants.HybridFilterPolicyBatches]. The value is accepted but currently
	// discarded by the server (valkey-search issue #1457), so this currently has no observable
	// effect; included for forward compatibility and because the server still validates its
	// presence/ordering against Policy.
	BatchSize *int
}

// toArgs returns the arguments for the VSIM FILTER clause: the server's counted form when
// Policy or BatchSize is set, or the uncounted extension form otherwise.
func (f *FtHybridVsimFilter) toArgs() ([]string, error) {
	if f.BatchSize != nil && f.Policy != constants.HybridFilterPolicyBatches {
		return nil, errors.New("BatchSize requires Policy=HybridFilterPolicyBatches")
	}
	subArgs := []string{}
	if f.Policy != "" {
		subArgs = append(subArgs, "POLICY", string(f.Policy))
	}
	if f.BatchSize != nil {
		subArgs = append(subArgs, "BATCH_SIZE", strconv.Itoa(*f.BatchSize))
	}
	if len(subArgs) == 0 {
		// Uncounted extension: a bare expression, no POLICY/BATCH_SIZE possible in this form.
		return []string{"FILTER", f.Expression}, nil
	}
	tokenCount := 1 + len(subArgs)
	args := []string{"FILTER", strconv.Itoa(tokenCount), f.Expression}
	args = append(args, subArgs...)
	return args, nil
}

// FtHybridSearchArm is the SEARCH arm of an FT.HYBRID query: an ordinary (non-vector)
// FT.SEARCH-style text/tag/numeric query, run alongside the VSIM arm and fused with it. A vector
// query (e.g. a "=>[KNN ...]" clause) is rejected here by the server; use [FtHybridVsimArm] for
// the vector half instead.
type FtHybridSearchArm struct {
	// Query is the text/tag/numeric query, in the same language FT.SEARCH uses. Must not be a
	// vector query (e.g. must not contain a "=>[KNN ...]" clause); the server rejects that
	// combination.
	Query string
	// Scorer is the scoring function used to rank this arm's results (valkey-search 1.3.0).
	// "BM25STD" is the only value currently registered and selectable; any other value is
	// rejected by the server.
	Scorer string
	// YieldScoreAs exposes this arm's score under the given column name, reachable from an
	// FtAggregateApply expression, an FtAggregateSortProperty, FtAggregateGroupBy/
	// FtAggregateReducer expressions, and an FtHybridFunction's Expr. This alias cannot itself
	// be named inside a LoadFields entry -- the server rejects it even though the alias is
	// otherwise freely reachable -- it is already present in the default reply with no LOAD
	// needed, and remains reachable from every other clause type regardless of whether a LOAD
	// clause is present.
	YieldScoreAs string
}

// ToArgs returns the arguments for the SEARCH clause.
func (a *FtHybridSearchArm) ToArgs() []string {
	args := []string{"SEARCH", a.Query}
	if a.Scorer != "" {
		args = append(args, "SCORER", a.Scorer)
	}
	if a.YieldScoreAs != "" {
		args = append(args, "YIELD_SCORE_AS", a.YieldScoreAs)
	}
	return args
}

// FtHybridVsimArm is the VSIM arm of an FT.HYBRID query: a vector similarity search against a
// vector field, run alongside the SEARCH arm and fused with it.
//
// The query vector is given one of two ways, exactly one of which must be set:
//
//   - Vector: raw vector bytes. The client generates an internal $-prefixed PARAMS entry under
//     the hood and references it on the wire. FT.HYBRID's VSIM clause has no wire-level inline
//     vector literal at all -- the parameter token must start with "$" or the server rejects it
//     outright ("VSIM: vector parameter must start with '$'"), so an auto-generated PARAMS entry
//     is the only way to offer a "just pass the bytes" call shape. Go's []byte is always raw
//     vector data regardless of its content -- unlike a string, there is no ambiguity with a
//     "$name" reference to resolve, which is why this is a separate field from VectorParam
//     rather than a single any-typed field the way some other languages model it.
//   - VectorParam: an explicit "$name" string (including the leading "$") referencing an entry
//     the caller has placed in [FtHybridOptions.Params], exactly like
//     [FtSearchOptions.Params]/[FtAggregateOptions.Params] already work for every other
//     PARAMS-driven query.
type FtHybridVsimArm struct {
	// Field is the vector field's identifier, without the leading "@" (added automatically).
	Field string
	// Vector is the raw query vector bytes. An internal PARAMS entry is generated automatically.
	// Mutually exclusive with VectorParam; exactly one must be set.
	Vector []byte
	// VectorParam is a "$name" string (including the leading "$") referencing an entry the
	// caller has placed in [FtHybridOptions.Params]. Mutually exclusive with Vector; exactly one
	// must be set.
	VectorParam string
	// K is the number of nearest neighbours to return. Optional on the wire -- omitting the
	// whole KNN block (which happens when K, EfRuntime and ShardKRatio are all unset) defaults
	// to 10 server-side, the same default RediSearch documents for FT.HYBRID's VSIM clause.
	K *int
	// EfRuntime is the HNSW search-time effort parameter. Only valid when the KNN block is
	// written (RANGE is not currently supported by this typed API).
	EfRuntime *int
	// ShardKRatio tunes how much of K each shard returns during a cluster fanout. Parsed and
	// accepted but does not change query results, only how much work each shard does to produce
	// them -- accepted here so a query written for RediSearch is not rejected by this client
	// either.
	ShardKRatio *float64
	// YieldScoreAs exposes this arm's score (the vector distance) under the given column name.
	// See [FtHybridSearchArm.YieldScoreAs] for the LOAD-clause restriction that applies
	// identically here.
	YieldScoreAs string
	// Filter is a pre-filter on the vector search. See [FtHybridVsimFilter].
	Filter *FtHybridVsimFilter
}

// IsParamReference reports whether this arm references an existing PARAMS entry (VectorParam
// set) rather than needing a generated one for raw Vector bytes.
func (a *FtHybridVsimArm) IsParamReference() bool {
	return a.VectorParam != ""
}

// ToArgs returns the arguments for the VSIM clause. generatedParamName is the "$"-prefixed
// parameter name to reference on the wire when Vector was given as raw bytes rather than
// VectorParam; ignored if VectorParam is set. Pass "" when VectorParam is set.
func (a *FtHybridVsimArm) ToArgs(generatedParamName string) ([]string, error) {
	if (a.VectorParam == "") == (len(a.Vector) == 0) {
		return nil, errors.New("exactly one of Vector or VectorParam must be set")
	}
	if a.VectorParam != "" && !strings.HasPrefix(a.VectorParam, "$") {
		return nil, errors.New(
			"VectorParam must start with '$' (the server rejects a parameter reference that " +
				"doesn't: \"VSIM: vector parameter must start with '$'\")",
		)
	}
	if a.EfRuntime != nil && a.Filter != nil && a.Filter.Policy == constants.HybridFilterPolicyAdhoc {
		return nil, errors.New(
			"EfRuntime is not supported with Filter.Policy=HybridFilterPolicyAdhoc " +
				"(the server rejects this combination: \"EF_RUNTIME is irrelevant for POLICY ADHOC\")",
		)
	}

	var paramRef string
	if a.IsParamReference() {
		paramRef = a.VectorParam
	} else {
		if generatedParamName == "" {
			return nil, errors.New("generatedParamName is required when Vector is raw bytes rather than VectorParam")
		}
		paramRef = generatedParamName
	}

	args := []string{"VSIM", "@" + a.Field, paramRef}
	if a.K != nil || a.EfRuntime != nil || a.ShardKRatio != nil {
		subArgs := []string{}
		if a.K != nil {
			subArgs = append(subArgs, "K", strconv.Itoa(*a.K))
		}
		if a.EfRuntime != nil {
			subArgs = append(subArgs, "EF_RUNTIME", strconv.Itoa(*a.EfRuntime))
		}
		if a.ShardKRatio != nil {
			subArgs = append(subArgs, "SHARD_K_RATIO", strconv.FormatFloat(*a.ShardKRatio, 'g', -1, 64))
		}
		args = append(args, "KNN", strconv.Itoa(len(subArgs)))
		args = append(args, subArgs...)
	}
	if a.YieldScoreAs != "" {
		args = append(args, "YIELD_SCORE_AS", a.YieldScoreAs)
	}
	if a.Filter != nil {
		filterArgs, err := a.Filter.toArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, filterArgs...)
	}
	return args, nil
}

// FtHybridCombine is implemented by all COMBINE clause variants for FT.HYBRID, controlling how
// the SEARCH and VSIM arms' per-document scores are fused into one ranking. See
// [FtHybridRrf], [FtHybridLinear] and [FtHybridFunction].
type FtHybridCombine interface {
	ToArgs() ([]string, error)
}

// FtHybridRrf fuses the two arms' results using Reciprocal Rank Fusion: each arm's rank (not
// its raw score) contributes 1/(constant+rank) to the fused score.
type FtHybridRrf struct {
	// Constant is the RRF constant. Must be non-negative -- the server rejects a negative value
	// (it places a pole at rank == -constant).
	Constant *float64
	// Window is how many of each arm's results take part in fusion (and, in cluster mode, how
	// many each shard is asked to fetch). Bounded by the server's max-combine-window config.
	Window *int
	// YieldScoreAs exposes the fused score under the given column name instead of the default
	// "__score".
	YieldScoreAs string
}

// ToArgs returns the arguments for the COMBINE RRF clause.
func (c *FtHybridRrf) ToArgs() ([]string, error) {
	if c.Constant != nil && *c.Constant < 0 {
		return nil, errors.New("constant must not be negative")
	}
	subArgs := []string{}
	if c.Constant != nil {
		subArgs = append(subArgs, "CONSTANT", strconv.FormatFloat(*c.Constant, 'g', -1, 64))
	}
	if c.Window != nil {
		subArgs = append(subArgs, "WINDOW", strconv.Itoa(*c.Window))
	}
	if c.YieldScoreAs != "" {
		subArgs = append(subArgs, "YIELD_SCORE_AS", c.YieldScoreAs)
	}
	args := []string{"COMBINE", "RRF", strconv.Itoa(len(subArgs))}
	return append(args, subArgs...), nil
}

// FtHybridLinear fuses the two arms' results linearly: alpha*search_score + beta*vsim_score.
// Writing neither Alpha nor Beta defaults both to 0.3/0.7 respectively (server-side); writing
// exactly one without the other is rejected by the server as more likely a typo than an
// intentional default.
type FtHybridLinear struct {
	// Alpha is the SEARCH arm's weight. Defaults (server-side) to 0.3 when neither Alpha nor
	// Beta is set. Setting one without the other is rejected by the server.
	Alpha *float64
	// Beta is the VSIM arm's weight. Defaults (server-side) to 0.7 when neither Alpha nor Beta
	// is set.
	Beta *float64
	// Window is how many of each arm's results take part in fusion.
	Window *int
	// YieldScoreAs exposes the fused score under the given column name instead of the default
	// "__score".
	YieldScoreAs string
}

// ToArgs returns the arguments for the COMBINE LINEAR clause.
func (c *FtHybridLinear) ToArgs() ([]string, error) {
	if (c.Alpha == nil) != (c.Beta == nil) {
		return nil, errors.New(
			"alpha and beta must both be set, or both left unset -- the server rejects writing exactly one of them",
		)
	}
	subArgs := []string{}
	if c.Alpha != nil {
		subArgs = append(subArgs, "ALPHA", strconv.FormatFloat(*c.Alpha, 'g', -1, 64))
	}
	if c.Beta != nil {
		subArgs = append(subArgs, "BETA", strconv.FormatFloat(*c.Beta, 'g', -1, 64))
	}
	if c.Window != nil {
		subArgs = append(subArgs, "WINDOW", strconv.Itoa(*c.Window))
	}
	if c.YieldScoreAs != "" {
		subArgs = append(subArgs, "YIELD_SCORE_AS", c.YieldScoreAs)
	}
	args := []string{"COMBINE", "LINEAR", strconv.Itoa(len(subArgs))}
	return append(args, subArgs...), nil
}

// FtHybridFunction fuses the two arms' results using a caller-supplied expression. Expr is a raw
// string written in the same expression language FtAggregateApply's Expression uses -- the
// server compiles it against a context where each arm's score is reachable via its own
// YieldScoreAs alias (if any), a positional default "@__arm<i>_score", and (for this SEARCH+VSIM
// two-arm shape) "@__search_score" / "@__vector_score" specifically.
//
// This is a valkey-search extension not present in RediSearch's FT.HYBRID (included here anyway
// rather than dropped).
type FtHybridFunction struct {
	// Expr is the fusion expression, as a raw string (e.g. "@__search_score + @__vector_score"
	// or, with explicit arm aliases, "@ss + @vs").
	Expr string
	// Window is how many of each arm's results take part in fusion. If left unset, the server
	// defaults this to the widest window allowed rather than the usual RRF/LINEAR default, since
	// a caller-written expression typically expects to see every matching candidate, not a
	// top-window slice.
	Window *int
	// YieldScoreAs exposes the fused score under the given column name instead of the default
	// "__score".
	YieldScoreAs string
}

// ToArgs returns the arguments for the COMBINE FUNCTION clause.
func (c *FtHybridFunction) ToArgs() ([]string, error) {
	subArgs := []string{"EXPR", c.Expr}
	if c.Window != nil {
		subArgs = append(subArgs, "WINDOW", strconv.Itoa(*c.Window))
	}
	if c.YieldScoreAs != "" {
		subArgs = append(subArgs, "YIELD_SCORE_AS", c.YieldScoreAs)
	}
	args := []string{"COMBINE", "FUNCTION", strconv.Itoa(len(subArgs))}
	return append(args, subArgs...), nil
}

// FtHybridOptions holds the optional arguments for the FT.HYBRID command's aggregate-suffix
// tail, which follows the mandatory SEARCH, VSIM and optional COMBINE clauses (passed directly
// to the FtHybrid/ClusterFtHybrid functions, not through this struct).
type FtHybridOptions struct {
	// Combine controls how the SEARCH and VSIM arms' scores are fused. See [FtHybridRrf],
	// [FtHybridLinear] and [FtHybridFunction]. If omitted, the server's own default fusion
	// method applies.
	Combine FtHybridCombine
	// LoadAll loads every field declared in the index, plus both arms' score aliases
	// automatically. Unlike a named LoadFields/LoadFieldEntries list, "LOAD *" cannot name an
	// arm's score alias at all (there is nothing to name), so this form has no equivalent of the
	// LOAD-clause alias restriction described on [FtHybridSearchArm.YieldScoreAs]. Mutually
	// exclusive with LoadFields and LoadFieldEntries.
	LoadAll bool
	// LoadFields loads only the fields in this list, each loaded under its own name, reusing the
	// FT.AGGREGATE LOAD clause grammar verbatim (byte-identical between the two commands). Must
	// NOT include either arm's YieldScoreAs alias -- the server rejects it outright even though
	// the alias is otherwise freely reachable; see [FtHybridSearchArm.YieldScoreAs]. With no LOAD
	// clause at all (LoadAll, LoadFields and LoadFieldEntries all left unset), FT.HYBRID's
	// default reply is the document key and the fused score -- a default FT.AGGREGATE has no
	// equivalent of. Mutually exclusive with LoadAll. Can be freely combined with
	// LoadFieldEntries -- see [loadFieldsToArgs] for the emission order.
	LoadFields []string
	// LoadFieldEntries loads only the fields in this list, each optionally renamed via
	// [LoadField.Alias] (the LOAD ... AS form, valkey-search 1.3.0; see [LoadField]'s docstring
	// for the compatibility gate). Subject to the same YieldScoreAs restriction as LoadFields.
	// Mutually exclusive with LoadAll. Can be freely combined with LoadFields.
	LoadFieldEntries []LoadField
	// Clauses are FILTER, LIMIT, GROUPBY, SORTBY and APPLY clauses, reusing [FtAggregateClause]
	// and its implementations verbatim from the FT.AGGREGATE options (FT.HYBRID forwards this
	// whole tail to the exact same aggregate pipeline parser FT.AGGREGATE uses). Every arm alias
	// is freely reachable from these clauses (APPLY, SORTBY, GROUPBY/REDUCE) -- only the LOAD
	// clause has the restriction described above. FT.HYBRID also applies its own default LIMIT
	// of 10 rows when no FtAggregateLimit clause is given here -- FT.AGGREGATE has no such
	// default and returns everything. ToArgs always emits Clauses after Params's own serialized
	// arguments -- PARAMS is only actually written to the wire when len(Params) > 0 (plus any
	// internally generated VSIM entry) is non-empty, so this tail is not unconditionally
	// preceded by a PARAMS token; what matters is the relative order ToArgs guarantees, not
	// PARAMS's unconditional presence. Emitting a leading FILTER clause right after the VSIM
	// arm would otherwise let the server's VSIM parser claim it as a VSIM pre-filter instead.
	Clauses []FtAggregateClause
	// WithCursor requests a paged reply. See [FtWithCursor].
	WithCursor *FtWithCursor
	// LocalOnly restricts the query to the local shard rather than fanning out across the
	// cluster. This is a valkey-search extension not present in RediSearch's FT.HYBRID (included
	// here anyway rather than dropped). LOCALONLY is accepted anywhere in this tail BEFORE PARAMS
	// (including after DIALECT or a LOAD clause) but is rejected ("Unexpected argument
	// `LOCALONLY`") once PARAMS has been parsed, regardless of what follows PARAMS. ToArgs always
	// emits it before Params and Clauses (PARAMS always follows it immediately), so this
	// restriction is handled transparently regardless of the order this struct's fields were set
	// in.
	LocalOnly bool
	// Timeout overrides the module timeout, in milliseconds.
	Timeout *int
	// Params are key/value pairs referenced from within the SEARCH query, a VsimArm's VectorParam
	// reference, or any clause's expression text. If an [FtHybridVsimArm] in the same call is
	// given raw Vector bytes rather than VectorParam, an internally generated entry is merged
	// into this list automatically -- a key collision with a caller-supplied entry here is an
	// error rather than silently overwriting either one.
	Params []FtAggregateParam
	// Dialect sets the query dialect version. FT.HYBRID is fixed at dialect 2 server-side;
	// passing any other value is rejected by the server.
	Dialect *int
}

// mergeParams merges Params with an internally generated VSIM PARAMS entry, returning an error
// if the generated name collides with a caller-supplied one.
func (o *FtHybridOptions) mergeParams(generatedVsimParamName, generatedVsimParamValue string) ([]FtAggregateParam, error) {
	merged := make([]FtAggregateParam, 0, len(o.Params)+1)
	merged = append(merged, o.Params...)
	if generatedVsimParamName != "" {
		for _, p := range o.Params {
			if p.Key == generatedVsimParamName {
				return nil, errors.New(
					"generated VSIM parameter name '" + generatedVsimParamName +
						"' collides with an entry already present in Params; rename the caller-supplied Params entry",
				)
			}
		}
		merged = append(merged, FtAggregateParam{Key: generatedVsimParamName, Value: generatedVsimParamValue})
	}
	return merged, nil
}

// ToArgs returns the optional arguments for the FT.HYBRID command's aggregate-suffix tail.
// generatedVsimParamName/generatedVsimParamValue is an internally generated PARAMS entry for an
// [FtHybridVsimArm] given raw Vector bytes, merged with Params here. Supplied by
// FtHybrid/ClusterFtHybrid; not intended to be set directly by callers (pass "" for both when
// calling this directly, e.g. from a unit test, if no raw-bytes VSIM arm is involved).
func (o *FtHybridOptions) ToArgs(generatedVsimParamName, generatedVsimParamValue string) ([]string, error) {
	if o.LoadAll && (len(o.LoadFields) > 0 || len(o.LoadFieldEntries) > 0) {
		return nil, errors.New("LoadAll and LoadFields/LoadFieldEntries are mutually exclusive")
	}
	args := []string{}
	if o.LocalOnly {
		// Must come before PARAMS -- see the docstring above. ToArgs always emits it this early
		// regardless of field-set order, so this restriction is handled transparently.
		args = append(args, "LOCALONLY")
	}
	if o.LoadAll {
		args = append(args, "LOAD", "*")
	} else if loadArgs := loadFieldsToArgs(o.LoadFields, o.LoadFieldEntries); loadArgs != nil {
		args = append(args, loadArgs...)
	}
	if o.Timeout != nil {
		args = append(args, "TIMEOUT", strconv.Itoa(*o.Timeout))
	}
	mergedParams, err := o.mergeParams(generatedVsimParamName, generatedVsimParamValue)
	if err != nil {
		return nil, err
	}
	if len(mergedParams) > 0 {
		args = append(args, "PARAMS", strconv.Itoa(len(mergedParams)*2))
		for _, p := range mergedParams {
			args = append(args, p.Key, p.Value)
		}
	}
	if o.Clauses != nil {
		// Must come after PARAMS -- a leading FILTER clause emitted right after the VSIM arm
		// would otherwise be claimed by the server's VSIM parser as a VSIM pre-filter instead.
		// See the docstring above.
		for _, clause := range o.Clauses {
			args = append(args, clause.ftAggregateClauseArgs()...)
		}
	}
	if o.WithCursor != nil {
		args = append(args, o.WithCursor.toArgs()...)
	}
	if o.Dialect != nil {
		args = append(args, "DIALECT", strconv.Itoa(*o.Dialect))
	}
	return args, nil
}
