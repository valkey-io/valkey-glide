// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_GeoAdd() {
	client := getExampleGlideClient()

	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}

	result, err := client.GeoAdd(uuid.New().String(), membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 2
}

func ExampleGlideClusterClient_GeoAdd() {
	client := getExampleGlideClusterClient()

	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}

	result, err := client.GeoAdd(uuid.New().String(), membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 2
}

func ExampleGlideClient_GeoHash() {
	client := getExampleGlideClient()
	key := uuid.New().String()
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}

	// Add the coordinates
	_, err := client.GeoAdd(key, membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Test getting geohash for multiple members
	geoHashResults, err := client.GeoHash(key, []string{"Palermo", "Catania"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(geoHashResults)

	// Output:
	// [sqc8b49rny0 sqdtr74hyu0]
}

func ExampleGlideClusterClient_GeoHash() {
	client := getExampleGlideClusterClient()
	key := uuid.New().String()
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}

	// Add the coordinates
	_, err := client.GeoAdd(key, membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Test getting geohash for multiple members
	geoHashResults, err := client.GeoHash(key, []string{"Palermo", "Catania"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(geoHashResults)

	// Output:
	// [sqc8b49rny0 sqdtr74hyu0]
}

func ExampleGlideClient_GeoPos() {
	client := getExampleGlideClient()

	key := uuid.New().String()
	_, err := client.GeoAdd(key, map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	positions, err := client.GeoPos(key, []string{"Palermo", "Catania"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(positions)

	// Output:
	// [[13.361389338970184 38.1155563954963] [15.087267458438873 37.50266842333162]]
}

func ExampleGlideClusterClient_GeoPos() {
	client := getExampleGlideClusterClient()

	key := uuid.New().String()
	_, err := client.GeoAdd(key, map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	positions, err := client.GeoPos(key, []string{"Palermo", "Catania"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(positions)

	// Output:
	// [[13.361389338970184 38.1155563954963] [15.087267458438873 37.50266842333162]]
}

func ExampleGlideClient_GeoDist() {
	client := getExampleGlideClient()
	key := uuid.New().String()
	member1 := "Palermo"
	member2 := "Catania"
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}

	// Add the coordinates
	_, err := client.GeoAdd(key, membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Test getting geodist for multiple members
	result, err := client.GeoDist(key, member1, member2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output:
	// 166274.1516
}

func ExampleGlideClusterClient_GeoDist() {
	client := getExampleGlideClusterClient()
	key := uuid.New().String()
	member1 := "Palermo"
	member2 := "Catania"
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}

	// Add the coordinates
	_, err := client.GeoAdd(key, membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	// Test getting geodist for multiple members
	result, err := client.GeoDist(key, member1, member2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output:
	// 166274.1516
}

func ExampleGlideClient_GeoSearch() {
	client := getExampleGlideClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearch(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [Palermo]
}

func ExampleGlideClusterClient_GeoSearch() {
	client := getExampleGlideClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearch(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [Palermo]
}

func ExampleGlideClient_GeoSearchWithResultOptions() {
	client := getExampleGlideClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithResultOptions(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.SortOrderDesc),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [Palermo]
}

func ExampleGlideClusterClient_GeoSearchWithResultOptions() {
	client := getExampleGlideClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithResultOptions(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.SortOrderDesc),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [Palermo]
}

func ExampleGlideClient_GeoSearchWithFullOptions() {
	client := getExampleGlideClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithFullOptions(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(2).SetSortOrder(options.SortOrderDesc),
		*options.NewGeoSearchOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [[Palermo [0 3479099956230698 [13.361389338970184 38.1155563954963]]]]
}

func ExampleGlideClusterClient_GeoSearchWithFullOptions() {
	client := getExampleGlideClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithFullOptions(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(2).SetSortOrder(options.SortOrderDesc),
		*options.NewGeoSearchOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [[Palermo [0 3479099956230698 [13.361389338970184 38.1155563954963]]]]
}

func ExampleGlideClient_GeoSearchWithOptions() {
	client := getExampleGlideClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithOptions(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [[Palermo [0 3479099956230698 [13.361389338970184 38.1155563954963]]]]
}

func ExampleGlideClusterClient_GeoSearchWithOptions() {
	client := getExampleGlideClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithOptions(
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [[Palermo [0 3479099956230698 [13.361389338970184 38.1155563954963]]]]
}

func AddInitialGeoData(client BaseClient, key string) {
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
	}

	_, err := client.GeoAdd(key, membersToCoordinates)
	if err != nil {
		fmt.Println("GeoSearch glide example failed with an error: ", err)
	}
}
