/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

package api

import "C"

// RedisClient is a client used for connection to standalone Redis servers.
type RedisClient struct {
	*baseClient
}

// CreateClient creates a Redis client in standalone mode using the given [RedisClientConfiguration].
func CreateClient(config *RedisClientConfiguration) (*RedisClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &RedisClient{client}, nil
}
