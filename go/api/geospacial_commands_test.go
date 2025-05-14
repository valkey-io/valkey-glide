// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"
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

	result, err := client.GeoAdd(context.Background(), uuid.New().String(), membersToCoordinates)
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

	result, err := client.GeoAdd(context.Background(), uuid.New().String(), membersToCoordinates)
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
	_, err := client.GeoAdd(context.Background(), key, membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Test getting geohash for multiple members
	geoHashResults, err := client.GeoHash(context.Background(), key, []string{"Palermo", "Catania"})
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
	_, err := client.GeoAdd(context.Background(), key, membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Test getting geohash for multiple members
	geoHashResults, err := client.GeoHash(context.Background(), key, []string{"Palermo", "Catania"})
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
	_, err := client.GeoAdd(context.Background(), key, map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	positions, err := client.GeoPos(context.Background(), key, []string{"Palermo", "Catania"})
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
	_, err := client.GeoAdd(context.Background(), key, map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	positions, err := client.GeoPos(context.Background(), key, []string{"Palermo", "Catania"})
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
	_, err := client.GeoAdd(context.Background(), key, membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Test getting geodist for multiple members
	result, err := client.GeoDist(context.Background(), key, member1, member2)
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
	_, err := client.GeoAdd(context.Background(), key, membersToCoordinates)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	// Test getting geodist for multiple members
	result, err := client.GeoDist(context.Background(), key, member1, member2)
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

	result, err := client.GeoSearch(context.Background(),
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

	result, err := client.GeoSearch(context.Background(),
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

	result, err := client.GeoSearchWithResultOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
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

	result, err := client.GeoSearchWithResultOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
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

	result, err := client.GeoSearchWithFullOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(2).SetSortOrder(options.DESC),
		*options.NewGeoSearchInfoOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [{Palermo {38.1155563954963 13.361389338970184} 0 3479099956230698}]
}

func ExampleGlideClusterClient_GeoSearchWithFullOptions() {
	client := getExampleGlideClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithFullOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(2).SetSortOrder(options.DESC),
		*options.NewGeoSearchInfoOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [{Palermo {38.1155563954963 13.361389338970184} 0 3479099956230698}]
}

func ExampleGlideClient_GeoSearchWithInfoOptions() {
	client := getExampleGlideClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithInfoOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchInfoOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [{Palermo {38.1155563954963 13.361389338970184} 0 3479099956230698}]
}

func ExampleGlideClusterClient_GeoSearchWithInfoOptions() {
	client := getExampleGlideClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithInfoOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchInfoOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [{Palermo {38.1155563954963 13.361389338970184} 0 3479099956230698}]
}

func ExampleGlideClient_GeoSearchStore() {
	client := getExampleGlideClient()

	source := uuid.New().String()
	destination := uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStore(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleGlideClusterClient_GeoSearchStore() {
	client := getExampleGlideClusterClient()

	source := "{key-}" + uuid.New().String()
	destination := "{key-}" + uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStore(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleGlideClient_GeoSearchStoreWithInfoOptions() {
	client := getExampleGlideClient()

	source := uuid.New().String()
	destination := uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithInfoOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchStoreInfoOptions().SetStoreDist(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleGlideClusterClient_GeoSearchStoreWithInfoOptions() {
	client := getExampleGlideClusterClient()

	source := "{key-}" + uuid.New().String()
	destination := "{key-}" + uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithInfoOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchStoreInfoOptions().SetStoreDist(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleGlideClient_GeoSearchStoreWithResultOptions() {
	client := getExampleGlideClient()

	source := uuid.New().String()
	destination := uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithResultOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleGlideClusterClient_GeoSearchStoreWithResultOptions() {
	client := getExampleGlideClusterClient()

	source := "{key-}" + uuid.New().String()
	destination := "{key-}" + uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithResultOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleGlideClient_GeoSearchStoreWithFullOptions() {
	client := getExampleGlideClient()

	source := uuid.New().String()
	destination := uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithFullOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
		*options.NewGeoSearchStoreInfoOptions().SetStoreDist(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleGlideClusterClient_GeoSearchStoreWithFullOptions() {
	client := getExampleGlideClusterClient()

	source := "{key-}" + uuid.New().String()
	destination := "{key-}" + uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithFullOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, options.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
		*options.NewGeoSearchStoreInfoOptions().SetStoreDist(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func AddInitialGeoData(client BaseClient, key string) {
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
	}

	_, err := client.GeoAdd(context.Background(), key, membersToCoordinates)
	if err != nil {
		fmt.Println("GeoSearch glide example failed with an error: ", err)
	}
}
