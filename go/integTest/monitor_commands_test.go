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

	time.Sleep(500 * time.Millisecond)

	mu.Lock()
	defer mu.Unlock()
	var commands []string
	for _, line := range received {
		commands = append(commands, line.Command)
	}
	assert.Contains(suite.T(), commands, "SET")
}

func (suite *GlideTestSuite) TestMonitorQueue() {
	monitor, err := glide.NewMonitorClient(suite.defaultClientConfig(), nil)
	require.NoError(suite.T(), err)
	defer monitor.Close()

	client := suite.defaultClient()
	_, err = client.Ping(context.Background())
	require.NoError(suite.T(), err)

	time.Sleep(500 * time.Millisecond)

	line, ok := monitor.TryGetMonitorMessage()
	assert.True(suite.T(), ok)
	assert.NotEmpty(suite.T(), line.Command)
	assert.Greater(suite.T(), line.Timestamp, float64(0))
	assert.GreaterOrEqual(suite.T(), line.DB, int64(0))
	assert.NotEmpty(suite.T(), line.ClientAddr)
	assert.NotEmpty(suite.T(), line.Command)
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

	time.Sleep(500 * time.Millisecond)

	for i := 0; i < 10; i++ {
		line, ok := monitor.TryGetMonitorMessage()
		if !ok {
			break
		}
		if line.Command == "SET" {
			assert.Greater(suite.T(), line.Timestamp, float64(0))
			assert.GreaterOrEqual(suite.T(), line.DB, int64(0))
			assert.NotEmpty(suite.T(), line.ClientAddr)
			assert.Equal(suite.T(), 2, len(line.Args))
			return
		}
	}
	suite.T().Fatal("SET command not found in monitor queue within expected messages")
}
