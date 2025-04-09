// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"github.com/google/uuid"
)

func (suite *GlideTestSuite) TestWatch() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(key, "value"))
	suite.verifyOK(client.Watch([]string{"key1"}))
}

func (suite *GlideTestSuite) TestUnwatch() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(key, "value"))
	suite.verifyOK(client.Unwatch())
}
