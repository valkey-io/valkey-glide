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
