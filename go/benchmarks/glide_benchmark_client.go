// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package main

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api"
)

type glideBenchmarkClient struct {
	client api.BaseClient
}

func (glideBenchmarkClient *glideBenchmarkClient) connect(connectionSettings *connectionSettings) error {
	if connectionSettings.clusterModeEnabled {
		config := api.NewGlideClusterClientConfiguration().
			WithAddress(&api.NodeAddress{Host: connectionSettings.host, Port: connectionSettings.port}).
			WithUseTLS(connectionSettings.useTLS)
		glideClient, err := api.NewGlideClusterClient(context.TODO(), config)
		if err != nil {
			return err
		}

		glideBenchmarkClient.client = glideClient
		return nil
	} else {
		config := api.NewGlideClientConfiguration().
			WithAddress(&api.NodeAddress{Host: connectionSettings.host, Port: connectionSettings.port}).
			WithUseTLS(connectionSettings.useTLS)
		glideClient, err := api.NewGlideClient(context.TODO(), config)
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
