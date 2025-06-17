// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_GeoAdd() {
	client := getExampleClient()

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

func ExampleClusterClient_GeoAdd() {
	client := getExampleClusterClient()

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

func ExampleClient_GeoHash() {
	client := getExampleClient()
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
	// [{sqc8b49rny0 false} {sqdtr74hyu0 false}]
}

func ExampleClusterClient_GeoHash() {
	client := getExampleClusterClient()
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
	// [{sqc8b49rny0 false} {sqdtr74hyu0 false}]
}

func ExampleClient_GeoPos() {
	client := getExampleClient()

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

func ExampleClusterClient_GeoPos() {
	client := getExampleClusterClient()

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

func ExampleClient_GeoDist() {
	client := getExampleClient()
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

func ExampleClusterClient_GeoDist() {
	client := getExampleClusterClient()
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

func ExampleClient_GeoSearch() {
	client := getExampleClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearch(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [Palermo]
}

func ExampleClusterClient_GeoSearch() {
	client := getExampleClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearch(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [Palermo]
}

func ExampleClient_GeoSearchWithResultOptions() {
	client := getExampleClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithResultOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [Palermo]
}

func ExampleClusterClient_GeoSearchWithResultOptions() {
	client := getExampleClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithResultOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [Palermo]
}

func ExampleClient_GeoSearchWithFullOptions() {
	client := getExampleClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithFullOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
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

func ExampleClusterClient_GeoSearchWithFullOptions() {
	client := getExampleClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithFullOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
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

func ExampleClient_GeoSearchWithInfoOptions() {
	client := getExampleClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithInfoOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
		*options.NewGeoSearchInfoOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [{Palermo {38.1155563954963 13.361389338970184} 0 3479099956230698}]
}

func ExampleClusterClient_GeoSearchWithInfoOptions() {
	client := getExampleClusterClient()

	key := uuid.New().String()

	AddInitialGeoData(client, key)

	result, err := client.GeoSearchWithInfoOptions(context.Background(),
		key,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
		*options.NewGeoSearchInfoOptions().SetWithDist(true).SetWithCoord(true).SetWithHash(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// [{Palermo {38.1155563954963 13.361389338970184} 0 3479099956230698}]
}

func ExampleClient_GeoSearchStore() {
	client := getExampleClient()

	source := uuid.New().String()
	destination := uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStore(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClusterClient_GeoSearchStore() {
	client := getExampleClusterClient()

	source := "{key-}" + uuid.New().String()
	destination := "{key-}" + uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStore(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClient_GeoSearchStoreWithInfoOptions() {
	client := getExampleClient()

	source := uuid.New().String()
	destination := uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithInfoOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
		*options.NewGeoSearchStoreInfoOptions().SetStoreDist(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClusterClient_GeoSearchStoreWithInfoOptions() {
	client := getExampleClusterClient()

	source := "{key-}" + uuid.New().String()
	destination := "{key-}" + uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithInfoOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
		*options.NewGeoSearchStoreInfoOptions().SetStoreDist(true),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClient_GeoSearchStoreWithResultOptions() {
	client := getExampleClient()

	source := uuid.New().String()
	destination := uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithResultOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClusterClient_GeoSearchStoreWithResultOptions() {
	client := getExampleClusterClient()

	source := "{key-}" + uuid.New().String()
	destination := "{key-}" + uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithResultOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
		*options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.DESC),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClient_GeoSearchStoreWithFullOptions() {
	client := getExampleClient()

	source := uuid.New().String()
	destination := uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithFullOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
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

func ExampleClusterClient_GeoSearchStoreWithFullOptions() {
	client := getExampleClusterClient()

	source := "{key-}" + uuid.New().String()
	destination := "{key-}" + uuid.New().String()

	AddInitialGeoData(client, source)

	result, err := client.GeoSearchStoreWithFullOptions(context.Background(),
		destination,
		source,
		&options.GeoMemberOrigin{Member: "Palermo"},
		*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
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

func AddInitialGeoData(client interfaces.BaseClientCommands, key string) {
	membersToCoordinates := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
	}

	_, err := client.GeoAdd(context.Background(), key, membersToCoordinates)
	if err != nil {
		fmt.Println("GeoSearch glide example failed with an error: ", err)
	}
}
