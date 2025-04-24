// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
)

func (suite *GlideTestSuite) TestWatch() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(key, "value"))
	suite.verifyOK(client.Watch([]string{"key1"}))

	tx := api.NewTransaction(client)
	cmd := tx.GlideClient
	cmd.Del([]string{key})
	result, err := tx.Exec()
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "[OK OK 1]", result)

}

func (suite *GlideTestSuite) TestUnwatch() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(key, "value"))
	suite.verifyOK(client.Unwatch())
}

func (suite *GlideTestSuite) TestExec() {
	client := suite.defaultClient()
	key := uuid.New().String()
	tx := api.NewTransaction(client)
	cmd := tx.GlideClient
	cmd.Set(key, "hello")
	cmd.Get(key)
	cmd.Del([]string{key})
	result, err := tx.Exec()
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "[OK hello 1]", result)

}
