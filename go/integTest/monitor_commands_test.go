// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
)

func (suite *GlideTestSuite) TestMonitorReceivesCommands() {
	var received []glide.MonitorLine
	var mu sync.Mutex

	monitor, err := glide.NewMonitorClient(
		suite.defaultClientConfig(),
		func(line glide.MonitorLine) {
			mu.Lock()
			defer mu.Unlock()
			received = append(received, line)
		},
	)
	require.NoError(suite.T(), err)
	defer monitor.Close()

	client := suite.defaultClient()
	key := uuid.New().String()
	_, err = client.Set(context.Background(), key, "value")
	require.NoError(suite.T(), err)

	deadline := time.Now().Add(5 * time.Second)
	found := false
	for time.Now().Before(deadline) {
		mu.Lock()
		for _, line := range received {
			if line.Command == "SET" {
				found = true
				break
			}
		}
		mu.Unlock()
		if found {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	assert.True(suite.T(), found, "SET command not found in monitor output")
}

func (suite *GlideTestSuite) TestMonitorQueue() {
	monitor, err := glide.NewMonitorClient(suite.defaultClientConfig(), nil)
	require.NoError(suite.T(), err)
	defer monitor.Close()

	client := suite.defaultClient()
	_, err = client.Ping(context.Background())
	require.NoError(suite.T(), err)

	deadline := time.Now().Add(5 * time.Second)
	var line glide.MonitorLine
	var ok bool
	for time.Now().Before(deadline) {
		line, ok = monitor.TryGetMonitorMessage()
		if ok {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	require.True(suite.T(), ok, "timed out waiting for monitor message")
	assert.NotEmpty(suite.T(), line.Command)
	assert.Greater(suite.T(), line.Timestamp, float64(0))
	assert.GreaterOrEqual(suite.T(), line.DB, int64(0))
	assert.NotEmpty(suite.T(), line.ClientAddr)
	assert.NotNil(suite.T(), line.Args)
}

func (suite *GlideTestSuite) TestMonitorGetMessageBlocking() {
	monitor, err := glide.NewMonitorClient(suite.defaultClientConfig(), nil)
	require.NoError(suite.T(), err)
	defer monitor.Close()

	client := suite.defaultClient()
	_, err = client.Ping(context.Background())
	require.NoError(suite.T(), err)

	done := make(chan glide.MonitorLine, 1)
	go func() {
		line, err := monitor.GetMonitorMessage()
		if err == nil {
			done <- line
		}
	}()

	select {
	case line := <-done:
		assert.NotEmpty(suite.T(), line.Command)
	case <-time.After(5 * time.Second):
		suite.T().Fatal("timed out waiting for monitor message")
	}
}

func (suite *GlideTestSuite) TestMonitorCloseIdempotent() {
	monitor, err := glide.NewMonitorClient(suite.defaultClientConfig(), nil)
	require.NoError(suite.T(), err)

	monitor.Close()
	monitor.Close() // Should not panic or error
}

func (suite *GlideTestSuite) TestMonitorFields() {
	monitor, err := glide.NewMonitorClient(suite.defaultClientConfig(), nil)
	require.NoError(suite.T(), err)
	defer monitor.Close()

	client := suite.defaultClient()
	key := uuid.New().String()
	_, err = client.Set(context.Background(), key, "hello")
	require.NoError(suite.T(), err)

	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		line, ok := monitor.TryGetMonitorMessage()
		if !ok {
			time.Sleep(10 * time.Millisecond)
			continue
		}
		if line.Command == "SET" {
			assert.Greater(suite.T(), line.Timestamp, float64(0))
			assert.GreaterOrEqual(suite.T(), line.DB, int64(0))
			assert.NotEmpty(suite.T(), line.ClientAddr)
			assert.Equal(suite.T(), 2, len(line.Args))
			return
		}
	}
	suite.T().Fatal("SET command not found in monitor queue within timeout")
}
