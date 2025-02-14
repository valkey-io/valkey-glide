// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"flag"
	"fmt"
)

var clusterNodes = flag.String("clusternodes", "", "AddressNodes for running Valkey/Redis cluster nodes")
var standaloneNode = flag.String("standalonenode", "", "Address for running Valkey/Redis standalone node")

// getExampleGlideClient returns a GlideClient instance for testing purposes.
// This function is used in the examples of the GlideClient methods.
func getExampleGlideClient() *GlideClient {
	// Read the GLIDE_CLUSTER_NODES environment variable
	if standaloneNode == nil {
		fmt.Println("Standalone Node not set")
	} else {
		fmt.Println("Standalone Node set to: ", *clusterNodes)
	}

	config := NewGlideClientConfiguration().
		WithAddress(new(NodeAddress)) // use default address

	client, err := NewGlideClient(config)
	if err != nil {
		fmt.Println("error connecting to database: ", err)
	}

	_, err = client.CustomCommand([]string{"FLUSHALL"}) // todo: replace with client.FlushAll() when implemented
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return client.(*GlideClient)
}

func getExampleGlideClusterClient() *GlideClusterClient {
	if clusterNodes == nil {
		fmt.Println("Cluster Nodes not set")
	} else {
		fmt.Println("Cluster Nodes set to: ", clusterNodes)
	}

	config := NewGlideClusterClientConfiguration().
		WithAddress(&NodeAddress{Host: "localhost", Port: 7001}).
		WithRequestTimeout(5000)

	client, err := NewGlideClusterClient(config)
	if err != nil {
		fmt.Println("error connecting to database: ", err)
	}

	_, err = client.CustomCommand([]string{"FLUSHALL"}) // todo: replace with client.FlushAll() when implemented
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return client.(*GlideClusterClient)
}

// CompareUnorderedSlices compares two unordered slices of structs and returns if both are equal.
// func CompareUnorderedSlices[T any](slice1, slice2 []T) bool {
// 	return cmp.Equal(slice1, slice2, cmpopts.SortSlices(func(a, b T) bool {
// 		return fmt.Sprintf("%v", a) < fmt.Sprintf("%v", b)
// 	}))
// }
