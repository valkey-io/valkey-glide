// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"

	"github.com/valkey-io/valkey-glide/go/utils"
)

// return type for the `GeoSearch` command
type Location struct {
	Name  string
	Coord GeospatialData
	Dist  float64
	Hash  int64
}

// The interface representing origin of the search for the `GeoSearch` command
type GeoSearchOrigin interface {
	ToArgs() ([]string, error)
}

// The search origin represented by a [GeospatialData] position
type GeoCoordOrigin struct {
	GeospatialData GeospatialData
}

// Converts the [GeoCoordOrigin] to the arguments for the `GeoSearch` command
func (o *GeoCoordOrigin) ToArgs() ([]string, error) {
	return []string{
		GeoCoordOriginAPIKeyword,
		utils.FloatToString(o.GeospatialData.Longitude),
		utils.FloatToString(o.GeospatialData.Latitude),
	}, nil
}

// The search origin represented by an existing member in the sorted set
type GeoMemberOrigin struct {
	Member string
}

// Converts the [GeoMemberOrigin] to the arguments for the `GeoSearch` command
func (o *GeoMemberOrigin) ToArgs() ([]string, error) {
	return []string{
		GeoMemberOriginAPIKeyword,
		o.Member,
	}, nil
}

// The search options for the `GeoSearch` command
type GeoSearchShape struct {
	shape  SearchShape
	radius float64
	width  float64
	height float64
	unit   GeoUnit
}

// Creates a new [GeoSearchShape] for a circle search by radius
func NewCircleSearchShape(radius float64, unit GeoUnit) *GeoSearchShape {
	return &GeoSearchShape{
		shape:  BYRADIUS,
		radius: radius,
		width:  0,
		height: 0,
		unit:   unit,
	}
}

// Creates a new [GeoSearchShape] for a box search by width and height
func NewBoxSearchShape(width float64, height float64, unit GeoUnit) *GeoSearchShape {
	return &GeoSearchShape{
		shape:  BYBOX,
		width:  width,
		height: height,
		unit:   unit,
	}
}

// Converts the [GeoSearchShape] to the arguments for the `GeoSearch` command
func (o *GeoSearchShape) ToArgs() ([]string, error) {
	switch o.shape {
	case BYRADIUS:
		return []string{string(o.shape), utils.FloatToString(o.radius), string(o.unit)}, nil
	case BYBOX:
		return []string{
			string(o.shape),
			utils.FloatToString(o.width),
			utils.FloatToString(o.height),
			string(o.unit),
		}, nil
	}
	return nil, errors.New("invalid geosearch shape")
}

// Optional arguments for the `GeoSearch` command
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
type GeoSearchInfoOptions struct {
	WithDist  bool
	WithCoord bool
	WithHash  bool
}

// Creates a new [GeoSearchInfoOptions] with the default options
func NewGeoSearchInfoOptions() *GeoSearchInfoOptions {
	return &GeoSearchInfoOptions{
		WithDist:  false,
		WithCoord: false,
		WithHash:  false,
	}
}

// WITHDIST: GeoSearch also return the distance of the returned items from the specified center point.
//
// The distance is returned in the same unit as specified for the `searchBy` argument.
func (o *GeoSearchInfoOptions) SetWithDist(withDist bool) *GeoSearchInfoOptions {
	o.WithDist = withDist
	return o
}

// WITHCOORD: GeoSearch also return the coordinate of the returned items.
func (o *GeoSearchInfoOptions) SetWithCoord(withCoord bool) *GeoSearchInfoOptions {
	o.WithCoord = withCoord
	return o
}

// WITHHASH: GeoSearch also return the geohash of the returned items.
func (o *GeoSearchInfoOptions) SetWithHash(withHash bool) *GeoSearchInfoOptions {
	o.WithHash = withHash
	return o
}

// Returns the arguments for the `GeoSearch` command
func (o *GeoSearchInfoOptions) ToArgs() ([]string, error) {
	args := []string{}

	if o.WithDist {
		args = append(args, WithDistValkeyApi)
	}
	if o.WithCoord {
		args = append(args, WithCoordValkeyApi)
	}
	if o.WithHash {
		args = append(args, WithHashValkeyApi)
	}
	return args, nil
}

// Optional arguments for `GeoSearch` that contains up to 2 optional inputs
type GeoSearchResultOptions struct {
	sortOrder  OrderBy
	count      int64
	countIsSet bool
	isAny      bool
}

func NewGeoSearchResultOptions() *GeoSearchResultOptions {
	return &GeoSearchResultOptions{
		sortOrder:  "",
		count:      0,
		countIsSet: false,
		isAny:      false,
	}
}

// Optional argument for `GeoSearch` that sets the query's order to sort the results by:
// - ASC: Sort returned items from the nearest to the farthest, relative to the center point.
// - DESC: Sort returned items from the farthest to the nearest, relative to the center point.
func (o *GeoSearchResultOptions) SetSortOrder(sortOrder OrderBy) *GeoSearchResultOptions {
	o.sortOrder = sortOrder
	return o
}

// Optional argument for `GeoSearch` that sets the number of results to return.
func (o *GeoSearchResultOptions) SetCount(count int64) *GeoSearchResultOptions {
	o.count = count
	o.countIsSet = true
	return o
}

// Optional argument for `GeoSearch` that sets the query to return any results.
func (o *GeoSearchResultOptions) SetIsAny(isAny bool) *GeoSearchResultOptions {
	o.isAny = isAny
	return o
}

// Converts the [GeoSearchResultOptions] to a string array of arguments for the `GeoSearch` command
func (o *GeoSearchResultOptions) ToArgs() ([]string, error) {
	args := []string{}

	if o.sortOrder != "" {
		args = append(args, string(o.sortOrder))
	}

	if o.countIsSet {
		args = append(args, CountKeyword)
		args = append(args, utils.IntToString(o.count))

		if o.isAny {
			args = append(args, "ANY")
		}
	}

	return args, nil
}

const StoreDistAPIKeyword = "STOREDIST"

// Optional arguments for `GeoSearchStore` that contains up to 1 optional input
type GeoSearchStoreInfoOptions struct {
	StoreDist bool
}

func NewGeoSearchStoreInfoOptions() *GeoSearchStoreInfoOptions {
	return &GeoSearchStoreInfoOptions{
		StoreDist: false,
	}
}

// Optional argument for `GeoSearchStore` that sets the query to store the distance of the returned items.
func (o *GeoSearchStoreInfoOptions) SetStoreDist(storeDist bool) *GeoSearchStoreInfoOptions {
	o.StoreDist = storeDist
	return o
}

// Returns the arguments for the `GeoSearchStore` command
func (o *GeoSearchStoreInfoOptions) ToArgs() ([]string, error) {
	args := []string{}

	if o.StoreDist {
		args = append(args, StoreDistAPIKeyword)
	}

	return args, nil
}
