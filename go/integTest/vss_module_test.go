// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func (suite *GlideTestSuite) TestModuleVerifyVssLoaded() {
	client := suite.defaultClusterClient()
	result, err := client.InfoWithOptions(
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Server}},
			RouteOption: nil,
		},
	)

	assert.Nil(suite.T(), err)
	for _, value := range result.MultiValue() {
		assert.True(suite.T(), strings.Contains(value, "# search_index_stats"))
	}
}
