// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func anyToString(value interface{}) string {
	switch v := value.(type) {
	case string:
		return v
	case int:
		return strconv.Itoa(v)
	case int8:
		return strconv.FormatInt(int64(v), 10)
	case int16:
		return strconv.FormatInt(int64(v), 10)
	case int32:
		return strconv.FormatInt(int64(v), 10)
	case int64:
		return strconv.FormatInt(v, 10)
	case uint:
		return strconv.FormatUint(uint64(v), 10)
	case uint8:
		return strconv.FormatUint(uint64(v), 10)
	case uint16:
		return strconv.FormatUint(uint64(v), 10)
	case uint32:
		return strconv.FormatUint(uint64(v), 10)
	case uint64:
		return strconv.FormatUint(v, 10)
	case float32:
		return strconv.FormatFloat(float64(v), 'f', -1, 32)
	case float64:
		return strconv.FormatFloat(v, 'f', -1, 64)
	case bool:
		return strconv.FormatBool(v)
	case []byte:
		return string(v)
	case fmt.Stringer:
		return v.String()
	default:
		return fmt.Sprintf("%v", v)
	}
}

func (suite *GlideTestSuite) TestWatch() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(key, "value"))

	result, err := client.Watch([]string{"key1"})
	assert.NoError(suite.T(), err)
	strings.Contains(result, "OK")
	tx := api.NewTransaction(client)
	cmd := tx.GlideClient
	cmd.Del([]string{key})
	resultTx, errTx := tx.Exec()
	assert.NoError(suite.T(), errTx)
	resultString := anyToString(resultTx)
	strings.Contains(resultString, "1")
}

func (suite *GlideTestSuite) TestUnwatch() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(key, "value"))
	result, errTx := client.Unwatch()
	strings.Contains(result, "OK")
	assert.NoError(suite.T(), errTx)
}

func (suite *GlideTestSuite) TestExec() {
	client := suite.defaultClient()
	key := uuid.New().String()
	tx := api.NewTransaction(client)
	cmd := tx.GlideClient
	cmd.Set(key, "hello")
	cmd.Get(key)
	cmd.Del([]string{key})
	result, errTx := tx.Exec()
	assert.NoError(suite.T(), errTx)
	resultString := anyToString(result)
	strings.Contains(resultString, "OK hello 1")
}

func (suite *GlideTestSuite) TestExec_Cluster() {
	client := suite.defaultClusterClient()
	key := uuid.New().String()

	// Create transaction with multiple operations
	tx := api.NewClusterTransaction(client)
	cmd := tx.GlideClusterClient

	cmd.Set(key, "hello")
	cmd.Get(key)
	cmd.Del([]string{key})

	// Execute the transaction
	result, errTx := tx.Exec()
	assert.NoError(suite.T(), errTx)

	// Convert the result to a string for verification
	resultString := anyToString(result)
	assert.Contains(suite.T(), resultString, "OK")
	assert.Contains(suite.T(), resultString, "hello")
	assert.Contains(suite.T(), resultString, "1")
}

func (suite *GlideTestSuite) TestExecWithOptions() {
	client := suite.defaultClient()
	key := uuid.New().String()

	suite.verifyOK(client.Set(key, "hello"))

	tx := api.NewTransaction(client)
	cmd := tx.GlideClient

	cmd.CustomCommand([]string{"HGET", key, "field"})
	options := &api.TransactionOption{
		RaiseOnError: true,
	}
	_, err := tx.ExecWithOptions(options)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "WRONGTYPE")
}

func (suite *GlideTestSuite) TestExecWithOptions_RaiseOnErrorFalse() {
	client := suite.defaultClient()
	key := uuid.New().String()
	key2 := "{" + key + "}" + uuid.New().String()

	suite.verifyOK(client.Set(key, "hello"))

	tx := api.NewTransaction(client)
	cmd := tx.GlideClient
	cmd.Set(key, "hello")
	cmd.LPop(key)
	cmd.Del([]string{key})
	cmd.Rename(key, key2)

	// Test with RaiseOnError=false
	options := &api.TransactionOption{
		RaiseOnError: false,
	}
	results, err := tx.ExecWithOptions(options)
	assert.NoError(suite.T(), err)

	assert.Equal(suite.T(), 4, len(results))

	assert.Equal(suite.T(), "OK", results[0])
	assert.Equal(suite.T(), "ExtensionError", results[1])

	assert.Equal(suite.T(), int64(1), results[2])
	assert.Equal(suite.T(), "KnownError", results[3])
}

func (suite *GlideTestSuite) TestExecWithOptions_RaiseOnErrorTrue() {
	client := suite.defaultClient()
	key := uuid.New().String()
	key2 := "{" + key + "}" + uuid.New().String()

	suite.verifyOK(client.Set(key, "hello"))

	tx := api.NewTransaction(client)
	cmd := tx.GlideClient
	cmd.Set(key, "hello")
	cmd.LPop(key)
	cmd.Del([]string{key})
	cmd.Rename(key, key2)

	options := &api.TransactionOption{
		RaiseOnError: true,
	}
	_, err := tx.ExecWithOptions(options)

	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "WRONGTYPE")
}

func (suite *GlideTestSuite) TestExecWithOptions_Timeout() {
	client := suite.defaultClient()

	tx := api.NewTransaction(client)
	cmd := tx.GlideClient
	cmd.CustomCommand([]string{"DEBUG", "sleep", "0.5"})

	options := &api.TransactionOption{
		Timeout: 100,
	}

	_, err := tx.ExecWithOptions(options)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "timed out")

	options = &api.TransactionOption{
		Timeout: 1000,
	}

	results, err := tx.ExecWithOptions(options)
	assert.NoError(suite.T(), err)

	assert.NotNil(suite.T(), results)
	resultString := anyToString(results)
	assert.Contains(suite.T(), resultString, "OK")
}

func (suite *GlideTestSuite) TestExecWithOptions_Cluster_RaiseOnErrorFalse() {
	client := suite.defaultClusterClient()
	key := uuid.New().String()

	suite.verifyOK(client.Set(key, "hello"))

	tx := api.NewClusterTransaction(client)
	cmd := tx.GlideClusterClient

	var simpleRoute config.Route = config.RandomRoute

	cmd.PingWithOptions(options.ClusterPingOptions{
		PingOptions: &options.PingOptions{Message: "test"},
		RouteOption: &options.RouteOption{Route: simpleRoute},
	})

	cmd.LPop(key)

	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Cluster}},
		RouteOption: &options.RouteOption{
			Route: simpleRoute,
		},
	}

	cmd.InfoWithOptions(opts)

	cmd.ClientGetName()

	// Test with RaiseOnError=false
	options := &api.TransactionOption{
		RaiseOnError: false,
	}
	results, err := tx.ExecWithOptions(options)
	assert.NoError(suite.T(), err)

	assert.Equal(suite.T(), 4, len(results))

	assert.Equal(suite.T(), "test", results[0])
	assert.Equal(suite.T(), "ExtensionError", results[1])

	infoResult, ok := results[2].(string)
	assert.True(suite.T(), ok, "Expected string for INFO command result with RandomRoute")
	assert.Contains(suite.T(), infoResult, "cluster")

	assert.Contains(suite.T(), results, results[3])
}

func (suite *GlideTestSuite) TestExecWithOptions_Cluster_RaiseOnErrorTrue() {
	client := suite.defaultClusterClient()
	key := uuid.New().String()

	suite.verifyOK(client.Set(key, "hello"))

	tx := api.NewClusterTransaction(client)
	cmd := tx.GlideClusterClient

	var simpleRoute config.Route = config.RandomRoute

	cmd.PingWithOptions(options.ClusterPingOptions{
		PingOptions: &options.PingOptions{Message: "test"},
		RouteOption: &options.RouteOption{Route: simpleRoute},
	})

	cmd.LPop(key)

	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Cluster}},
		RouteOption: &options.RouteOption{
			Route: simpleRoute,
		},
	}
	cmd.InfoWithOptions(opts)
	cmd.ClientGetName()

	options := &api.TransactionOption{
		RaiseOnError: true,
	}

	_, err := tx.ExecWithOptions(options)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "WRONGTYPE")
}

func (suite *GlideTestSuite) TestExecWithOptions_Cluster_Timeout() {
	client := suite.defaultClusterClient()

	tx := api.NewClusterTransaction(client)
	cmd := tx.GlideClusterClient

	var simpleRoute config.Route = config.RandomRoute

	cmd.CustomCommandWithRoute(
		[]string{"DEBUG", "sleep", "0.5"},
		simpleRoute,
	)

	options := &api.TransactionOption{
		Timeout: 100,
	}

	_, err := tx.ExecWithOptions(options)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "timed out")

	options = &api.TransactionOption{
		Timeout: 1000,
	}

	results, err := tx.ExecWithOptions(options)
	assert.NoError(suite.T(), err)

	assert.NotNil(suite.T(), results)
	resultString := anyToString(results)
	assert.Contains(suite.T(), resultString, "OK")
}
