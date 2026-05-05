// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/internal/protobuf"
)

// MinCompressionSize is the absolute minimum allowed value for CompressionConfiguration.MinCompressionSize.
// This corresponds to the compression header size (5 bytes) plus 1 byte of payload.
// TO-DO: Update this to be something more descriptive and less confusing, MinMinCompressionSize ?
// Should be kept in sync with the Rust core's MIN_COMPRESSED_SIZE (HEADER_SIZE + 1).
const MinCompressionSize = 6

// DefaultMinCompressionSize is the default threshold below which values will not be compressed.
// Operators can lower this to MinCompressionSize if they have highly compressible small values.
const DefaultMinCompressionSize = 64

// DefaultMaxDecompressedSize is the default maximum decompressed size (512MB, matching Valkey's proto-max-bulk-len).
const DefaultMaxDecompressedSize uint64 = 512 * 1024 * 1024

// CompressionBackend represents the compression algorithm to use.
type CompressionBackend int

const (
	// ZSTD uses the Zstandard compression algorithm. Default compression level is 3.
	ZSTD CompressionBackend = iota
	// LZ4 uses the LZ4 compression algorithm. Default compression level is 0.
	LZ4
)

// CompressionConfiguration represents the configuration for automatic value compression.
//
// When enabled, values sent to the server will be compressed using the specified backend
// if they meet the minimum size threshold. Compressed values are automatically decompressed
// on retrieval.
type CompressionConfiguration struct {
	// Whether compression is enabled.
	enabled bool
	// The compression backend to use. Defaults to ZSTD.
	backend CompressionBackend
	// The compression level. If nil, the backend's default level is used.
	// Valid ranges are backend-specific and validated by the Rust core.
	compressionLevel *int32
	// Minimum size in bytes for values to be compressed. Defaults to 64.
	minCompressionSize uint32
	// Maximum allowed size in bytes for decompressed data.
	// This limit prevents decompression bombs (maliciously crafted compressed data
	// that expands to huge sizes).
	// If nil, the Rust default (512MB) is used.
	maxDecompressedSize *uint64
}

// NewCompressionConfiguration returns a [CompressionConfiguration] with compression enabled,
// ZSTD backend, default compression level, and minimum compression size of 64 bytes.
// The max decompressed size defaults to the Rust core's default (512MB).
func NewCompressionConfiguration() *CompressionConfiguration {
	return &CompressionConfiguration{
		enabled:             true,
		backend:             ZSTD,
		minCompressionSize:  DefaultMinCompressionSize,
		maxDecompressedSize: nil, // Use Rust default
	}
}

// WithEnabled sets whether compression is enabled.
// This allows toggling compression on or off without removing the configuration entirely.
func (c *CompressionConfiguration) WithEnabled(enabled bool) *CompressionConfiguration {
	c.enabled = enabled
	return c
}

// WithBackend sets the compression backend.
func (c *CompressionConfiguration) WithBackend(backend CompressionBackend) *CompressionConfiguration {
	c.backend = backend
	return c
}

// WithCompressionLevel sets the compression level. Valid ranges are backend-specific:
// ZSTD supports levels from -131072 to 22 (default 3).
// LZ4 supports levels from -128 to 12 (default 0).
func (c *CompressionConfiguration) WithCompressionLevel(level int32) *CompressionConfiguration {
	c.compressionLevel = &level
	return c
}

// WithMinCompressionSize sets the minimum size in bytes for values to be compressed.
// Must be at least MinCompressionSize (6) bytes. Defaults to DefaultMinCompressionSize (64) bytes.
func (c *CompressionConfiguration) WithMinCompressionSize(size uint32) *CompressionConfiguration {
	c.minCompressionSize = size
	return c
}

// WithMaxDecompressedSize sets the maximum allowed size in bytes for decompressed data.
// This limit prevents decompression bombs (maliciously crafted compressed data that expands to huge sizes).
// If nil, the Rust default (512MB) is used.
func (c *CompressionConfiguration) WithMaxDecompressedSize(size *uint64) *CompressionConfiguration {
	c.maxDecompressedSize = size
	return c
}

// Validate checks that the compression configuration is valid.
func (c *CompressionConfiguration) Validate() error {
	if c.minCompressionSize < MinCompressionSize {
		return fmt.Errorf(
			"min_compression_size must be at least %d bytes, got %d",
			MinCompressionSize,
			c.minCompressionSize,
		)
	}

	if c.maxDecompressedSize != nil && *c.maxDecompressedSize == 0 {
		return fmt.Errorf("max_decompressed_size must be positive if set")
	}

	return nil
}

func (c *CompressionConfiguration) toProtobuf() (*protobuf.CompressionConfig, error) {
	if err := c.Validate(); err != nil {
		return nil, err
	}

	pbConfig := &protobuf.CompressionConfig{
		Enabled:            c.enabled,
		Backend:            protobuf.CompressionBackend(c.backend),
		MinCompressionSize: c.minCompressionSize,
	}

	if c.compressionLevel != nil {
		pbConfig.CompressionLevel = c.compressionLevel
	}

	if c.maxDecompressedSize != nil {
		pbConfig.MaxDecompressedSize = c.maxDecompressedSize
	}

	return pbConfig, nil
}
