// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func (suite *GlideTestSuite) TestClientPauseAllThenUnpause() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		ctx := context.Background()

		key := "clientPauseAll_then_clientUnpause_key"
		_, err := client.Set(ctx, key, "before")
		suite.Require().NoError(err)

		// TODO #6144: Simplify once ClientPause declaration moved to base class.
		switch c := client.(type) {
		case *glide.Client:
			result, err := c.ClientPause(ctx, 2*time.Second)
			suite.Require().NoError(err)
			suite.Equal("OK", result)
		case *glide.ClusterClient:
			result, err := c.ClientPause(ctx, 2*time.Second)
			suite.Require().NoError(err)
			suite.Equal("OK", result)
		}

		type stringResult struct {
			value string
			err   error
		}

		getCh := make(chan stringResult, 1)
		setCh := make(chan stringResult, 1)
		unpauseCh := make(chan stringResult, 1)

		go func() {
			v, err := client.Get(ctx, key)
			getCh <- stringResult{value: v.Value(), err: err}
		}()
		go func() {
			v, err := client.Set(ctx, key, "after")
			setCh <- stringResult{value: v, err: err}
		}()
		go func() {
			var v string
			var err error

			// TODO #6144: Simplify once ClientUnpause declaration moved to base class.
			switch c := client.(type) {
			case *glide.Client:
				v, err = c.ClientUnpause(ctx)
			case *glide.ClusterClient:
				v, err = c.ClientUnpause(ctx)
			}

			unpauseCh <- stringResult{value: v, err: err}
		}()

		// Verify that none of the commands completed while paused.
		select {
		case r := <-getCh:
			suite.Failf("GET completed while paused", "value=%q err=%v", r.value, r.err)
		case r := <-setCh:
			suite.Failf("SET completed while paused", "value=%q err=%v", r.value, r.err)
		case r := <-unpauseCh:
			suite.Failf("UNPAUSE completed while paused", "value=%q err=%v", r.value, r.err)
		case <-time.After(300 * time.Millisecond):
		}

		// Verify that all commands complete once pause expires.
		collect := func(ch <-chan stringResult, name string) stringResult {
			select {
			case r := <-ch:
				return r
			case <-time.After(5 * time.Second):
				suite.Failf(name+" did not complete", "timed out after 5s")
				return stringResult{}
			}
		}

		getRes := collect(getCh, "GET")
		setRes := collect(setCh, "SET")
		unpauseRes := collect(unpauseCh, "UNPAUSE")

		suite.NoError(getRes.err)
		suite.Equal("before", getRes.value)
		suite.NoError(setRes.err)
		suite.Equal("OK", setRes.value)
		suite.NoError(unpauseRes.err)
		suite.Equal("OK", unpauseRes.value)

		after, err := client.Get(ctx, key)
		suite.NoError(err)
		suite.Equal("after", after.Value())
	})
}

func (suite *GlideTestSuite) TestClientPauseWriteThenUnpause() {
	client := suite.defaultClient()
	ctx := context.Background()

	key := "clientPauseWrite_then_clientUnpause_key"
	_, err := client.Set(ctx, key, "before")
	suite.Require().NoError(err)

	result, err := client.ClientPauseWithMode(ctx, 2*time.Second, options.ClientPauseModeWrite)
	suite.Require().NoError(err)
	suite.Equal("OK", result)

	// Reads are not blocked by PAUSE WRITE.
	before, err := client.Get(ctx, key)
	suite.Require().NoError(err)
	suite.Equal("before", before.Value())

	type stringResult struct {
		value string
		err   error
	}

	setCh := make(chan stringResult, 1)
	go func() {
		v, err := client.Set(ctx, key, "after")
		setCh <- stringResult{value: v, err: err}
	}()

	// Verify that SET has not completed while paused.
	select {
	case r := <-setCh:
		suite.Failf("SET completed while paused", "value=%q err=%v", r.value, r.err)
	case <-time.After(300 * time.Millisecond):
	}

	// Unpause.
	unpauseResult, err := client.ClientUnpause(ctx)
	suite.Require().NoError(err)
	suite.Equal("OK", unpauseResult)

	// Verify that SET completes once unpaused.
	select {
	case r := <-setCh:
		suite.NoError(r.err)
		suite.Equal("OK", r.value)
	case <-time.After(5 * time.Second):
		suite.Fail("SET did not complete within 5s of CLIENT UNPAUSE")
	}

	after, err := client.Get(ctx, key)
	suite.NoError(err)
	suite.Equal("after", after.Value())
}

// TestClientPauseWriteThenUnpauseCluster verifies CLIENT PAUSE WRITE mode with routing for cluster client.
func (suite *GlideTestSuite) TestClientPauseWriteThenUnpauseCluster() {
	client := suite.defaultClusterClient()
	ctx := context.Background()

	key := "clientPauseWrite_then_clientUnpause_cluster_key"
	_, err := client.Set(ctx, key, "before")
	suite.Require().NoError(err)

	mode := options.ClientPauseModeWrite
	pauseOpts := options.ClientPauseClusterOptions{
		Mode:        &mode,
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	result, err := client.ClientPauseWithOptions(ctx, 2*time.Second, pauseOpts)
	suite.Require().NoError(err)
	suite.Equal("OK", result)

	// Reads are not blocked by PAUSE WRITE.
	before, err := client.Get(ctx, key)
	suite.Require().NoError(err)
	suite.Equal("before", before.Value())

	type stringResult struct {
		value string
		err   error
	}

	setCh := make(chan stringResult, 1)
	go func() {
		v, err := client.Set(ctx, key, "after")
		setCh <- stringResult{value: v, err: err}
	}()

	// Verify that SET has not completed while paused.
	select {
	case r := <-setCh:
		suite.Failf("SET completed while paused", "value=%q err=%v", r.value, r.err)
	case <-time.After(300 * time.Millisecond):
	}

	// Unpause.
	unpauseOpts := options.RouteOption{Route: config.AllPrimaries}
	unpauseResult, err := client.ClientUnpauseWithOptions(ctx, unpauseOpts)
	suite.Require().NoError(err)
	suite.Equal("OK", unpauseResult)

	// Verify that SET completes once unpaused.
	select {
	case r := <-setCh:
		suite.NoError(r.err)
		suite.Equal("OK", r.value)
	case <-time.After(5 * time.Second):
		suite.Fail("SET did not complete within 5s of CLIENT UNPAUSE")
	}

	after, err := client.Get(ctx, key)
	suite.NoError(err)
	suite.Equal("after", after.Value())
}
