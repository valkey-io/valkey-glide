// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

type BaseOptions interface {
	ToArgs() ([]string, error)
}
