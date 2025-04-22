package main

import (
	"fmt"
	"log"

	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func main() {
	// host := "localhost"
	// port := 7005

	configg := api.NewGlideClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Host: "localhost", Port: 7006}).
		WithAddress(&api.NodeAddress{Host: "localhost", Port: 7007}).
		WithAddress(&api.NodeAddress{Host: "localhost", Port: 7008})

	// config := api.NewGlideClusterClientConfiguration().
	// 	WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.NewGlideClusterClient(configg)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}
	tx := api.NewClusterTransaction(client)
	cmd := tx.GlideClusterClient
	cmd.Info()
	var simpleRoute config.Route = config.RandomRoute
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Cluster}},
		RouteOption: &options.RouteOption{
			Route: simpleRoute,
		},
	}

	cmd.InfoWithOptions(opts)

	pingOpts := options.ClusterPingOptions{
		PingOptions: &options.PingOptions{
			Message: "Hello Valkey",
		},
		RouteOption: &options.RouteOption{
			Route: simpleRoute,
		},
	}

	// Add ping with options and routing to transaction
	cmd.PingWithOptions(pingOpts)

	routeOpt := options.RouteOption{
		Route: simpleRoute,
	}
	cmd.ClientIdWithOptions(routeOpt)
	cmd.ClientGetNameWithOptions(routeOpt)

	echoOpts := options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "Hello from Echo command",
		},
		RouteOption: &options.RouteOption{
			Route: simpleRoute,
		},
	}

	cmd.EchoWithOptions(echoOpts)
	setparams := map[string]string{"timeout": "1000", "maxmemory": "1GB"}
	cmd.ConfigSetWithOptions(setparams, routeOpt)
	configParams := []string{"timeout", "maxmemory"}
	cmd.ConfigGetWithOptions(configParams, routeOpt)
	cmd.TimeWithOptions(routeOpt)
	cmd.ConfigRewriteWithOptions(routeOpt)
	cmd.ConfigResetStatWithOptions(routeOpt)
	cmd.LastSaveWithOptions(routeOpt)

	syncMode := options.SYNC

	flushOptions := options.FlushClusterOptions{
		FlushMode:   &syncMode,
		RouteOption: &routeOpt,
	}
	cmd.FlushAllWithOptions(flushOptions)
	cmd.FlushDBWithOptions(flushOptions)

	randomRouteOptions := options.ClusterLolwutOptions{
		LolwutOptions: &options.LolwutOptions{
			Version: 6,
			Args:    &[]int{10, 20},
		},
		RouteOption: &options.RouteOption{
			Route: config.RandomRoute,
		},
	}
	cmd.LolwutWithOptions(randomRouteOptions)
	cmd.DBSizeWithOptions(routeOpt)
	cmd.CustomCommandWithRoute([]string{"ping"}, config.RandomRoute)
	cmd.RandomKey()
	cmd.RandomKeyWithRoute(routeOpt)
	// // cmd.Scan(1)

	cmd.Ping()
	cmd.ClientId()

	cmd.ClientGetName()
	// cmd.Echo("hi")
	cmd.ClientSetName("hi")

	cmd.ConfigSet(map[string]string{"timeout": "1000", "maxmemory": "1GB"})
	cmd.ConfigGet([]string{"timeout", "maxmemory"})

	cmd.ConfigResetStat()
	cmd.LastSave()
	cmd.FlushAll()

	cmd.FlushDB()
	cmd.Lolwut()

	cmd.CustomCommand([]string{"ping"})
	cmd.RandomKey()

	cmd.Ping()
	result, err := tx.Exec()
	if err != nil {
		log.Fatalf("Transaction failed: %v", err)
	}
	fmt.Println(result)

	client.Close()
}
