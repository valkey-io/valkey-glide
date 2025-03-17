// main.go - cluster version
package main

import (
 "fmt"
 "log"

 "github.com/valkey-io/valkey-glide/go/api"
 configg "github.com/valkey-io/valkey-glide/go/api/config"
 "github.com/valkey-io/valkey-glide/go/api/options"
)

func main() {
 // Configure Valkey Glide Cluster Client
 config := api.NewGlideClusterClientConfiguration().
  WithAddress(&api.NodeAddress{Host: "localhost", Port: 6380}).
  WithAddress(&api.NodeAddress{Host: "localhost", Port: 6381}).
  WithAddress(&api.NodeAddress{Host: "localhost", Port: 6382})


 client, err := api.NewGlideClusterClient(config)
 if err != nil {
  log.Fatal("Error connecting to database: ", err)
 }

 // Lolwut without route
 fmt.Println("Testing Lolwut without route:")
 noRouteOptions := options.ClusterLolwutOptions{
  LolwutOptions: &options.LolwutOptions{
   Version: 6,
   Args:    &[]int{10, 20},
  },
  RouteOption: nil,
 }
 noRouteResult, err := client.LolwutWithOptions(noRouteOptions)
 if err != nil {
    // This provides the same error message but within the program's standard logging format
    log.Fatal("Glide example has failed with an error: ", err)
 }
 fmt.Println("Lolwut result (no route):", noRouteResult.SingleValue())

 // Lolwut with AllNodes route
 fmt.Println("\nTesting Lolwut with AllNodes route:")
 allNodesOptions := options.ClusterLolwutOptions{
  LolwutOptions: &options.LolwutOptions{
   Version: 6,
   Args:    &[]int{10, 20},
  },
  RouteOption: &options.RouteOption{
   Route: configg.AllNodes,
  },
 }
 allNodesResult, err := client.LolwutWithOptions(allNodesOptions)
 if err != nil {
  log.Fatal("Lolwut with AllNodes route failed: ", err)
 }
 if allNodesResult.IsMultiValue() {
  for node, res := range allNodesResult.MultiValue() {
   fmt.Printf("Node: %s, Result: %s\n", node, res)
  }
 }

 // Lolwut with AllPrimaries route
 fmt.Println("\nTesting Lolwut with AllPrimaries route:")
 allPrimariesOptions := options.ClusterLolwutOptions{
  LolwutOptions: &options.LolwutOptions{
   Version: 6,
   Args:    &[]int{10, 20},
  },
  RouteOption: &options.RouteOption{
   Route: configg.AllPrimaries,
  },
 }
 allPrimariesResult, err := client.LolwutWithOptions(allPrimariesOptions)
 if err != nil {
  log.Fatal("Lolwut with AllPrimaries route failed: ", err)
 }
 if allPrimariesResult.IsMultiValue() {
  for node, res := range allPrimariesResult.MultiValue() {
   fmt.Printf("Node: %s, Result: %s\n", node, res)
  }
 }

 //Lolwut with RandomRoute
 fmt.Println("\nTesting Lolwut with RandomRoute:")
 randomRouteOptions := options.ClusterLolwutOptions{
  LolwutOptions: &options.LolwutOptions{
   Version: 6,
   Args:    &[]int{10, 20},
  },
  RouteOption: &options.RouteOption{
   Route: configg.RandomRoute,
  },
 }
 randomRouteResult, err := client.LolwutWithOptions(randomRouteOptions)
 if err != nil {
  log.Fatal("Lolwut with RandomRoute failed: ", err)
 }
 fmt.Println("Lolwut result (RandomRoute):", randomRouteResult.SingleValue())

 client.Close()
}
