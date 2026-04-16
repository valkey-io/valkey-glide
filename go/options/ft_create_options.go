// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// fieldType is the internal wire type for a field in the index schema.
type fieldType string

const (
	fieldTypeText    fieldType = "TEXT"
	fieldTypeTag     fieldType = "TAG"
	fieldTypeNumeric fieldType = "NUMERIC"
	fieldTypeVector  fieldType = "VECTOR"
)

// vectorAlgorithm is the internal wire type for the vector indexing algorithm.
type vectorAlgorithm string

const (
	vectorAlgorithmHNSW vectorAlgorithm = "HNSW"
	vectorAlgorithmFlat vectorAlgorithm = "FLAT"
)

// Field is the interface implemented by all index schema field types.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type Field interface {
	// ToArgs returns the command arguments for this field.
	ToArgs() ([]string, error)
}

// baseField holds common field properties shared by all field types.
type baseField struct {
	Name     string
	Alias    string
	Sortable bool
}

func (f *baseField) baseArgs(ft fieldType) []string {
	args := []string{f.Name}
	if f.Alias != "" {
		args = append(args, "AS", f.Alias)
	}
	args = append(args, string(ft))
	return args
}

// TextField represents a full-text search field.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type TextField struct {
	baseField
	// NoStem disables stemming when indexing the field.
	NoStem bool
	// Weight declares the importance of this field when calculating result accuracy. Default is 1.
	Weight *float64
	// WithSuffixTrie keeps a suffix trie for the field to optimize contains and suffix queries.
	// Mutually exclusive with NoSuffixTrie.
	WithSuffixTrie bool
	// NoSuffixTrie disables the suffix trie for the field.
	// Mutually exclusive with WithSuffixTrie.
	NoSuffixTrie bool
}

// NewTextField creates a new TextField with the given name.
func NewTextField(name string) *TextField {
	return &TextField{baseField: baseField{Name: name}}
}

// SetAlias sets an alias for the field.
func (f *TextField) SetAlias(alias string) *TextField { f.Alias = alias; return f }

// SetSortable enables sorting on this field.
func (f *TextField) SetSortable(sortable bool) *TextField { f.Sortable = sortable; return f }

// SetNoStem disables stemming when indexing the field.
func (f *TextField) SetNoStem(nostem bool) *TextField { f.NoStem = nostem; return f }

// SetWeight sets the importance of this field when calculating result accuracy.
func (f *TextField) SetWeight(weight float64) *TextField { f.Weight = &weight; return f }

// SetWithSuffixTrie enables suffix trie for the field.
func (f *TextField) SetWithSuffixTrie(v bool) *TextField { f.WithSuffixTrie = v; return f }

// SetNoSuffixTrie disables suffix trie for the field.
func (f *TextField) SetNoSuffixTrie(v bool) *TextField { f.NoSuffixTrie = v; return f }

// ToArgs returns the command arguments for this TextField.
func (f *TextField) ToArgs() ([]string, error) {
	if f.WithSuffixTrie && f.NoSuffixTrie {
		return nil, errors.New("WithSuffixTrie and NoSuffixTrie are mutually exclusive")
	}
	args := f.baseArgs(fieldTypeText)
	if f.NoStem {
		args = append(args, "NOSTEM")
	}
	if f.Weight != nil {
		args = append(args, "WEIGHT", strconv.FormatFloat(*f.Weight, 'g', -1, 64))
	}
	if f.WithSuffixTrie {
		args = append(args, "WITHSUFFIXTRIE")
	} else if f.NoSuffixTrie {
		args = append(args, "NOSUFFIXTRIE")
	}
	if f.Sortable {
		args = append(args, "SORTABLE")
	}
	return args, nil
}

// TagField represents a tag field (a list of tags delimited by a separator character).
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type TagField struct {
	baseField
	// Separator specifies how text in the attribute is split into individual tags. Must be a single character.
	Separator string
	// CaseSensitive preserves the original letter cases of tags.
	CaseSensitive bool
}

// NewTagField creates a new TagField with the given name.
func NewTagField(name string) *TagField {
	return &TagField{baseField: baseField{Name: name}}
}

// SetAlias sets an alias for the field.
func (f *TagField) SetAlias(alias string) *TagField { f.Alias = alias; return f }

// SetSortable enables sorting on this field.
func (f *TagField) SetSortable(sortable bool) *TagField { f.Sortable = sortable; return f }

// SetSeparator sets the tag separator character.
func (f *TagField) SetSeparator(sep string) *TagField { f.Separator = sep; return f }

// SetCaseSensitive preserves original letter cases of tags.
func (f *TagField) SetCaseSensitive(v bool) *TagField { f.CaseSensitive = v; return f }

// ToArgs returns the command arguments for this TagField.
func (f *TagField) ToArgs() ([]string, error) {
	args := f.baseArgs(fieldTypeTag)
	if f.Separator != "" {
		args = append(args, "SEPARATOR", f.Separator)
	}
	if f.CaseSensitive {
		args = append(args, "CASESENSITIVE")
	}
	if f.Sortable {
		args = append(args, "SORTABLE")
	}
	return args, nil
}

// NumericField represents a numeric field.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type NumericField struct {
	baseField
}

// NewNumericField creates a new NumericField with the given name.
func NewNumericField(name string) *NumericField {
	return &NumericField{baseField: baseField{Name: name}}
}

// SetAlias sets an alias for the field.
func (f *NumericField) SetAlias(alias string) *NumericField { f.Alias = alias; return f }

// SetSortable enables sorting on this field.
func (f *NumericField) SetSortable(sortable bool) *NumericField { f.Sortable = sortable; return f }

// ToArgs returns the command arguments for this NumericField.
func (f *NumericField) ToArgs() ([]string, error) {
	args := f.baseArgs(fieldTypeNumeric)
	if f.Sortable {
		args = append(args, "SORTABLE")
	}
	return args, nil
}

// VectorFieldFlat represents a vector field using the FLAT (brute force) algorithm.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type VectorFieldFlat struct {
	baseField
	// DistanceMetric is the distance metric used in vector similarity search.
	DistanceMetric constants.DistanceMetric
	// Dimensions is the number of dimensions in the vector.
	Dimensions int
	// Type is the vector data type. Defaults to FLOAT32.
	Type constants.VectorDataType
	// InitialCap is the initial vector capacity in the index affecting memory allocation size.
	InitialCap *int
}

// NewVectorFieldFlat creates a new VectorFieldFlat with required parameters.
func NewVectorFieldFlat(name string, distanceMetric constants.DistanceMetric, dimensions int) *VectorFieldFlat {
	return &VectorFieldFlat{
		baseField:      baseField{Name: name},
		DistanceMetric: distanceMetric,
		Dimensions:     dimensions,
		Type:           constants.VectorDataTypeFloat32,
	}
}

// SetAlias sets an alias for the field.
func (f *VectorFieldFlat) SetAlias(alias string) *VectorFieldFlat { f.Alias = alias; return f }

// SetType sets the vector data type.
func (f *VectorFieldFlat) SetType(t constants.VectorDataType) *VectorFieldFlat {
	f.Type = t
	return f
}

// SetInitialCap sets the initial vector capacity in the index.
func (f *VectorFieldFlat) SetInitialCap(cap int) *VectorFieldFlat { f.InitialCap = &cap; return f }

// ToArgs returns the command arguments for this VectorFieldFlat.
func (f *VectorFieldFlat) ToArgs() ([]string, error) {
	args := f.baseArgs(fieldTypeVector)
	args = append(args, string(vectorAlgorithmFlat))
	attrs := f.buildAttrs()
	args = append(args, strconv.Itoa(len(attrs)))
	args = append(args, attrs...)
	return args, nil
}

func (f *VectorFieldFlat) buildAttrs() []string {
	attrs := []string{
		"DIM", strconv.Itoa(f.Dimensions),
		"DISTANCE_METRIC", string(f.DistanceMetric),
		"TYPE", string(f.Type),
	}
	if f.InitialCap != nil {
		attrs = append(attrs, "INITIAL_CAP", strconv.Itoa(*f.InitialCap))
	}
	return attrs
}

// VectorFieldHNSW represents a vector field using the HNSW algorithm.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type VectorFieldHNSW struct {
	baseField
	// DistanceMetric is the distance metric used in vector similarity search.
	DistanceMetric constants.DistanceMetric
	// Dimensions is the number of dimensions in the vector.
	Dimensions int
	// Type is the vector data type. Defaults to FLOAT32.
	Type constants.VectorDataType
	// InitialCap is the initial vector capacity in the index affecting memory allocation size.
	InitialCap *int
	// NumberOfEdges is the max number of outgoing edges for each node in the graph per layer.
	// Default is 16, maximum is 512. Equivalent to M in the module API.
	NumberOfEdges *int
	// VectorsExaminedOnConstruction controls the number of vectors examined during index construction.
	// Default is 200, maximum is 4096. Equivalent to EF_CONSTRUCTION in the module API.
	VectorsExaminedOnConstruction *int
	// VectorsExaminedOnRuntime controls the number of vectors examined during query operations.
	// Default is 10, maximum is 4096. Equivalent to EF_RUNTIME in the module API.
	VectorsExaminedOnRuntime *int
}

// NewVectorFieldHNSW creates a new VectorFieldHNSW with required parameters.
func NewVectorFieldHNSW(name string, distanceMetric constants.DistanceMetric, dimensions int) *VectorFieldHNSW {
	return &VectorFieldHNSW{
		baseField:      baseField{Name: name},
		DistanceMetric: distanceMetric,
		Dimensions:     dimensions,
		Type:           constants.VectorDataTypeFloat32,
	}
}

// SetAlias sets an alias for the field.
func (f *VectorFieldHNSW) SetAlias(alias string) *VectorFieldHNSW { f.Alias = alias; return f }

// SetType sets the vector data type.
func (f *VectorFieldHNSW) SetType(t constants.VectorDataType) *VectorFieldHNSW {
	f.Type = t
	return f
}

// SetInitialCap sets the initial vector capacity in the index.
func (f *VectorFieldHNSW) SetInitialCap(cap int) *VectorFieldHNSW { f.InitialCap = &cap; return f }

// SetNumberOfEdges sets the max number of outgoing edges per node (M parameter).
func (f *VectorFieldHNSW) SetNumberOfEdges(m int) *VectorFieldHNSW {
	f.NumberOfEdges = &m
	return f
}

// SetVectorsExaminedOnConstruction sets the EF_CONSTRUCTION parameter.
func (f *VectorFieldHNSW) SetVectorsExaminedOnConstruction(ef int) *VectorFieldHNSW {
	f.VectorsExaminedOnConstruction = &ef
	return f
}

// SetVectorsExaminedOnRuntime sets the EF_RUNTIME parameter.
func (f *VectorFieldHNSW) SetVectorsExaminedOnRuntime(ef int) *VectorFieldHNSW {
	f.VectorsExaminedOnRuntime = &ef
	return f
}

// ToArgs returns the command arguments for this VectorFieldHNSW.
func (f *VectorFieldHNSW) ToArgs() ([]string, error) {
	args := f.baseArgs(fieldTypeVector)
	args = append(args, string(vectorAlgorithmHNSW))
	attrs := f.buildAttrs()
	args = append(args, strconv.Itoa(len(attrs)))
	args = append(args, attrs...)
	return args, nil
}

func (f *VectorFieldHNSW) buildAttrs() []string {
	attrs := []string{
		"DIM", strconv.Itoa(f.Dimensions),
		"DISTANCE_METRIC", string(f.DistanceMetric),
		"TYPE", string(f.Type),
	}
	if f.InitialCap != nil {
		attrs = append(attrs, "INITIAL_CAP", strconv.Itoa(*f.InitialCap))
	}
	if f.NumberOfEdges != nil {
		attrs = append(attrs, "M", strconv.Itoa(*f.NumberOfEdges))
	}
	if f.VectorsExaminedOnConstruction != nil {
		attrs = append(attrs, "EF_CONSTRUCTION", strconv.Itoa(*f.VectorsExaminedOnConstruction))
	}
	if f.VectorsExaminedOnRuntime != nil {
		attrs = append(attrs, "EF_RUNTIME", strconv.Itoa(*f.VectorsExaminedOnRuntime))
	}
	return attrs
}

// FtCreateOptions holds optional arguments for the FT.CREATE command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type FtCreateOptions struct {
	// DataType is the index data type. If not set, a HASH index is created.
	DataType constants.IndexDataType
	// Prefixes is a list of key prefixes to index.
	Prefixes []string
	// Score is the default score for documents in the index. Default is 1.0.
	Score *float64
	// Language is the default language for documents in the index.
	Language string
	// SkipInitialScan skips scanning and indexing existing documents on index creation.
	SkipInitialScan bool
	// MinStemSize is the minimum word length to stem. Words shorter than this are not stemmed.
	MinStemSize *int
	// WithOffsets stores term offsets for document fields. Mutually exclusive with NoOffsets.
	WithOffsets bool
	// NoOffsets does not store term offsets. Mutually exclusive with WithOffsets.
	NoOffsets bool
	// NoStopWords disables stop-word filtering. Mutually exclusive with StopWords.
	NoStopWords bool
	// StopWords is a custom list of stop words. Mutually exclusive with NoStopWords.
	StopWords []string
	// Punctuation is a custom set of punctuation characters to use during tokenization.
	Punctuation string
}

// ToArgs returns the command arguments for FtCreateOptions.
func (o *FtCreateOptions) ToArgs() ([]string, error) {
	if o.WithOffsets && o.NoOffsets {
		return nil, errors.New("WithOffsets and NoOffsets are mutually exclusive")
	}
	if o.NoStopWords && len(o.StopWords) > 0 {
		return nil, errors.New("NoStopWords and StopWords are mutually exclusive")
	}
	args := []string{}
	if o.DataType != "" {
		args = append(args, "ON", string(o.DataType))
	}
	if len(o.Prefixes) > 0 {
		args = append(args, "PREFIX", strconv.Itoa(len(o.Prefixes)))
		args = append(args, o.Prefixes...)
	}
	if o.Score != nil {
		args = append(args, "SCORE", strconv.FormatFloat(*o.Score, 'g', -1, 64))
	}
	if o.Language != "" {
		args = append(args, "LANGUAGE", o.Language)
	}
	if o.SkipInitialScan {
		args = append(args, "SKIPINITIALSCAN")
	}
	if o.MinStemSize != nil {
		args = append(args, "MINSTEMSIZE", strconv.Itoa(*o.MinStemSize))
	}
	if o.WithOffsets {
		args = append(args, "WITHOFFSETS")
	} else if o.NoOffsets {
		args = append(args, "NOOFFSETS")
	}
	if o.NoStopWords {
		args = append(args, "NOSTOPWORDS")
	} else if len(o.StopWords) > 0 {
		args = append(args, "STOPWORDS", strconv.Itoa(len(o.StopWords)))
		args = append(args, o.StopWords...)
	}
	if o.Punctuation != "" {
		args = append(args, "PUNCTUATION", o.Punctuation)
	}
	return args, nil
}
