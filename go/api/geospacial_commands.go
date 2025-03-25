// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// GeoSpatialCommands supports commands and transactions for the "Geo Spatial Commands" group
// for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#geo-spatial
type GeoSpatialCommands interface {
	GeoAdd(key string, membersToGeospatialData map[string]options.GeospatialData) (int64, error)

	GeoAddWithOptions(
		key string,
		membersToGeospatialData map[string]options.GeospatialData,
		options options.GeoAddOptions,
	) (int64, error)

	GeoHash(key string, members []string) ([]string, error)

	GeoPos(key string, members []string) ([][]float64, error)
}
