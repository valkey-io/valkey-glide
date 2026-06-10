// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strings"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

var (
	bgsaveResponses = []string{
		"Background saving started",
		"Background saving scheduled",
	}
	bgrewriteaofResponses = []string{
		"Background append only file rewriting started",
		"Background append only file rewriting scheduled",
	}
)

func containsAny(response string, candidates []string) bool {
	for _, candidate := range candidates {
		if response == candidate {
			return true
		}
	}
	return false
}

func clusterInfoToString(info map[string]string) string {
	values := make([]string, 0, len(info))
	for _, value := range info {
		values = append(values, value)
	}
	return strings.Join(values, "\n")
}

func (suite *GlideTestSuite) waitUntilSaveNotInProgress(getInfo func() (string, error)) {
	suite.T().Helper()
	deadline := time.Now().Add(30 * time.Second)
	for time.Now().Before(deadline) {
		info, err := getInfo()
		if err != nil {
			time.Sleep(100 * time.Millisecond)
			continue
		}
		if !strings.Contains(info, "rdb_bgsave_in_progress:1") &&
			!strings.Contains(info, "aof_rewrite_in_progress:1") {
			return
		}
		time.Sleep(100 * time.Millisecond)
	}
	suite.T().Fatal("timed out waiting for background save or AOF rewrite to finish")
}

func (suite *GlideTestSuite) TestSaveStandalone() {
	client := suite.defaultClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		return client.Info(context.Background())
	})
	suite.verifyOK(client.Save(context.Background()))
}

func (suite *GlideTestSuite) TestBgsaveStandalone() {
	client := suite.defaultClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		return client.Info(context.Background())
	})
	response, err := client.Bgsave(context.Background())
	require.NoError(suite.T(), err)
	assert.True(suite.T(), containsAny(response, bgsaveResponses), "unexpected response: %s", response)
}

func (suite *GlideTestSuite) TestBgRewriteAofStandalone() {
	client := suite.defaultClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		return client.Info(context.Background())
	})
	response, err := client.BgRewriteAof(context.Background())
	require.NoError(suite.T(), err)
	assert.True(
		suite.T(),
		containsAny(response, bgrewriteaofResponses),
		"unexpected response: %s",
		response,
	)
}

func (suite *GlideTestSuite) TestReplicaOfNoOneStandalone() {
	client := suite.defaultClient()
	suite.verifyOK(client.ReplicaOfNoOne(context.Background()))
}

func (suite *GlideTestSuite) TestSaveCluster() {
	client := suite.defaultClusterClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		info, err := client.Info(context.Background())
		if err != nil {
			return "", err
		}
		return clusterInfoToString(info), nil
	})
	suite.verifyOK(client.Save(context.Background()))
}

func (suite *GlideTestSuite) TestSaveWithOptionsCluster() {
	client := suite.defaultClusterClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		info, err := client.Info(context.Background())
		if err != nil {
			return "", err
		}
		return clusterInfoToString(info), nil
	})
	suite.verifyOK(client.SaveWithOptions(context.Background(), options.RouteOption{Route: config.AllPrimaries}))
}

func (suite *GlideTestSuite) TestBgsaveCluster() {
	client := suite.defaultClusterClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		info, err := client.Info(context.Background())
		if err != nil {
			return "", err
		}
		return clusterInfoToString(info), nil
	})
	response, err := client.Bgsave(context.Background())
	require.NoError(suite.T(), err)
	for _, value := range response.MultiValue() {
		assert.True(suite.T(), containsAny(value, bgsaveResponses), "unexpected response: %s", value)
	}
}

func (suite *GlideTestSuite) TestBgsaveWithOptionsCluster() {
	client := suite.defaultClusterClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		info, err := client.Info(context.Background())
		if err != nil {
			return "", err
		}
		return clusterInfoToString(info), nil
	})
	response, err := client.BgsaveWithOptions(
		context.Background(),
		options.RouteOption{Route: config.AllPrimaries},
	)
	require.NoError(suite.T(), err)
	for _, value := range response.MultiValue() {
		assert.True(suite.T(), containsAny(value, bgsaveResponses), "unexpected response: %s", value)
	}
}

func (suite *GlideTestSuite) TestBgRewriteAofCluster() {
	client := suite.defaultClusterClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		info, err := client.Info(context.Background())
		if err != nil {
			return "", err
		}
		return clusterInfoToString(info), nil
	})
	response, err := client.BgRewriteAof(context.Background())
	require.NoError(suite.T(), err)
	for _, value := range response.MultiValue() {
		assert.True(
			suite.T(),
			containsAny(value, bgrewriteaofResponses),
			"unexpected response: %s",
			value,
		)
	}
}

func (suite *GlideTestSuite) TestBgRewriteAofWithOptionsCluster() {
	client := suite.defaultClusterClient()
	suite.waitUntilSaveNotInProgress(func() (string, error) {
		info, err := client.Info(context.Background())
		if err != nil {
			return "", err
		}
		return clusterInfoToString(info), nil
	})
	response, err := client.BgRewriteAofWithOptions(
		context.Background(),
		options.RouteOption{Route: config.AllPrimaries},
	)
	require.NoError(suite.T(), err)
	for _, value := range response.MultiValue() {
		assert.True(
			suite.T(),
			containsAny(value, bgrewriteaofResponses),
			"unexpected response: %s",
			value,
		)
	}
}

func (suite *GlideTestSuite) TestSaveShared() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		switch c := client.(type) {
		case interfaces.GlideClientCommands:
			suite.waitUntilSaveNotInProgress(func() (string, error) {
				return c.Info(context.Background())
			})
			suite.verifyOK(c.Save(context.Background()))
		case interfaces.GlideClusterClientCommands:
			suite.waitUntilSaveNotInProgress(func() (string, error) {
				info, err := c.Info(context.Background())
				if err != nil {
					return "", err
				}
				return clusterInfoToString(info), nil
			})
			suite.verifyOK(c.Save(context.Background()))
		}
	})
}
