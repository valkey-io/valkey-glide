// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// TestContext_CancelBeforeExecution tests what happens when the context is
// cancelled before any command is executed
func (suite *GlideTestSuite) TestContext_CancelBeforeExecution() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create a context that's already cancelled
		ctx, cancel := context.WithCancel(context.Background())
		cancel() // Cancel immediately

		// Try to execute BLPop command with the cancelled context
		_, err := client.BLPop(ctx, []string{"testList"}, 10.0)

		// Verify that the command fails with context cancellation error
		assert.Error(suite.T(), err)
		assert.Equal(suite.T(), context.Canceled.Error(), err.Error())
	})
}

// TestContext_CancelDuringExecution tests what happens when the context is
// cancelled while a command is being executed
func (suite *GlideTestSuite) TestContext_CancelDuringExecution() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create a context with a short timeout
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
		defer cancel()

		// Start a long-running command (BLPOP) that will block for a while
		_, err := client.BLPop(ctx, []string{"testList"}, 10.0)

		// Verify that the command fails with context deadline exceeded error
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), err.Error(), "context deadline exceeded")
	})
}

// TestContext_CancelWithConnectionPasswordUpdate tests context cancellation
// with connection password update operation
func (suite *GlideTestSuite) TestContext_CancelWithConnectionPasswordUpdate() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create a context that's already cancelled
		ctx, cancel := context.WithCancel(context.Background())
		cancel() // Cancel immediately

		// Try to update the password with a cancelled context
		var err error

		switch c := client.(type) {
		case interfaces.GlideClientCommands:
			// Try to update password with cancelled context
			_, err = c.UpdateConnectionPassword(ctx, "dummy_password", true)
		case interfaces.GlideClusterClientCommands:
			// Try to update password with cancelled context
			_, err = c.UpdateConnectionPassword(ctx, "dummy_password", true)
		default:
			suite.T().Fatalf("Unexpected client type: %T", client)
		}

		// Verify that the command fails with context cancellation error
		assert.Error(suite.T(), err)
		assert.Equal(suite.T(), context.Canceled.Error(), err.Error())
	})
}

// TestContext_CancelWithScan tests context cancellation with scan operation
// which is specifically handled in the cluster client
func (suite *GlideTestSuite) TestContext_CancelWithScan() {
	suite.runWithSpecificClients(ClusterFlag, func(client interfaces.BaseClientCommands) {
		clusterClient, _ := client.(interfaces.GlideClusterClientCommands)

		// Create a context that's already cancelled
		ctx, cancel := context.WithCancel(context.Background())
		cancel() // Cancel immediately

		// Create a new cluster scan cursor
		cursor := options.NewClusterScanCursor()

		// Try to perform a scan with cancelled context
		scanOpts := options.NewClusterScanOptions().
			SetMatch("*").
			SetCount(10)

		// Use the correct API
		_, _, err := clusterClient.ScanWithOptions(ctx, *cursor, *scanOpts)

		// Verify that the command fails with context cancellation error
		assert.Error(suite.T(), err)
		assert.Equal(suite.T(), context.Canceled.Error(), err.Error())
	})
}
