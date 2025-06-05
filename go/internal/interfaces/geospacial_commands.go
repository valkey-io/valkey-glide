// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// GeoSpatialCommands supports commands and transactions for the "Geo Spatial Commands" group
// for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#geo-spatial
type GeoSpatialCommands interface {
	GeoAdd(ctx context.Context, key string, membersToGeospatialData map[string]options.GeospatialData) (int64, error)

	GeoAddWithOptions(
		ctx context.Context,
		key string,
		membersToGeospatialData map[string]options.GeospatialData,
		options options.GeoAddOptions,
	) (int64, error)

	GeoHash(ctx context.Context, key string, members []string) ([]models.Result[string], error)

	GeoPos(ctx context.Context, key string, members []string) ([][]float64, error)

	GeoDist(ctx context.Context, key string, member1 string, member2 string) (models.Result[float64], error)

	GeoDistWithUnit(
		ctx context.Context,
		key string,
		member1 string,
		member2 string,
		unit constants.GeoUnit,
	) (models.Result[float64], error)

	GeoSearch(
		ctx context.Context,
		key string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
	) ([]string, error)

	GeoSearchWithInfoOptions(
		ctx context.Context,
		key string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		infoOptions options.GeoSearchInfoOptions,
	) ([]options.Location, error)

	GeoSearchWithResultOptions(
		ctx context.Context,
		key string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		resultOptions options.GeoSearchResultOptions,
	) ([]string, error)

	GeoSearchWithFullOptions(
		ctx context.Context,
		key string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		resultOptions options.GeoSearchResultOptions,
		infoOptions options.GeoSearchInfoOptions,
	) ([]options.Location, error)

	GeoSearchStore(
		ctx context.Context,
		destinationKey string,
		sourceKey string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
	) (int64, error)

	GeoSearchStoreWithInfoOptions(
		ctx context.Context,
		destinationKey string,
		sourceKey string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		storeInfoOptions options.GeoSearchStoreInfoOptions,
	) (int64, error)

	GeoSearchStoreWithResultOptions(
		ctx context.Context,
		destinationKey string,
		sourceKey string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		resultOptions options.GeoSearchResultOptions,
	) (int64, error)

	GeoSearchStoreWithFullOptions(
		ctx context.Context,
		destinationKey string,
		sourceKey string,
		searchFrom options.GeoSearchOrigin,
		searchByShape options.GeoSearchShape,
		resultOptions options.GeoSearchResultOptions,
		storeInfoOptions options.GeoSearchStoreInfoOptions,
	) (int64, error)
}
