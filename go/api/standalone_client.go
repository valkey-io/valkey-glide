/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

// RedisClient is a client used for connection to standalone Redis servers.
type RedisClient struct {
	*baseClient
}

// NewRedisClient creates a [RedisClient] in standalone mode using the given [RedisClientConfiguration].
func NewRedisClient(config *RedisClientConfiguration) (*RedisClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &RedisClient{client}, nil
}

// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command, including
// the command name and subcommands, should be added as a separate value in args. The returning value depends on the executed
// command.
//
// This function should only be used for single-response commands. Commands that don't return response (such as SUBSCRIBE), or
// that return potentially more than a single response (such as XREAD), or that change the client's behavior (such as entering
// pub/sub mode on RESP2 connections) shouldn't be called using this function.
//
// For example, to return a list of all pub/sub clients:
//
//	client.CustomCommand([]string{"CLIENT", "LIST","TYPE", "PUBSUB"})
func (client *RedisClient) CustomCommand(args []string) (interface{}, error) {
	return client.executeCommand(C.CustomCommand, args)
}
