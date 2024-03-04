// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package api

type RedisError struct {
	msg string
}

func (e *RedisError) Error() string { return e.msg }
