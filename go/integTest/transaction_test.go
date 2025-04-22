// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"github.com/google/uuid"
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
}

func (suite *GlideTestSuite) TestUnwatch() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(key, "value"))
	suite.verifyOK(client.Unwatch())
}
