// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"

	"github.com/valkey-io/valkey-glide/go/utils"
)

// The interface representing origin of the search for the `GeoSearch` command
type GeoSearchOrigin interface {
	ToArgs() ([]string, error)
}

const (
	GeoCoordOriginAPIKeyword  = "FROMLONLAT"
	GeoMemberOriginAPIKeyword = "FROMMEMBER"
)

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

// The shape of the search area for the `GeoSearch` command
type SearchShape string

const (
	BYRADIUS SearchShape = "BYRADIUS"
	BYBOX    SearchShape = "BYBOX"
)

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
// @see [valkey.io](https://valkey.io/commands/geosearch/)
type GeoSearchOptions struct {
	WithDist  bool
	WithCoord bool
	WithHash  bool
}

// Creates a new [GeoSearchOptions] with the default options
func NewGeoSearchOptions() *GeoSearchOptions {
	return &GeoSearchOptions{
		WithDist:  false,
		WithCoord: false,
		WithHash:  false,
	}
}

// WITHDIST: GeoSearch also return the distance of the returned items from the specified center point.
//
//	The distance is returned in the same unit as specified for the `searchBy` argument.
func (o *GeoSearchOptions) SetWithDist(withDist bool) *GeoSearchOptions {
	o.WithDist = withDist
	return o
}

// WITHCOORD: GeoSearch also return the coordinate of the returned items.
func (o *GeoSearchOptions) SetWithCoord(withCoord bool) *GeoSearchOptions {
	o.WithCoord = withCoord
	return o
}

// WITHHASH: GeoSearch also return the geohash of the returned items.
func (o *GeoSearchOptions) SetWithHash(withHash bool) *GeoSearchOptions {
	o.WithHash = withHash
	return o
}

// Returns the arguments for the `GeoSearch` command
func (o *GeoSearchOptions) ToArgs() ([]string, error) {
	args := []string{}

	if o.WithDist {
		args = append(args, WithdistValkeyApi)
	}
	if o.WithCoord {
		args = append(args, WithcoordValkeyApi)
	}
	if o.WithHash {
		args = append(args, WithhashValkeyApi)
	}
	return args, nil
}

type SortOrder string

// Const value representing the sort order of nested results
const (
	SortOrderAsc  SortOrder = "ASC"
	SortOrderDesc SortOrder = "DESC"
)

// Optional arguments for `GeoSearch` that contains up to 2 optional inputs
type GeoSearchResultOptions struct {
	sortOrder  SortOrder
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
func (o *GeoSearchResultOptions) SetSortOrder(sortOrder SortOrder) *GeoSearchResultOptions {
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
