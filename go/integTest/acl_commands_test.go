// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strings"

	"github.com/stretchr/testify/assert"
)

func (suite *GlideTestSuite) TestAclCat() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclCat - get all categories
	categories, err := client.AclCat(context.Background())
	assert.NoError(t, err)
	assert.Greater(t, len(categories), 0)
	// Common categories that should exist
	assert.Contains(t, categories, "read")
	assert.Contains(t, categories, "write")
	assert.Contains(t, categories, "string")
}

func (suite *GlideTestSuite) TestAclCat_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclCat - get all categories
	categories, err := client.AclCat(context.Background())
	assert.NoError(t, err)
	assert.Greater(t, len(categories), 0)
	// Common categories that should exist
	assert.Contains(t, categories, "read")
	assert.Contains(t, categories, "write")
	assert.Contains(t, categories, "string")
}

func (suite *GlideTestSuite) TestAclCatWithCategory() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclCatWithCategory - get commands in "string" category
	commands, err := client.AclCatWithCategory(context.Background(), "string")
	assert.NoError(t, err)
	assert.Greater(t, len(commands), 0)
	// GET and SET should be in string category
	assert.Contains(t, commands, "get")
	assert.Contains(t, commands, "set")
}

func (suite *GlideTestSuite) TestAclCatWithCategory_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclCatWithCategory - get commands in "string" category
	commands, err := client.AclCatWithCategory(context.Background(), "string")
	assert.NoError(t, err)
	assert.Greater(t, len(commands), 0)
	// GET and SET should be in string category
	assert.Contains(t, commands, "get")
	assert.Contains(t, commands, "set")
}

func (suite *GlideTestSuite) TestAclGenPass() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclGenPass - generate default password
	password, err := client.AclGenPass(context.Background())
	assert.NoError(t, err)
	// Default is 256 bits = 64 hex characters
	assert.Len(t, password, 64)

	// Test AclGenPassWithBits - generate password with specific bits
	password128, err := client.AclGenPassWithBits(context.Background(), 128)
	assert.NoError(t, err)
	// 128 bits = 32 hex characters
	assert.Len(t, password128, 32)
}

func (suite *GlideTestSuite) TestAclGenPass_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclGenPass - generate default password
	password, err := client.AclGenPass(context.Background())
	assert.NoError(t, err)
	// Default is 256 bits = 64 hex characters
	assert.Len(t, password, 64)

	// Test AclGenPassWithBits - generate password with specific bits
	password128, err := client.AclGenPassWithBits(context.Background(), 128)
	assert.NoError(t, err)
	// 128 bits = 32 hex characters
	assert.Len(t, password128, 32)
}

func (suite *GlideTestSuite) TestAclUsers() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclUsers - get all usernames
	users, err := client.AclUsers(context.Background())
	assert.NoError(t, err)
	assert.Greater(t, len(users), 0)
	// Default user should always exist
	assert.Contains(t, users, "default")
}

func (suite *GlideTestSuite) TestAclUsers_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclUsers - get all usernames
	users, err := client.AclUsers(context.Background())
	assert.NoError(t, err)
	assert.Greater(t, len(users), 0)
	// Default user should always exist
	assert.Contains(t, users, "default")
}

func (suite *GlideTestSuite) TestAclWhoAmI() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclWhoAmI - get current username
	username, err := client.AclWhoAmI(context.Background())
	assert.NoError(t, err)
	// Should be "default" unless authenticated as different user
	assert.NotEmpty(t, username)
}

func (suite *GlideTestSuite) TestAclWhoAmI_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclWhoAmI - get current username
	username, err := client.AclWhoAmI(context.Background())
	assert.NoError(t, err)
	// Should be "default" unless authenticated as different user
	assert.NotEmpty(t, username)
}

func (suite *GlideTestSuite) TestAclList() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclList - get all ACL rules
	aclList, err := client.AclList(context.Background())
	assert.NoError(t, err)
	assert.Greater(t, len(aclList), 0)
	// Should contain at least the default user rule
	found := false
	for _, rule := range aclList {
		if strings.Contains(rule, "default") {
			found = true
			break
		}
	}
	assert.True(t, found, "ACL list should contain default user")
}

func (suite *GlideTestSuite) TestAclList_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclList - get all ACL rules
	aclList, err := client.AclList(context.Background())
	assert.NoError(t, err)
	assert.Greater(t, len(aclList), 0)
	// Should contain at least the default user rule
	found := false
	for _, rule := range aclList {
		if strings.Contains(rule, "default") {
			found = true
			break
		}
	}
	assert.True(t, found, "ACL list should contain default user")
}

func (suite *GlideTestSuite) TestAclGetUser() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclGetUser - get default user info
	userInfo, err := client.AclGetUser(context.Background(), "default")
	assert.NoError(t, err)
	assert.NotNil(t, userInfo)

	// Test non-existent user
	nonExistent, err := client.AclGetUser(context.Background(), "nonexistentuser12345")
	assert.NoError(t, err)
	assert.Nil(t, nonExistent)
}

func (suite *GlideTestSuite) TestAclGetUser_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclGetUser - get default user info
	userInfo, err := client.AclGetUser(context.Background(), "default")
	assert.NoError(t, err)
	assert.NotNil(t, userInfo)

	// Test non-existent user
	nonExistent, err := client.AclGetUser(context.Background(), "nonexistentuser12345")
	assert.NoError(t, err)
	assert.Nil(t, nonExistent)
}

func (suite *GlideTestSuite) TestAclSetUserAndDelUser() {
	client := suite.defaultClient()
	t := suite.T()

	testUser := "testuser_acl_" + suite.GenerateLargeUuid()[:8]

	// Test AclSetUser - create a new user
	result, err := client.AclSetUser(context.Background(), testUser, []string{"on", ">testpass", "+get", "~*"})
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Verify user was created
	users, err := client.AclUsers(context.Background())
	assert.NoError(t, err)
	assert.Contains(t, users, testUser)

	// Test AclDelUser - delete the user
	deleted, err := client.AclDelUser(context.Background(), []string{testUser})
	assert.NoError(t, err)
	assert.Equal(t, int64(1), deleted)

	// Verify user was deleted
	users, err = client.AclUsers(context.Background())
	assert.NoError(t, err)
	assert.NotContains(t, users, testUser)
}

func (suite *GlideTestSuite) TestAclSetUserAndDelUser_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	testUser := "testuser_acl_" + suite.GenerateLargeUuid()[:8]

	// Test AclSetUser - create a new user
	result, err := client.AclSetUser(context.Background(), testUser, []string{"on", ">testpass", "+get", "~*"})
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Verify user was created
	users, err := client.AclUsers(context.Background())
	assert.NoError(t, err)
	assert.Contains(t, users, testUser)

	// Test AclDelUser - delete the user
	deleted, err := client.AclDelUser(context.Background(), []string{testUser})
	assert.NoError(t, err)
	assert.Equal(t, int64(1), deleted)

	// Verify user was deleted
	users, err = client.AclUsers(context.Background())
	assert.NoError(t, err)
	assert.NotContains(t, users, testUser)
}

func (suite *GlideTestSuite) TestAclDryRun() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclDryRun - simulate command for default user
	result, err := client.AclDryRun(context.Background(), "default", "GET", []string{"testkey"})
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestAclDryRun_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclDryRun - simulate command for default user
	result, err := client.AclDryRun(context.Background(), "default", "GET", []string{"testkey"})
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestAclLog() {
	client := suite.defaultClient()
	t := suite.T()

	// Test AclLog - get ACL log (may be empty if no security events)
	log, err := client.AclLog(context.Background())
	assert.NoError(t, err)
	assert.NotNil(t, log)

	// Test AclLogWithCount
	logWithCount, err := client.AclLogWithCount(context.Background(), 5)
	assert.NoError(t, err)
	assert.NotNil(t, logWithCount)
	assert.LessOrEqual(t, len(logWithCount), 5)

	// Test AclLogReset
	resetResult, err := client.AclLogReset(context.Background())
	assert.NoError(t, err)
	assert.Equal(t, "OK", resetResult)
}

func (suite *GlideTestSuite) TestAclLog_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Test AclLog - get ACL log (may be empty if no security events)
	log, err := client.AclLog(context.Background())
	assert.NoError(t, err)
	assert.NotNil(t, log)

	// Test AclLogWithCount
	logWithCount, err := client.AclLogWithCount(context.Background(), 5)
	assert.NoError(t, err)
	assert.NotNil(t, logWithCount)
	assert.LessOrEqual(t, len(logWithCount), 5)

	// Test AclLogReset
	resetResult, err := client.AclLogReset(context.Background())
	assert.NoError(t, err)
	assert.Equal(t, "OK", resetResult)
}
