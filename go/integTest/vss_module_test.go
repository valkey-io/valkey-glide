// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strings"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func (suite *GlideTestSuite) TestModuleVerifyVssLoaded() {
	client := suite.defaultClusterClient()
	result, err := client.InfoWithOptions(context.Background(),
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.Server}},
			RouteOption: nil,
		},
	)

	suite.NoError(err)
	for _, value := range result.MultiValue() {
		assert.True(suite.T(), strings.Contains(value, "# search_index_stats"))
	}
}
