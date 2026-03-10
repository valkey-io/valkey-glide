// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package main

import (
	"context"

	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
)

type glideCompressedBenchmarkClient struct {
	client interfaces.BaseClientCommands
}

func (c *glideCompressedBenchmarkClient) connect(connectionSettings *connectionSettings) error {
	compressionConfig := config.NewCompressionConfiguration()

	if connectionSettings.clusterModeEnabled {
		cfg := config.NewClusterClientConfiguration().
			WithAddress(&config.NodeAddress{Host: connectionSettings.host, Port: connectionSettings.port}).
			WithUseTLS(connectionSettings.useTLS).
			WithCompressionConfiguration(compressionConfig)
		glideClient, err := glide.NewClusterClient(cfg)
		if err != nil {
			return err
		}
		c.client = glideClient
		return nil
	}

	cfg := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{Host: connectionSettings.host, Port: connectionSettings.port}).
		WithUseTLS(connectionSettings.useTLS).
		WithCompressionConfiguration(compressionConfig)
	glideClient, err := glide.NewClient(cfg)
	if err != nil {
		return err
	}
	c.client = glideClient
	return nil
}

func (c *glideCompressedBenchmarkClient) get(key string) (string, error) {
	result, err := c.client.Get(context.Background(), key)
	if err != nil {
		return "", err
	}
	return result.Value(), nil
}

func (c *glideCompressedBenchmarkClient) set(key string, value string) (string, error) {
	return c.client.Set(context.Background(), key, value)
}

func (c *glideCompressedBenchmarkClient) close() error {
	c.client.Close()
	return nil
}

func (c *glideCompressedBenchmarkClient) getName() string {
	return "glide-compressed"
}
