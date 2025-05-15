// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package main

import (
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
)

type glideBenchmarkClient struct {
	client interfaces.BaseClientCommands
}

func (glideBenchmarkClient *glideBenchmarkClient) connect(connectionSettings *connectionSettings) error {
	if connectionSettings.clusterModeEnabled {
		config := config.NewClusterClientConfiguration().
			WithAddress(&config.NodeAddress{Host: connectionSettings.host, Port: connectionSettings.port}).
			WithUseTLS(connectionSettings.useTLS)
		glideClient, err := glide.NewClusterClient(config)
		if err != nil {
			return err
		}

		glideBenchmarkClient.client = glideClient
		return nil
	} else {
		config := config.NewClientConfiguration().
			WithAddress(&config.NodeAddress{Host: connectionSettings.host, Port: connectionSettings.port}).
			WithUseTLS(connectionSettings.useTLS)
		glideClient, err := glide.NewClient(config)
		if err != nil {
			return err
		}

		glideBenchmarkClient.client = glideClient
		return nil
	}
}

func (glideBenchmarkClient *glideBenchmarkClient) get(key string) (string, error) {
	result, err := glideBenchmarkClient.client.Get(key)
	if err != nil {
		return "", err
	}

	return result.Value(), nil
}

func (glideBenchmarkClient *glideBenchmarkClient) set(key string, value string) (string, error) {
	return glideBenchmarkClient.client.Set(key, value)
}

func (glideBenchmarkClient *glideBenchmarkClient) close() error {
	glideBenchmarkClient.client.Close()
	return nil
}

func (glideBenchmarkClient *glideBenchmarkClient) getName() string {
	return "glide"
}
