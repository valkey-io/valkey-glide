// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/stretchr/testify/assert"
)

func (suite *GlideTestSuite) TestModuleVerifyJsonLoaded() {
	client := suite.defaultClusterClient()
	// TODO use INFO command
	result, err := client.CustomCommand([]string{"INFO", "MODULES"})

	assert.Nil(suite.T(), err)
	for _, value := range result.Value().(map[string]interface{}) {
		assert.True(suite.T(), strings.Contains(value.(string), "# json_core_metrics"))
	}
}
