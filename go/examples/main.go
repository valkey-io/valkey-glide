package main

import (
	"fmt"
	"strings"

	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func main() {
	host := "localhost"
	port := 6379

	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.NewGlideClient(config)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}

	opts := options.InfoOptions{Sections: []options.Section{options.Server}}
	response, err := client.InfoWithOptions(opts)
	//assert.NotNil(t, err)
	lines := strings.Split(response, "\n")
	var configFile string
	for _, line := range lines {
		if strings.HasPrefix(line, "config_file:") {
			configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
			break
		}
	}
	fmt.Println("configFile len: ", len(configFile))
	if len(configFile) > 0 {
		fmt.Println("configFile: ", configFile)
		//suite.verifyOK(client.ConfigRewrite())
	}

	client.Close()
}
