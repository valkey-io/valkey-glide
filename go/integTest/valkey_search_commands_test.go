// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func intPtr(v int) *int         { return &v }
func f64Ptr(v float64) *float64 { return &v }

var testBicycles = []string{
	`{"brand":"Velorim","model":"Jigger","price":270,"condition":"new"}`,
	`{"brand":"Bicyk","model":"Hillcraft","price":1200,"condition":"used"}`,
	`{"brand":"Nord","model":"Chook air 5","price":815,"condition":"used"}`,
	`{"brand":"Eva","model":"Eva 291","price":3400,"condition":"used"}`,
	`{"brand":"Noka Bikes","model":"Kahuna","price":3200,"condition":"used"}`,
	`{"brand":"Breakout","model":"XBN 2.1 Alloy","price":810,"condition":"new"}`,
	`{"brand":"ScramBikes","model":"WattBike","price":2300,"condition":"new"}`,
	`{"brand":"Peaknetic","model":"Secto","price":430,"condition":"new"}`,
	`{"brand":"nHill","model":"Summit","price":1200,"condition":"new"}`,
	`{"model":"ThrillCycle","brand":"BikeShind","price":815,"condition":"refurbished"}`,
}

// jsonSet calls JSON.SET via CustomCommand on a standalone client.
// TODO: replace once https://github.com/valkey-io/valkey-glide/issues/5589 has been resolved.
func (suite *GlideTestSuite) jsonSet(ctx context.Context, client *glide.Client, key, path, value string) {
	_, err := client.CustomCommand(ctx, []string{"JSON.SET", key, path, value})
	assert.NoError(suite.T(), err)
}

// jsonSetCluster calls JSON.SET via CustomCommand on a cluster client.
// TODO: replace once https://github.com/valkey-io/valkey-glide/issues/5589 has been resolved.
func (suite *GlideTestSuite) jsonSetCluster(ctx context.Context, client *glide.ClusterClient, key, path, value string) {
	_, err := client.CustomCommand(ctx, []string{"JSON.SET", key, path, value})
	assert.NoError(suite.T(), err)
}

// createIndexHelper creates a simple HASH index with a numeric and text field.
func (suite *GlideTestSuite) createIndexHelper(ctx context.Context, ft *glide.GlideFt, indexName string) string {
	prefix := "{hash-search-" + uuid.New().String() + "}:"
	_, err := ft.Create(ctx, indexName,
		[]options.Field{
			options.NewNumericField("price"),
			options.NewTextField("title"),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{prefix},
		},
	)
	suite.NoError(err)
	return prefix
}

// --- ft_create ---

func (suite *GlideTestSuite) TestModuleFtCreate() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	// simple HNSW vector index
	res, err := ft.Create(ctx, uuid.New().String(),
		[]options.Field{
			options.NewVectorFieldHNSW("vec", constants.DistanceMetricL2, 2).SetAlias("VEC"),
		}, nil)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// JSON index with FLAT vector + prefix
	res, err = ft.Create(ctx, uuid.New().String(),
		[]options.Field{
			options.NewVectorFieldFlat("$.vec", constants.DistanceMetricL2, 6).SetAlias("VEC"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeJSON, Prefixes: []string{"json:"}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// HNSW with extra parameters
	res, err = ft.Create(ctx, uuid.New().String(),
		[]options.Field{
			options.NewVectorFieldHNSW("doc_embedding", constants.DistanceMetricCosine, 1536).
				SetNumberOfEdges(40).
				SetVectorsExaminedOnConstruction(250).
				SetVectorsExaminedOnRuntime(40),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{"docs:"}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// multiple fields
	res, err = ft.Create(ctx, uuid.New().String(),
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("published_at"),
			options.NewTagField("category"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{"blog:post:"}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// multiple prefixes
	dupIndex := uuid.New().String()
	res, err = ft.Create(ctx, dupIndex,
		[]options.Field{
			options.NewTagField("author_id"),
			options.NewTagField("author_ids"),
			options.NewTextField("title"),
			options.NewTextField("name"),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{"author:details:", "book:details:"},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// duplicate index → error
	_, err = ft.Create(ctx, dupIndex,
		[]options.Field{options.NewTextField("title"), options.NewTextField("name")}, nil)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "already exists")

	// no fields → error
	_, err = ft.Create(ctx, uuid.New().String(), []options.Field{}, nil)
	assert.Error(suite.T(), err)
	errMsg := err.Error()
	assert.True(suite.T(),
		strings.Contains(errMsg, "wrong number of arguments") ||
			strings.Contains(errMsg, "schema must have at least one"),
		"unexpected error: "+errMsg)

	// duplicate field name → error
	_, err = ft.Create(ctx, uuid.New().String(),
		[]options.Field{options.NewTextField("name"), options.NewTextField("name")}, nil)
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "already exists") ||
			strings.Contains(err.Error(), "Duplicate"),
		"unexpected error: "+err.Error())
}

// --- ft_search ---

func (suite *GlideTestSuite) TestModuleFtSearch() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewVectorFieldHNSW("vec", constants.DistanceMetricL2, 2).SetAlias("VEC"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	vec0 := string([]byte{0, 0, 0, 0, 0, 0, 0, 0})
	vec1 := string([]byte{0, 0, 0, 0, 0, 0, 0x80, 0xBF})
	_, err = client.HSet(ctx, prefix+"0", map[string]string{"vec": vec0})
	assert.NoError(suite.T(), err)
	_, err = client.HSet(ctx, prefix+"1", map[string]string{"vec": vec1})
	assert.NoError(suite.T(), err)
	time.Sleep(time.Second)

	result, err := ft.Search(ctx, index, "*=>[KNN 2 @VEC $query_vec]",
		&options.FtSearchOptions{
			Params:       []options.FtSearchParam{{Key: "query_vec", Value: vec0}},
			ReturnFields: []options.FtSearchReturnField{{FieldIdentifier: "vec"}},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)

	// verify both docs are present with their vec field values
	assert.Equal(suite.T(), 2, len(result.Documents))
	docsByKey := map[string]map[string]any{}
	for _, doc := range result.Documents {
		docsByKey[doc.Key] = doc.Fields
	}
	assert.Contains(suite.T(), docsByKey, prefix+"0")
	assert.Contains(suite.T(), docsByKey, prefix+"1")
	assert.Equal(suite.T(), vec0, docsByKey[prefix+"0"]["vec"])
	assert.Equal(suite.T(), vec1, docsByKey[prefix+"1"]["vec"])

	// querying non-existing index → error
	_, err = ft.Search(ctx, uuid.New().String(), "*", nil)
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_search_nocontent ---

func (suite *GlideTestSuite) TestModuleFtSearchNoContent() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2).SetAlias("VEC"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	vec0 := string([]byte{0, 0, 0, 0, 0, 0, 0, 0})
	vec1 := string([]byte{0, 0, 0, 0, 0, 0, 0x80, 0xBF})
	client.HSet(ctx, prefix+"0", map[string]string{"vec": vec0})
	client.HSet(ctx, prefix+"1", map[string]string{"vec": vec1})
	time.Sleep(time.Second)

	result, err := ft.Search(ctx, index, "*=>[KNN 2 @VEC $query_vec]",
		&options.FtSearchOptions{
			NoContent: true,
			Params:    []options.FtSearchParam{{Key: "query_vec", Value: vec0}},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)
	// With NOCONTENT each doc should have empty fields; both keys must be present
	assert.Equal(suite.T(), 2, len(result.Documents))
	docKeys := map[string]bool{}
	for _, doc := range result.Documents {
		docKeys[doc.Key] = true
		assert.Empty(suite.T(), doc.Fields)
	}
	assert.True(suite.T(), docKeys[prefix+"0"])
	assert.True(suite.T(), docKeys[prefix+"1"])
}

// --- ft_search_dialect ---

func (suite *GlideTestSuite) TestModuleFtSearchDialect() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2).SetAlias("VEC"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	vec0 := string([]byte{0, 0, 0, 0, 0, 0, 0, 0})
	client.HSet(ctx, prefix+"0", map[string]string{"vec": vec0})
	time.Sleep(time.Second)

	result, err := ft.Search(ctx, index, "*=>[KNN 1 @VEC $query_vec]",
		&options.FtSearchOptions{
			Dialect:      intPtr(2),
			Params:       []options.FtSearchParam{{Key: "query_vec", Value: vec0}},
			ReturnFields: []options.FtSearchReturnField{{FieldIdentifier: "vec"}},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), result.TotalResults)
	assert.Equal(suite.T(), 1, len(result.Documents))
	assert.Equal(suite.T(), prefix+"0", result.Documents[0].Key)
	assert.NotEmpty(suite.T(), result.Documents[0].Fields)
}

// --- ft_drop_and_ft_list ---

func (suite *GlideTestSuite) TestModuleFtDropAndFtList() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	index := uuid.New().String()
	_, err := ft.Create(ctx, index,
		[]options.Field{options.NewVectorFieldHNSW("vec", constants.DistanceMetricL2, 2)}, nil)
	assert.NoError(suite.T(), err)

	before, err := ft.List(ctx)
	assert.NoError(suite.T(), err)
	beforeSet := make(map[string]bool)
	for _, n := range before {
		beforeSet[n] = true
	}
	assert.True(suite.T(), beforeSet[index])

	_, err = ft.DropIndex(ctx, index)
	assert.NoError(suite.T(), err)

	after, err := ft.List(ctx)
	assert.NoError(suite.T(), err)
	afterSet := make(map[string]bool)
	for _, n := range after {
		afterSet[n] = true
	}
	assert.False(suite.T(), afterSet[index])

	// after + {index} must equal before (set equality)
	afterSet[index] = true
	assert.Equal(suite.T(), beforeSet, afterSet)

	// drop non-existent → error
	_, err = ft.DropIndex(ctx, index)
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_aggregate (bicycles + movies) ---

func (suite *GlideTestSuite) TestModuleFtAggregateBicycles() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("AGGREGATE is currently not fully supported")
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefixBicycles := "{bicycles-" + uuid.New().String() + "}:"
	indexBicycles := prefixBicycles + "idx"

	_, err := ft.Create(ctx, indexBicycles,
		[]options.Field{
			options.NewTextField("$.model").SetAlias("model"),
			options.NewNumericField("$.price").SetAlias("price"),
			options.NewTagField("$.condition").SetAlias("condition").SetSeparator(","),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeJSON, Prefixes: []string{prefixBicycles}})
	assert.NoError(suite.T(), err)

	for i, b := range testBicycles {
		suite.jsonSetCluster(ctx, client, prefixBicycles+strconv.Itoa(i), ".", b)
	}
	time.Sleep(time.Second)

	aggreg, err := ft.Aggregate(ctx, indexBicycles, "*",
		&options.FtAggregateOptions{
			LoadFields: []string{"__key"},
			Clauses: []options.FtAggregateClause{
				&options.FtAggregateGroupBy{
					Properties: []string{"@condition"},
					Reducers:   []options.FtAggregateReducer{{Function: "COUNT", Args: []string{}, Name: "bicycles"}},
				},
			},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), 3, len(aggreg))

	condCounts := map[string]float64{}
	for _, row := range aggreg {
		cond, _ := row["condition"].(string)
		cnt, _ := row["bicycles"].(float64)
		condCounts[cond] = cnt
	}
	assert.Equal(suite.T(), float64(5), condCounts["new"])
	assert.Equal(suite.T(), float64(4), condCounts["used"])
	assert.Equal(suite.T(), float64(1), condCounts["refurbished"])

	ft.DropIndex(ctx, indexBicycles)
}

func (suite *GlideTestSuite) TestModuleFtAggregateMovies() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("AGGREGATE is currently not fully supported")
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefixMovies := "{movies-" + uuid.New().String() + "}:"
	indexMovies := prefixMovies + "idx"

	_, err := ft.Create(ctx, indexMovies,
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("release_year"),
			options.NewNumericField("rating"),
			options.NewTagField("genre"),
			options.NewNumericField("votes"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefixMovies}})
	assert.NoError(suite.T(), err)

	type movie struct{ title, year, genre, rating, votes string }
	movies := []movie{
		{"Star Wars: Episode V - The Empire Strikes Back", "1980", "Action", "8.7", "1127635"},
		{"The Godfather", "1972", "Drama", "9.2", "1563839"},
		{"Heat", "1995", "Thriller", "8.2", "559490"},
		{"Star Wars: Episode VI - Return of the Jedi", "1983", "Action", "8.3", "906260"},
	}
	for i, m := range movies {
		_, err = client.HSet(ctx, prefixMovies+strconv.Itoa(11002+i), map[string]string{
			"title": m.title, "release_year": m.year, "genre": m.genre, "rating": m.rating, "votes": m.votes,
		})
		assert.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	aggreg, err := ft.Aggregate(ctx, indexMovies, "*",
		&options.FtAggregateOptions{
			LoadAll: true,
			Clauses: []options.FtAggregateClause{
				&options.FtAggregateApply{Expression: "ceil(@rating)", Name: "r_rating"},
				&options.FtAggregateGroupBy{
					Properties: []string{"@genre"},
					Reducers: []options.FtAggregateReducer{
						{Function: "COUNT", Args: []string{}, Name: "nb_of_movies"},
						{Function: "SUM", Args: []string{"votes"}, Name: "nb_of_votes"},
						{Function: "AVG", Args: []string{"r_rating"}, Name: "avg_rating"},
					},
				},
				&options.FtAggregateSortBy{
					Properties: []options.FtAggregateSortProperty{
						{Property: "@avg_rating", Order: constants.FtAggregateOrderByDesc},
						{Property: "@nb_of_votes", Order: constants.FtAggregateOrderByDesc},
					},
				},
			},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), 3, len(aggreg))

	genreMap := map[string]map[string]any{}
	for _, row := range aggreg {
		g, _ := row["genre"].(string)
		genreMap[g] = row
	}
	assert.Equal(suite.T(), float64(1), genreMap["Drama"]["nb_of_movies"])
	assert.Equal(suite.T(), float64(1563839), genreMap["Drama"]["nb_of_votes"])
	assert.Equal(suite.T(), float64(10), genreMap["Drama"]["avg_rating"])
	assert.Equal(suite.T(), float64(2), genreMap["Action"]["nb_of_movies"])
	assert.Equal(suite.T(), float64(2033895), genreMap["Action"]["nb_of_votes"])
	assert.Equal(suite.T(), float64(9), genreMap["Action"]["avg_rating"])
	assert.Equal(suite.T(), float64(1), genreMap["Thriller"]["nb_of_movies"])
	assert.Equal(suite.T(), float64(559490), genreMap["Thriller"]["nb_of_votes"])
	assert.Equal(suite.T(), float64(9), genreMap["Thriller"]["avg_rating"])

	ft.DropIndex(ctx, indexMovies)
}

// --- ft_info ---

func (suite *GlideTestSuite) TestModuleFtInfo() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	index := uuid.New().String()
	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewVectorFieldHNSW("$.vec", constants.DistanceMetricCosine, 42).SetAlias("VEC"),
			options.NewTextField("$.name").SetAlias("name"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeJSON, Prefixes: []string{"123"}})
	assert.NoError(suite.T(), err)

	info, err := ft.Info(ctx, index)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), index, info["index_name"])

	// key_type may be top-level or nested inside index_definition depending on server version
	keyType := ""
	if kt, ok := info["key_type"]; ok {
		keyType, _ = kt.(string)
	} else if idxDef, ok := info["index_definition"]; ok {
		// Server 9.x: index_definition is a flat array like [key_type HASH prefixes [...] ...]
		if defArr, ok := idxDef.([]any); ok {
			defMap := models.FlatArrayToMap(defArr)
			if kt, ok := defMap["key_type"]; ok {
				keyType, _ = kt.(string)
			}
		} else if defMap, ok := idxDef.(map[string]any); ok {
			if kt, ok := defMap["key_type"]; ok {
				keyType, _ = kt.(string)
			}
		}
	}
	assert.Equal(suite.T(), "JSON", keyType)

	// fields may be under "fields" or "attributes" depending on server version
	var fields []any
	if f, ok := info["fields"]; ok {
		fields, _ = f.([]any)
	} else if a, ok := info["attributes"]; ok {
		fields, _ = a.([]any)
	}
	assert.NotNil(suite.T(), fields)
	assert.Equal(suite.T(), 2, len(fields))

	// Find the vector field and text field by checking each field's type
	var vecField, textField map[string]any
	for _, f := range fields {
		fm, ok := f.(map[string]any)
		if !ok {
			// Field might be a flat array — convert it
			if fa, ok := f.([]any); ok {
				fm = models.FlatArrayToMap(fa)
			} else {
				continue
			}
		}
		if ft, _ := fm["type"].(string); ft == "VECTOR" {
			vecField = fm
		} else if ft == "TEXT" {
			textField = fm
		}
	}

	assert.NotNil(suite.T(), vecField)
	assert.Equal(suite.T(), "$.vec", vecField["identifier"])
	// field_name or attribute may hold the alias
	alias := ""
	if fn, ok := vecField["field_name"]; ok {
		alias, _ = fn.(string)
	} else if at, ok := vecField["attribute"]; ok {
		alias, _ = at.(string)
	}
	assert.Equal(suite.T(), "VEC", alias)

	assert.NotNil(suite.T(), textField)
	assert.Equal(suite.T(), "$.name", textField["identifier"])

	// querying a missing index → error
	ft.DropIndex(ctx, index)
	_, err = ft.Info(ctx, index)
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_aliasadd_aliasdel_aliasupdate_aliaslist ---

func (suite *GlideTestSuite) TestModuleFtAliasOperations() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("ALIAS is currently unsupported")
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	alias1 := "alias1-" + uuid.New().String()
	alias2 := "alias2-" + uuid.New().String()
	indexName := uuid.New().String() + "-index"

	_, err := ft.Create(ctx, indexName,
		[]options.Field{options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2)}, nil)
	assert.NoError(suite.T(), err)

	// add alias1
	res, err := ft.AliasAdd(ctx, alias1, indexName)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	aliases, err := ft.AliasList(ctx)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), indexName, aliases[alias1])

	// duplicate alias → error
	_, err = ft.AliasAdd(ctx, alias1, indexName)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Alias already exists")

	// aliasupdate creates alias2
	res, err = ft.AliasUpdate(ctx, alias2, indexName)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	aliases, err = ft.AliasList(ctx)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), indexName, aliases[alias1])
	assert.Equal(suite.T(), indexName, aliases[alias2])

	// delete alias2
	res, err = ft.AliasDel(ctx, alias2)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// delete alias1
	res, err = ft.AliasDel(ctx, alias1)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// aliasdel on non-existent → error
	_, err = ft.AliasDel(ctx, alias2)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Alias does not exist")

	// aliasadd with non-existent index → error
	_, err = ft.AliasAdd(ctx, alias1, "nonexistent_index")
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())

	ft.DropIndex(ctx, indexName)
}

// --- ft_explain ---

func (suite *GlideTestSuite) TestModuleFtExplain() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("EXPLAIN is currently unsupported")
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	indexName := uuid.New().String()
	suite.createIndexHelper(ctx, ft, indexName)

	query := "@price:[0 10]"
	result, err := ft.Explain(ctx, indexName, query)
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), result, "price")
	assert.Contains(suite.T(), result, "0")
	assert.Contains(suite.T(), result, "10")

	// wildcard query
	resultAll, err := ft.Explain(ctx, indexName, "*")
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), resultAll, "*")

	ft.DropIndex(ctx, indexName)

	// missing index → error
	_, err = ft.Explain(ctx, uuid.New().String(), "*")
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_explaincli ---

func (suite *GlideTestSuite) TestModuleFtExplainCLI() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("EXPLAIN is currently unsupported")
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	indexName := uuid.New().String()
	suite.createIndexHelper(ctx, ft, indexName)

	query := "@price:[0 10]"
	result, err := ft.ExplainCLI(ctx, indexName, query)
	assert.NoError(suite.T(), err)
	joined := strings.Join(result, " ")
	assert.Contains(suite.T(), joined, "price")
	assert.Contains(suite.T(), joined, "0")
	assert.Contains(suite.T(), joined, "10")

	// wildcard query
	resultAll, err := ft.ExplainCLI(ctx, indexName, "*")
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), strings.Join(resultAll, " "), "*")

	ft.DropIndex(ctx, indexName)

	// missing index → error
	_, err = ft.ExplainCLI(ctx, uuid.New().String(), "*")
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_search_1_2_sortby ---

func (suite *GlideTestSuite) TestModuleFtSearchSortBy() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewNumericField("price").SetSortable(true),
			options.NewTextField("name"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"price": "10", "name": "Aardvark"})
	client.HSet(ctx, prefix+"2", map[string]string{"price": "20", "name": "Mango"})
	client.HSet(ctx, prefix+"3", map[string]string{"price": "30", "name": "Zebra"})
	time.Sleep(time.Second)

	// ASC — verify documents are returned in ascending price order
	result, err := ft.Search(ctx, index, "@price:[1 +inf]",
		&options.FtSearchOptions{SortBy: "price", SortByOrder: constants.FtSearchSortOrderAsc})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), result.TotalResults)
	assert.Equal(suite.T(), 3, len(result.Documents))
	var ascPrices []string
	for _, doc := range result.Documents {
		ascPrices = append(ascPrices, doc.Fields["price"].(string))
	}
	assert.Equal(suite.T(), []string{"10", "20", "30"}, ascPrices)

	// DESC — verify documents are returned in descending price order
	result, err = ft.Search(ctx, index, "@price:[1 +inf]",
		&options.FtSearchOptions{SortBy: "price", SortByOrder: constants.FtSearchSortOrderDesc})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), result.TotalResults)
	assert.Equal(suite.T(), 3, len(result.Documents))
	var descPrices []string
	for _, doc := range result.Documents {
		descPrices = append(descPrices, doc.Fields["price"].(string))
	}
	assert.Equal(suite.T(), []string{"30", "20", "10"}, descPrices)

	ft.DropIndex(ctx, index)
}

// --- ft_search_1_2_withsortkeys ---

func (suite *GlideTestSuite) TestModuleFtSearchWithSortKeys() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewNumericField("price").SetSortable(true),
			options.NewTextField("name"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"price": "10", "name": "Aardvark"})
	client.HSet(ctx, prefix+"2", map[string]string{"price": "20", "name": "Mango"})
	client.HSet(ctx, prefix+"3", map[string]string{"price": "30", "name": "Zebra"})
	time.Sleep(time.Second)

	result, err := ft.Search(ctx, index, "@price:[1 +inf]",
		&options.FtSearchOptions{
			SortBy:       "price",
			SortByOrder:  constants.FtSearchSortOrderAsc,
			WithSortKeys: true,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), result.TotalResults)

	// With WITHSORTKEYS, documents have SortKey populated
	assert.Equal(suite.T(), 3, len(result.Documents))

	// Verify sort keys are present and documents are in ascending price order
	var sortKeyPrices []string
	for _, doc := range result.Documents {
		assert.NotEmpty(suite.T(), doc.SortKey)
		assert.NotNil(suite.T(), doc.Fields)
		sortKeyPrices = append(sortKeyPrices, doc.Fields["price"].(string))
	}
	assert.Equal(suite.T(), []string{"10", "20", "30"}, sortKeyPrices)

	// Sort keys for numeric fields are prefixed with '#'; verify all three appear
	foundPrices := map[string]bool{}
	for _, doc := range result.Documents {
		for _, p := range []string{"10", "20", "30"} {
			if strings.Contains(doc.SortKey, p) {
				foundPrices[p] = true
			}
		}
	}
	assert.True(suite.T(), foundPrices["10"])
	assert.True(suite.T(), foundPrices["20"])
	assert.True(suite.T(), foundPrices["30"])

	ft.DropIndex(ctx, index)
}

// --- ft_search_1_2_text_query_flags ---

func (suite *GlideTestSuite) TestModuleFtSearchTextQueryFlags() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	client.HSet(ctx, prefix+"2", map[string]string{"title": "hello there"})
	client.HSet(ctx, prefix+"3", map[string]string{"title": "goodbye world"})
	client.HSet(ctx, prefix+"4", map[string]string{"title": "world hello"})
	time.Sleep(time.Second)

	// VERBATIM — no stemming; "hello" matches 3 docs
	result, err := ft.Search(ctx, index, "hello", &options.FtSearchOptions{Verbatim: true})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), result.TotalResults)

	// SLOP without INORDER — "hello world" with slop 1 matches 2 docs
	result, err = ft.Search(ctx, index, "hello world", &options.FtSearchOptions{Slop: intPtr(1)})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)

	// SLOP + INORDER — only "hello world" (in order)
	result, err = ft.Search(ctx, index, "hello world",
		&options.FtSearchOptions{InOrder: true, Slop: intPtr(1)})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), result.TotalResults)

	ft.DropIndex(ctx, index)
}

// --- ft_search_1_2_shard_consistency ---

func (suite *GlideTestSuite) TestModuleFtSearchShardConsistency() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewTagField("tag"),
			options.NewNumericField("score"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"tag": "test", "score": "1"})
	client.HSet(ctx, prefix+"2", map[string]string{"tag": "test", "score": "2"})
	time.Sleep(time.Second)

	// SOMESHARDS + INCONSISTENT
	result, err := ft.Search(ctx, index, "@tag:{test}",
		&options.FtSearchOptions{
			ShardScope:  options.FtSearchShardScopeSomeShards,
			Consistency: options.FtSearchConsistencyInconsistent,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)

	// ALLSHARDS + CONSISTENT
	result, err = ft.Search(ctx, index, "@tag:{test}",
		&options.FtSearchOptions{
			ShardScope:  options.FtSearchShardScopeAllShards,
			Consistency: options.FtSearchConsistencyConsistent,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)

	ft.DropIndex(ctx, index)
}

// --- ft_aggregate_1_2_query_flags ---

func (suite *GlideTestSuite) TestModuleFtAggregateQueryFlags() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewNumericField("score"),
			options.NewTextField("title"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"score": "10", "title": "hello world"})
	client.HSet(ctx, prefix+"2", map[string]string{"score": "20", "title": "hello there"})
	time.Sleep(time.Second)

	// VERBATIM
	result, err := ft.Aggregate(ctx, index, "@score:[1 +inf]",
		&options.FtAggregateOptions{Verbatim: true})
	assert.NoError(suite.T(), err)
	require.Equal(suite.T(), 2, len(result))
	assert.Empty(suite.T(), result[0])
	assert.Empty(suite.T(), result[1])

	// INORDER + SLOP
	result, err = ft.Aggregate(ctx, index, "@score:[1 +inf]",
		&options.FtAggregateOptions{InOrder: true, Slop: intPtr(1)})
	assert.NoError(suite.T(), err)
	require.Equal(suite.T(), 2, len(result))
	assert.Empty(suite.T(), result[0])
	assert.Empty(suite.T(), result[1])

	// DIALECT
	result, err = ft.Aggregate(ctx, index, "@score:[1 +inf]",
		&options.FtAggregateOptions{Dialect: intPtr(2)})
	assert.NoError(suite.T(), err)
	require.Equal(suite.T(), 2, len(result))
	assert.Empty(suite.T(), result[0])
	assert.Empty(suite.T(), result[1])

	// LOAD (loadAll) — fields should be present in result
	result, err = ft.Aggregate(ctx, index, "@score:[20 +inf]",
		&options.FtAggregateOptions{LoadAll: true})
	assert.NoError(suite.T(), err)
	require.Equal(suite.T(), 1, len(result))
	assert.Equal(suite.T(), "hello there", result[0]["title"])

	ft.DropIndex(ctx, index)
}

// --- ft_create_1_2_index_options ---

func (suite *GlideTestSuite) TestModuleFtCreateIndexOptions() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	// SCORE + LANGUAGE + SKIPINITIALSCAN
	res, err := ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:        constants.IndexDataTypeHash,
			Prefixes:        []string{prefix},
			Score:           f64Ptr(1.0),
			Language:        "english",
			SkipInitialScan: true,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	ft.DropIndex(ctx, index)

	// MINSTEMSIZE — words shorter than 6 chars are not stemmed
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:    constants.IndexDataTypeHash,
			Prefixes:    []string{prefix},
			MinStemSize: intPtr(6),
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "running"})
	client.HSet(ctx, prefix+"2", map[string]string{"title": "plays"})
	time.Sleep(time.Second)
	r, _ := ft.Search(ctx, index, "run", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	r, _ = ft.Search(ctx, index, "play", nil)
	assert.Equal(suite.T(), int64(0), r.TotalResults)
	ft.DropIndex(ctx, index)

	// NOSTOPWORDS — "the" should be indexable
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:    constants.IndexDataTypeHash,
			Prefixes:    []string{prefix},
			NoStopWords: true,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "the quick fox"})
	time.Sleep(time.Second)
	r, _ = ft.Search(ctx, index, "the", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	ft.DropIndex(ctx, index)

	// STOPWORDS — "fox" and "an" are stop words
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:  constants.IndexDataTypeHash,
			Prefixes:  []string{prefix},
			StopWords: []string{"fox", "an"},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "the quick fox"})
	time.Sleep(time.Second)
	r, _ = ft.Search(ctx, index, "the", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	r, _ = ft.Search(ctx, index, "quick", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	_, err = ft.Search(ctx, index, "fox", nil) // stop word as query → error
	assert.Error(suite.T(), err)
	ft.DropIndex(ctx, index)

	// NOOFFSETS — SLOP queries should fail
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:  constants.IndexDataTypeHash,
			Prefixes:  []string{prefix},
			NoOffsets: true,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello"})
	time.Sleep(time.Second)
	r, _ = ft.Search(ctx, index, "hello", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	_, err = ft.Search(ctx, index, "hello", &options.FtSearchOptions{Slop: intPtr(1)})
	assert.Error(suite.T(), err) // SLOP requires offsets
	ft.DropIndex(ctx, index)
}

// --- ft_create_1_2_field_options ---

func (suite *GlideTestSuite) TestModuleFtCreateFieldOptions() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	// TextField nostem + weight + sortable; NumericField sortable; TagField sortable
	res, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewTextField("title").SetNoStem(true).SetWeight(1.0).SetSortable(true),
			options.NewNumericField("price").SetSortable(true),
			options.NewTagField("tag").SetSeparator(",").SetSortable(true),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello", "price": "10", "tag": "a,b"})
	time.Sleep(time.Second)

	// sortable numeric field works with SORTBY
	r, err := ft.Search(ctx, index, "@price:[1 +inf]",
		&options.FtSearchOptions{SortBy: "price", SortByOrder: constants.FtSearchSortOrderAsc})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), r.TotalResults)

	// nostem: "hello" matches, "hellos" does not
	r, _ = ft.Search(ctx, index, "hello", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	r, _ = ft.Search(ctx, index, "hellos", nil)
	assert.Equal(suite.T(), int64(0), r.TotalResults)
	ft.DropIndex(ctx, index)

	// TextField with WITHSUFFIXTRIE — suffix queries work
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title").SetWithSuffixTrie(true)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	time.Sleep(time.Second)
	r, err = ft.Search(ctx, index, "*orld", nil)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	ft.DropIndex(ctx, index)

	// TextField with NOSUFFIXTRIE — suffix queries fail
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title").SetNoSuffixTrie(true)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	time.Sleep(time.Second)
	_, err = ft.Search(ctx, index, "*orld", nil)
	assert.Error(suite.T(), err)
	ft.DropIndex(ctx, index)
}

// --- ft_info_1_2_options ---

func (suite *GlideTestSuite) TestModuleFtInfoWithOptions() {
	client := suite.defaultClusterClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "{" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	time.Sleep(time.Second)

	// LOCAL scope
	localInfo, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{Scope: options.FtInfoScopeLocal})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), index, localInfo["index_name"])
	assert.NotNil(suite.T(), localInfo["num_docs"])

	// LOCAL + ALLSHARDS + CONSISTENT
	localWithFlags, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{
			Scope:       options.FtInfoScopeLocal,
			ShardScope:  options.FtInfoShardScopeAllShards,
			Consistency: options.FtInfoConsistencyConsistent,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), index, localWithFlags["index_name"])

	// LOCAL + SOMESHARDS + INCONSISTENT
	localWithAltFlags, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{
			Scope:       options.FtInfoScopeLocal,
			ShardScope:  options.FtInfoShardScopeSomeShards,
			Consistency: options.FtInfoConsistencyInconsistent,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), index, localWithAltFlags["index_name"])

	// PRIMARY scope — may fail if coordinator not enabled
	primaryInfo, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{Scope: options.FtInfoScopePrimary})
	if err != nil {
		assert.Contains(suite.T(), err.Error(), "PRIMARY option is not valid")
	} else {
		assert.Equal(suite.T(), index, primaryInfo["index_name"])
	}

	// CLUSTER scope — may fail if coordinator not enabled
	clusterInfo, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{Scope: options.FtInfoScopeCluster})
	if err != nil {
		assert.Contains(suite.T(), err.Error(), "CLUSTER option is not valid")
	} else {
		assert.Equal(suite.T(), index, clusterInfo["index_name"])
	}

	ft.DropIndex(ctx, index)
}

// =============================================================================
// Standalone client variants of the cluster module tests above.
// =============================================================================

// --- ft_create (standalone) ---

func (suite *GlideTestSuite) TestModuleFtCreateStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	// simple HNSW vector index
	res, err := ft.Create(ctx, uuid.New().String(),
		[]options.Field{
			options.NewVectorFieldHNSW("vec", constants.DistanceMetricL2, 2).SetAlias("VEC"),
		}, nil)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// JSON index with FLAT vector + prefix
	res, err = ft.Create(ctx, uuid.New().String(),
		[]options.Field{
			options.NewVectorFieldFlat("$.vec", constants.DistanceMetricL2, 6).SetAlias("VEC"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeJSON, Prefixes: []string{"json:"}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// HNSW with extra parameters
	res, err = ft.Create(ctx, uuid.New().String(),
		[]options.Field{
			options.NewVectorFieldHNSW("doc_embedding", constants.DistanceMetricCosine, 1536).
				SetNumberOfEdges(40).
				SetVectorsExaminedOnConstruction(250).
				SetVectorsExaminedOnRuntime(40),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{"docs:"}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// multiple fields
	res, err = ft.Create(ctx, uuid.New().String(),
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("published_at"),
			options.NewTagField("category"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{"blog:post:"}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// multiple prefixes
	dupIndex := uuid.New().String()
	res, err = ft.Create(ctx, dupIndex,
		[]options.Field{
			options.NewTagField("author_id"),
			options.NewTagField("author_ids"),
			options.NewTextField("title"),
			options.NewTextField("name"),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{"author:details:", "book:details:"},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	// duplicate index → error
	_, err = ft.Create(ctx, dupIndex,
		[]options.Field{options.NewTextField("title"), options.NewTextField("name")}, nil)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "already exists")

	// no fields → error
	_, err = ft.Create(ctx, uuid.New().String(), []options.Field{}, nil)
	assert.Error(suite.T(), err)
	errMsg := err.Error()
	assert.True(suite.T(),
		strings.Contains(errMsg, "wrong number of arguments") ||
			strings.Contains(errMsg, "schema must have at least one"),
		"unexpected error: "+errMsg)

	// duplicate field name → error
	_, err = ft.Create(ctx, uuid.New().String(),
		[]options.Field{options.NewTextField("name"), options.NewTextField("name")}, nil)
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "already exists") ||
			strings.Contains(err.Error(), "Duplicate"),
		"unexpected error: "+err.Error())
}

// --- ft_search (standalone) ---

func (suite *GlideTestSuite) TestModuleFtSearchStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "search-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewVectorFieldHNSW("vec", constants.DistanceMetricL2, 2).SetAlias("VEC"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	vec0 := string([]byte{0, 0, 0, 0, 0, 0, 0, 0})
	vec1 := string([]byte{0, 0, 0, 0, 0, 0, 0x80, 0xBF})
	_, err = client.HSet(ctx, prefix+"0", map[string]string{"vec": vec0})
	assert.NoError(suite.T(), err)
	_, err = client.HSet(ctx, prefix+"1", map[string]string{"vec": vec1})
	assert.NoError(suite.T(), err)
	time.Sleep(time.Second)

	result, err := ft.Search(ctx, index, "*=>[KNN 2 @VEC $query_vec]",
		&options.FtSearchOptions{
			Params:       []options.FtSearchParam{{Key: "query_vec", Value: vec0}},
			ReturnFields: []options.FtSearchReturnField{{FieldIdentifier: "vec"}},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)

	assert.Equal(suite.T(), 2, len(result.Documents))
	docsByKey := map[string]map[string]any{}
	for _, doc := range result.Documents {
		docsByKey[doc.Key] = doc.Fields
	}
	assert.Contains(suite.T(), docsByKey, prefix+"0")
	assert.Contains(suite.T(), docsByKey, prefix+"1")
	assert.Equal(suite.T(), vec0, docsByKey[prefix+"0"]["vec"])
	assert.Equal(suite.T(), vec1, docsByKey[prefix+"1"]["vec"])

	// querying non-existing index → error
	_, err = ft.Search(ctx, uuid.New().String(), "*", nil)
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_search_nocontent (standalone) ---

func (suite *GlideTestSuite) TestModuleFtSearchNoContentStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "nocontent-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2).SetAlias("VEC"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	vec0 := string([]byte{0, 0, 0, 0, 0, 0, 0, 0})
	vec1 := string([]byte{0, 0, 0, 0, 0, 0, 0x80, 0xBF})
	client.HSet(ctx, prefix+"0", map[string]string{"vec": vec0})
	client.HSet(ctx, prefix+"1", map[string]string{"vec": vec1})
	time.Sleep(time.Second)

	result, err := ft.Search(ctx, index, "*=>[KNN 2 @VEC $query_vec]",
		&options.FtSearchOptions{
			NoContent: true,
			Params:    []options.FtSearchParam{{Key: "query_vec", Value: vec0}},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)
	assert.Equal(suite.T(), 2, len(result.Documents))
	docKeys := map[string]bool{}
	for _, doc := range result.Documents {
		docKeys[doc.Key] = true
		assert.Empty(suite.T(), doc.Fields)
	}
	assert.True(suite.T(), docKeys[prefix+"0"])
	assert.True(suite.T(), docKeys[prefix+"1"])
}

// --- ft_search_dialect (standalone) ---

func (suite *GlideTestSuite) TestModuleFtSearchDialectStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "dialect-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2).SetAlias("VEC"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	vec0 := string([]byte{0, 0, 0, 0, 0, 0, 0, 0})
	client.HSet(ctx, prefix+"0", map[string]string{"vec": vec0})
	time.Sleep(time.Second)

	result, err := ft.Search(ctx, index, "*=>[KNN 1 @VEC $query_vec]",
		&options.FtSearchOptions{
			Dialect:      intPtr(2),
			Params:       []options.FtSearchParam{{Key: "query_vec", Value: vec0}},
			ReturnFields: []options.FtSearchReturnField{{FieldIdentifier: "vec"}},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), result.TotalResults)
	assert.Equal(suite.T(), 1, len(result.Documents))
	assert.Equal(suite.T(), prefix+"0", result.Documents[0].Key)
	assert.NotEmpty(suite.T(), result.Documents[0].Fields)
}

// --- ft_drop_and_ft_list (standalone) ---

func (suite *GlideTestSuite) TestModuleFtDropAndFtListStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	index := uuid.New().String()
	_, err := ft.Create(ctx, index,
		[]options.Field{options.NewVectorFieldHNSW("vec", constants.DistanceMetricL2, 2)}, nil)
	assert.NoError(suite.T(), err)

	before, err := ft.List(ctx)
	assert.NoError(suite.T(), err)
	beforeSet := make(map[string]bool)
	for _, n := range before {
		beforeSet[n] = true
	}
	assert.True(suite.T(), beforeSet[index])

	_, err = ft.DropIndex(ctx, index)
	assert.NoError(suite.T(), err)

	after, err := ft.List(ctx)
	assert.NoError(suite.T(), err)
	afterSet := make(map[string]bool)
	for _, n := range after {
		afterSet[n] = true
	}
	assert.False(suite.T(), afterSet[index])

	afterSet[index] = true
	assert.Equal(suite.T(), beforeSet, afterSet)

	// drop non-existent → error
	_, err = ft.DropIndex(ctx, index)
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_aggregate bicycles (standalone) ---

func (suite *GlideTestSuite) TestModuleFtAggregateBicyclesStandalone() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("AGGREGATE is currently not fully supported")
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefixBicycles := "bicycles-" + uuid.New().String() + ":"
	indexBicycles := prefixBicycles + "idx"

	_, err := ft.Create(ctx, indexBicycles,
		[]options.Field{
			options.NewTextField("$.model").SetAlias("model"),
			options.NewNumericField("$.price").SetAlias("price"),
			options.NewTagField("$.condition").SetAlias("condition").SetSeparator(","),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeJSON, Prefixes: []string{prefixBicycles}})
	assert.NoError(suite.T(), err)

	for i, b := range testBicycles {
		suite.jsonSet(ctx, client, prefixBicycles+strconv.Itoa(i), ".", b)
	}
	time.Sleep(time.Second)

	aggreg, err := ft.Aggregate(ctx, indexBicycles, "*",
		&options.FtAggregateOptions{
			LoadFields: []string{"__key"},
			Clauses: []options.FtAggregateClause{
				&options.FtAggregateGroupBy{
					Properties: []string{"@condition"},
					Reducers:   []options.FtAggregateReducer{{Function: "COUNT", Args: []string{}, Name: "bicycles"}},
				},
			},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), 3, len(aggreg))

	condCounts := map[string]float64{}
	for _, row := range aggreg {
		cond, _ := row["condition"].(string)
		cnt, _ := row["bicycles"].(float64)
		condCounts[cond] = cnt
	}
	assert.Equal(suite.T(), float64(5), condCounts["new"])
	assert.Equal(suite.T(), float64(4), condCounts["used"])
	assert.Equal(suite.T(), float64(1), condCounts["refurbished"])

	ft.DropIndex(ctx, indexBicycles)
}

// --- ft_aggregate movies (standalone) ---

func (suite *GlideTestSuite) TestModuleFtAggregateMoviesStandalone() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("AGGREGATE is currently not fully supported")
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefixMovies := "movies-" + uuid.New().String() + ":"
	indexMovies := prefixMovies + "idx"

	_, err := ft.Create(ctx, indexMovies,
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("release_year"),
			options.NewNumericField("rating"),
			options.NewTagField("genre"),
			options.NewNumericField("votes"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefixMovies}})
	assert.NoError(suite.T(), err)

	type movie struct{ title, year, genre, rating, votes string }
	movies := []movie{
		{"Star Wars: Episode V - The Empire Strikes Back", "1980", "Action", "8.7", "1127635"},
		{"The Godfather", "1972", "Drama", "9.2", "1563839"},
		{"Heat", "1995", "Thriller", "8.2", "559490"},
		{"Star Wars: Episode VI - Return of the Jedi", "1983", "Action", "8.3", "906260"},
	}
	for i, m := range movies {
		_, err = client.HSet(ctx, prefixMovies+strconv.Itoa(11002+i), map[string]string{
			"title": m.title, "release_year": m.year, "genre": m.genre, "rating": m.rating, "votes": m.votes,
		})
		assert.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	aggreg, err := ft.Aggregate(ctx, indexMovies, "*",
		&options.FtAggregateOptions{
			LoadAll: true,
			Clauses: []options.FtAggregateClause{
				&options.FtAggregateApply{Expression: "ceil(@rating)", Name: "r_rating"},
				&options.FtAggregateGroupBy{
					Properties: []string{"@genre"},
					Reducers: []options.FtAggregateReducer{
						{Function: "COUNT", Args: []string{}, Name: "nb_of_movies"},
						{Function: "SUM", Args: []string{"votes"}, Name: "nb_of_votes"},
						{Function: "AVG", Args: []string{"r_rating"}, Name: "avg_rating"},
					},
				},
				&options.FtAggregateSortBy{
					Properties: []options.FtAggregateSortProperty{
						{Property: "@avg_rating", Order: constants.FtAggregateOrderByDesc},
						{Property: "@nb_of_votes", Order: constants.FtAggregateOrderByDesc},
					},
				},
			},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), 3, len(aggreg))

	genreMap := map[string]map[string]any{}
	for _, row := range aggreg {
		g, _ := row["genre"].(string)
		genreMap[g] = row
	}
	assert.Equal(suite.T(), float64(1), genreMap["Drama"]["nb_of_movies"])
	assert.Equal(suite.T(), float64(1563839), genreMap["Drama"]["nb_of_votes"])
	assert.Equal(suite.T(), float64(10), genreMap["Drama"]["avg_rating"])
	assert.Equal(suite.T(), float64(2), genreMap["Action"]["nb_of_movies"])
	assert.Equal(suite.T(), float64(2033895), genreMap["Action"]["nb_of_votes"])
	assert.Equal(suite.T(), float64(9), genreMap["Action"]["avg_rating"])
	assert.Equal(suite.T(), float64(1), genreMap["Thriller"]["nb_of_movies"])
	assert.Equal(suite.T(), float64(559490), genreMap["Thriller"]["nb_of_votes"])
	assert.Equal(suite.T(), float64(9), genreMap["Thriller"]["avg_rating"])

	ft.DropIndex(ctx, indexMovies)
}

// --- ft_info (standalone) ---

func (suite *GlideTestSuite) TestModuleFtInfoStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	index := uuid.New().String()
	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewVectorFieldHNSW("$.vec", constants.DistanceMetricCosine, 42).SetAlias("VEC"),
			options.NewTextField("$.name").SetAlias("name"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeJSON, Prefixes: []string{"123"}})
	assert.NoError(suite.T(), err)

	info, err := ft.Info(ctx, index)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), index, info["index_name"])

	keyType := ""
	if kt, ok := info["key_type"]; ok {
		keyType, _ = kt.(string)
	} else if idxDef, ok := info["index_definition"]; ok {
		if defArr, ok := idxDef.([]any); ok {
			defMap := models.FlatArrayToMap(defArr)
			if kt, ok := defMap["key_type"]; ok {
				keyType, _ = kt.(string)
			}
		} else if defMap, ok := idxDef.(map[string]any); ok {
			if kt, ok := defMap["key_type"]; ok {
				keyType, _ = kt.(string)
			}
		}
	}
	assert.Equal(suite.T(), "JSON", keyType)

	var fields []any
	if f, ok := info["fields"]; ok {
		fields, _ = f.([]any)
	} else if a, ok := info["attributes"]; ok {
		fields, _ = a.([]any)
	}
	assert.NotNil(suite.T(), fields)
	assert.Equal(suite.T(), 2, len(fields))

	var vecField, textField map[string]any
	for _, f := range fields {
		fm, ok := f.(map[string]any)
		if !ok {
			if fa, ok := f.([]any); ok {
				fm = models.FlatArrayToMap(fa)
			} else {
				continue
			}
		}
		if ft, _ := fm["type"].(string); ft == "VECTOR" {
			vecField = fm
		} else if ft == "TEXT" {
			textField = fm
		}
	}

	assert.NotNil(suite.T(), vecField)
	assert.Equal(suite.T(), "$.vec", vecField["identifier"])
	alias := ""
	if fn, ok := vecField["field_name"]; ok {
		alias, _ = fn.(string)
	} else if at, ok := vecField["attribute"]; ok {
		alias, _ = at.(string)
	}
	assert.Equal(suite.T(), "VEC", alias)

	assert.NotNil(suite.T(), textField)
	assert.Equal(suite.T(), "$.name", textField["identifier"])

	ft.DropIndex(ctx, index)
	_, err = ft.Info(ctx, index)
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_alias operations (standalone) ---

func (suite *GlideTestSuite) TestModuleFtAliasOperationsStandalone() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("ALIAS is currently unsupported")
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	alias1 := "alias1-" + uuid.New().String()
	alias2 := "alias2-" + uuid.New().String()
	indexName := uuid.New().String() + "-index"

	_, err := ft.Create(ctx, indexName,
		[]options.Field{options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2)}, nil)
	assert.NoError(suite.T(), err)

	res, err := ft.AliasAdd(ctx, alias1, indexName)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	aliases, err := ft.AliasList(ctx)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), indexName, aliases[alias1])

	_, err = ft.AliasAdd(ctx, alias1, indexName)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Alias already exists")

	res, err = ft.AliasUpdate(ctx, alias2, indexName)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	aliases, err = ft.AliasList(ctx)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), indexName, aliases[alias1])
	assert.Equal(suite.T(), indexName, aliases[alias2])

	res, err = ft.AliasDel(ctx, alias2)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	res, err = ft.AliasDel(ctx, alias1)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	_, err = ft.AliasDel(ctx, alias2)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Alias does not exist")

	_, err = ft.AliasAdd(ctx, alias1, "nonexistent_index")
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())

	ft.DropIndex(ctx, indexName)
}

// --- ft_explain (standalone) ---

func (suite *GlideTestSuite) TestModuleFtExplainStandalone() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("EXPLAIN is currently unsupported")
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	indexName := uuid.New().String()
	suite.createIndexHelper(ctx, ft, indexName)

	query := "@price:[0 10]"
	result, err := ft.Explain(ctx, indexName, query)
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), result, "price")
	assert.Contains(suite.T(), result, "0")
	assert.Contains(suite.T(), result, "10")

	resultAll, err := ft.Explain(ctx, indexName, "*")
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), resultAll, "*")

	ft.DropIndex(ctx, indexName)

	_, err = ft.Explain(ctx, uuid.New().String(), "*")
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_explaincli (standalone) ---

func (suite *GlideTestSuite) TestModuleFtExplainCLIStandalone() {
	// TODO: remove once it has been implemented in Valkey Search
	suite.T().Skip("EXPLAIN is currently unsupported")
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	indexName := uuid.New().String()
	suite.createIndexHelper(ctx, ft, indexName)

	query := "@price:[0 10]"
	result, err := ft.ExplainCLI(ctx, indexName, query)
	assert.NoError(suite.T(), err)
	joined := strings.Join(result, " ")
	assert.Contains(suite.T(), joined, "price")
	assert.Contains(suite.T(), joined, "0")
	assert.Contains(suite.T(), joined, "10")

	resultAll, err := ft.ExplainCLI(ctx, indexName, "*")
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), strings.Join(resultAll, " "), "*")

	ft.DropIndex(ctx, indexName)

	_, err = ft.ExplainCLI(ctx, uuid.New().String(), "*")
	assert.Error(suite.T(), err)
	assert.True(suite.T(),
		strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not exist"),
		"unexpected error: "+err.Error())
}

// --- ft_search sortby (standalone) ---

func (suite *GlideTestSuite) TestModuleFtSearchSortByStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "sortby-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewNumericField("price").SetSortable(true),
			options.NewTextField("name"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"price": "10", "name": "Aardvark"})
	client.HSet(ctx, prefix+"2", map[string]string{"price": "20", "name": "Mango"})
	client.HSet(ctx, prefix+"3", map[string]string{"price": "30", "name": "Zebra"})
	time.Sleep(time.Second)

	// ASC
	result, err := ft.Search(ctx, index, "@price:[1 +inf]",
		&options.FtSearchOptions{SortBy: "price", SortByOrder: constants.FtSearchSortOrderAsc})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), result.TotalResults)
	assert.Equal(suite.T(), 3, len(result.Documents))
	var ascPrices []string
	for _, doc := range result.Documents {
		ascPrices = append(ascPrices, doc.Fields["price"].(string))
	}
	assert.Equal(suite.T(), []string{"10", "20", "30"}, ascPrices)

	// DESC
	result, err = ft.Search(ctx, index, "@price:[1 +inf]",
		&options.FtSearchOptions{SortBy: "price", SortByOrder: constants.FtSearchSortOrderDesc})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), result.TotalResults)
	assert.Equal(suite.T(), 3, len(result.Documents))
	var descPrices []string
	for _, doc := range result.Documents {
		descPrices = append(descPrices, doc.Fields["price"].(string))
	}
	assert.Equal(suite.T(), []string{"30", "20", "10"}, descPrices)

	ft.DropIndex(ctx, index)
}

// --- ft_search withsortkeys (standalone) ---

func (suite *GlideTestSuite) TestModuleFtSearchWithSortKeysStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "sortkeys-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewNumericField("price").SetSortable(true),
			options.NewTextField("name"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"price": "10", "name": "Aardvark"})
	client.HSet(ctx, prefix+"2", map[string]string{"price": "20", "name": "Mango"})
	client.HSet(ctx, prefix+"3", map[string]string{"price": "30", "name": "Zebra"})
	time.Sleep(time.Second)

	result, err := ft.Search(ctx, index, "@price:[1 +inf]",
		&options.FtSearchOptions{
			SortBy:       "price",
			SortByOrder:  constants.FtSearchSortOrderAsc,
			WithSortKeys: true,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), result.TotalResults)
	assert.Equal(suite.T(), 3, len(result.Documents))

	var sortKeyPrices []string
	for _, doc := range result.Documents {
		assert.NotEmpty(suite.T(), doc.SortKey)
		assert.NotNil(suite.T(), doc.Fields)
		sortKeyPrices = append(sortKeyPrices, doc.Fields["price"].(string))
	}
	assert.Equal(suite.T(), []string{"10", "20", "30"}, sortKeyPrices)

	foundPrices := map[string]bool{}
	for _, doc := range result.Documents {
		for _, p := range []string{"10", "20", "30"} {
			if strings.Contains(doc.SortKey, p) {
				foundPrices[p] = true
			}
		}
	}
	assert.True(suite.T(), foundPrices["10"])
	assert.True(suite.T(), foundPrices["20"])
	assert.True(suite.T(), foundPrices["30"])

	ft.DropIndex(ctx, index)
}

// --- ft_search text query flags (standalone) ---

func (suite *GlideTestSuite) TestModuleFtSearchTextQueryFlagsStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "textflags-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	client.HSet(ctx, prefix+"2", map[string]string{"title": "hello there"})
	client.HSet(ctx, prefix+"3", map[string]string{"title": "goodbye world"})
	client.HSet(ctx, prefix+"4", map[string]string{"title": "world hello"})
	time.Sleep(time.Second)

	// VERBATIM
	result, err := ft.Search(ctx, index, "hello", &options.FtSearchOptions{Verbatim: true})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), result.TotalResults)

	// SLOP without INORDER
	result, err = ft.Search(ctx, index, "hello world", &options.FtSearchOptions{Slop: intPtr(1)})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)

	// SLOP + INORDER
	result, err = ft.Search(ctx, index, "hello world",
		&options.FtSearchOptions{InOrder: true, Slop: intPtr(1)})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), result.TotalResults)

	ft.DropIndex(ctx, index)
}

// --- ft_aggregate query flags (standalone) ---

func (suite *GlideTestSuite) TestModuleFtAggregateQueryFlagsStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "aggflags-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewNumericField("score"),
			options.NewTextField("title"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"score": "10", "title": "hello world"})
	client.HSet(ctx, prefix+"2", map[string]string{"score": "20", "title": "hello there"})
	time.Sleep(time.Second)

	// VERBATIM
	result, err := ft.Aggregate(ctx, index, "@score:[1 +inf]",
		&options.FtAggregateOptions{Verbatim: true})
	assert.NoError(suite.T(), err)
	require.Equal(suite.T(), 2, len(result))
	assert.Empty(suite.T(), result[0])
	assert.Empty(suite.T(), result[1])

	// INORDER + SLOP
	result, err = ft.Aggregate(ctx, index, "@score:[1 +inf]",
		&options.FtAggregateOptions{InOrder: true, Slop: intPtr(1)})
	assert.NoError(suite.T(), err)
	require.Equal(suite.T(), 2, len(result))
	assert.Empty(suite.T(), result[0])
	assert.Empty(suite.T(), result[1])

	// DIALECT
	result, err = ft.Aggregate(ctx, index, "@score:[1 +inf]",
		&options.FtAggregateOptions{Dialect: intPtr(2)})
	assert.NoError(suite.T(), err)
	require.Equal(suite.T(), 2, len(result))
	assert.Empty(suite.T(), result[0])
	assert.Empty(suite.T(), result[1])

	// LOAD (loadAll)
	result, err = ft.Aggregate(ctx, index, "@score:[20 +inf]",
		&options.FtAggregateOptions{LoadAll: true})
	assert.NoError(suite.T(), err)
	require.Equal(suite.T(), 1, len(result))
	assert.Equal(suite.T(), "hello there", result[0]["title"])

	ft.DropIndex(ctx, index)
}

// --- ft_create index options (standalone) ---

func (suite *GlideTestSuite) TestModuleFtCreateIndexOptionsStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "idxopts-" + uuid.New().String() + ":"
	index := prefix + "index"

	// SCORE + LANGUAGE + SKIPINITIALSCAN
	res, err := ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:        constants.IndexDataTypeHash,
			Prefixes:        []string{prefix},
			Score:           f64Ptr(1.0),
			Language:        "english",
			SkipInitialScan: true,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	ft.DropIndex(ctx, index)

	// MINSTEMSIZE
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:    constants.IndexDataTypeHash,
			Prefixes:    []string{prefix},
			MinStemSize: intPtr(6),
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "running"})
	client.HSet(ctx, prefix+"2", map[string]string{"title": "plays"})
	time.Sleep(time.Second)
	r, _ := ft.Search(ctx, index, "run", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	r, _ = ft.Search(ctx, index, "play", nil)
	assert.Equal(suite.T(), int64(0), r.TotalResults)
	ft.DropIndex(ctx, index)

	// NOSTOPWORDS
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:    constants.IndexDataTypeHash,
			Prefixes:    []string{prefix},
			NoStopWords: true,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "the quick fox"})
	time.Sleep(time.Second)
	r, _ = ft.Search(ctx, index, "the", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	ft.DropIndex(ctx, index)

	// STOPWORDS
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:  constants.IndexDataTypeHash,
			Prefixes:  []string{prefix},
			StopWords: []string{"fox", "an"},
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "the quick fox"})
	time.Sleep(time.Second)
	r, _ = ft.Search(ctx, index, "the", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	r, _ = ft.Search(ctx, index, "quick", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	_, err = ft.Search(ctx, index, "fox", nil)
	assert.Error(suite.T(), err)
	ft.DropIndex(ctx, index)

	// NOOFFSETS
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{
			DataType:  constants.IndexDataTypeHash,
			Prefixes:  []string{prefix},
			NoOffsets: true,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello"})
	time.Sleep(time.Second)
	r, _ = ft.Search(ctx, index, "hello", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	_, err = ft.Search(ctx, index, "hello", &options.FtSearchOptions{Slop: intPtr(1)})
	assert.Error(suite.T(), err)
	ft.DropIndex(ctx, index)
}

// --- ft_create field options (standalone) ---

func (suite *GlideTestSuite) TestModuleFtCreateFieldOptionsStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "fieldopts-" + uuid.New().String() + ":"
	index := prefix + "index"

	res, err := ft.Create(ctx, index,
		[]options.Field{
			options.NewTextField("title").SetNoStem(true).SetWeight(1.0).SetSortable(true),
			options.NewNumericField("price").SetSortable(true),
			options.NewTagField("tag").SetSeparator(",").SetSortable(true),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello", "price": "10", "tag": "a,b"})
	time.Sleep(time.Second)

	r, err := ft.Search(ctx, index, "@price:[1 +inf]",
		&options.FtSearchOptions{SortBy: "price", SortByOrder: constants.FtSearchSortOrderAsc})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), r.TotalResults)

	r, _ = ft.Search(ctx, index, "hello", nil)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	r, _ = ft.Search(ctx, index, "hellos", nil)
	assert.Equal(suite.T(), int64(0), r.TotalResults)
	ft.DropIndex(ctx, index)

	// WITHSUFFIXTRIE
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title").SetWithSuffixTrie(true)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	time.Sleep(time.Second)
	r, err = ft.Search(ctx, index, "*orld", nil)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), r.TotalResults)
	ft.DropIndex(ctx, index)

	// NOSUFFIXTRIE
	res, err = ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title").SetNoSuffixTrie(true)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), glide.OK, res)
	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	time.Sleep(time.Second)
	_, err = ft.Search(ctx, index, "*orld", nil)
	assert.Error(suite.T(), err)
	ft.DropIndex(ctx, index)
}

// --- ft_info with options (standalone) ---

func (suite *GlideTestSuite) TestModuleFtInfoWithOptionsStandalone() {
	if len(suite.standaloneHosts) == 0 {
		suite.T().Skip("No standalone server configured")
	}
	client := suite.defaultClient()
	ft := client.FT()
	ctx := context.Background()

	prefix := "infoopts-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := ft.Create(ctx, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	assert.NoError(suite.T(), err)

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	time.Sleep(time.Second)

	// LOCAL scope
	localInfo, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{Scope: options.FtInfoScopeLocal})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), index, localInfo["index_name"])
	assert.NotNil(suite.T(), localInfo["num_docs"])

	// LOCAL + ALLSHARDS + CONSISTENT
	localWithFlags, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{
			Scope:       options.FtInfoScopeLocal,
			ShardScope:  options.FtInfoShardScopeAllShards,
			Consistency: options.FtInfoConsistencyConsistent,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), index, localWithFlags["index_name"])

	// LOCAL + SOMESHARDS + INCONSISTENT
	localWithAltFlags, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{
			Scope:       options.FtInfoScopeLocal,
			ShardScope:  options.FtInfoShardScopeSomeShards,
			Consistency: options.FtInfoConsistencyInconsistent,
		})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), index, localWithAltFlags["index_name"])

	// PRIMARY scope
	primaryInfo, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{Scope: options.FtInfoScopePrimary})
	if err != nil {
		assert.Contains(suite.T(), err.Error(), "PRIMARY option is not valid")
	} else {
		assert.Equal(suite.T(), index, primaryInfo["index_name"])
	}

	// CLUSTER scope
	clusterInfo, err := ft.InfoWithOptions(ctx, index,
		&options.FtInfoOptions{Scope: options.FtInfoScopeCluster})
	if err != nil {
		assert.Contains(suite.T(), err.Error(), "CLUSTER option is not valid")
	} else {
		assert.Equal(suite.T(), index, clusterInfo["index_name"])
	}

	ft.DropIndex(ctx, index)
}
