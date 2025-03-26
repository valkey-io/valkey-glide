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

	GeoDist(key string, member1 string, member2 string) (Result[float64], error)

	GeoDistWithUnit(key string, member1 string, member2 string, unit options.GeoUnit) (Result[float64], error)

	GeoSearch(
		key string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
	) ([]string, error)

	GeoSearchWithInfoOptions(
		key string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		infoOptions options.GeoSearchInfoOptions,
	) ([]options.Location, error)

	GeoSearchWithResultOptions(
		key string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		resultOptions options.GeoSearchResultOptions,
	) ([]string, error)

	GeoSearchWithFullOptions(
		key string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		resultOptions options.GeoSearchResultOptions,
		infoOptions options.GeoSearchInfoOptions,
	) ([]options.Location, error)

	GeoSearchStore(
		destinationKey string,
		sourceKey string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
	) (int64, error)

	GeoSearchStoreWithInfoOptions(
		destinationKey string,
		sourceKey string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		storeInfoOptions options.GeoSearchStoreInfoOptions,
	) (int64, error)

	GeoSearchStoreWithResultOptions(
		destinationKey string,
		sourceKey string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		resultOptions options.GeoSearchResultOptions,
	) (int64, error)

	GeoSearchStoreWithFullOptions(
		destinationKey string,
		sourceKey string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		resultOptions options.GeoSearchResultOptions,
		storeInfoOptions options.GeoSearchStoreInfoOptions,
	) (int64, error)
}
