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
