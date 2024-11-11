// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

// GlideClient is a client used for connection in Standalone mode.
type GlideClient struct {
	*baseClient
}

// NewGlideClient creates a [GlideClient] in standalone mode using the given [GlideClientConfiguration].
func NewGlideClient(config *GlideClientConfiguration) (*GlideClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &GlideClient{client}, nil
}
