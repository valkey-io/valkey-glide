# GO wrapper

The GO wrapper is currently a **public preview.**

# Valkey GLIDE

Valkey General Language Independent Driver for the Enterprise (GLIDE), is an open-source Valkey client library. Valkey GLIDE is one of the official client libraries for Valkey, and it supports all Valkey commands. Valkey GLIDE supports Valkey 7.2 and above, and Redis open-source 6.2, 7.0 and 7.2. Application programmers use Valkey GLIDE to safely and reliably connect their applications to Valkey- and Redis OSS- compatible services. Valkey GLIDE is designed for reliability, optimized performance, and high-availability, for Valkey and Redis OSS based applications. It is sponsored and supported by AWS, and is pre-configured with best practices learned from over a decade of operating Redis OSS-compatible services used by hundreds of thousands of customers. To help ensure consistency in application development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with language specific extensions. This design ensures consistency in features across languages and reduces overall complexity.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - GO Wrapper

## GO supported versions

| Go Version |
|----------------|
| 1.20           |
| 1.22           |

## Installation and Setup

To install Valkey GLIDE in `go`, follow these steps:

1. Open your terminal.
2. Execute the command below:
    ```bash
    $ go get github.com/valkey-io/valkey-glide/go
    ```
3. After installation, you can start up a Redis or Valkey server and run one of the examples in [Basic Examples](#basic-examples).


## Basic Examples


### Standalone Example:

```go   
package main

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api"
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

	res, err := client.Ping()
	if err != nil {
		log.Fatal("Glide example failed with an error: ", err)
	}
	fmt.Println(res) // PONG

	client.Close()
}
```

### Cluster Example:

```go   
package main

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api"
)

func main() {
	host := "localhost"
	port := 7001

	config := api.NewGlideClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Host: host, Port: port})

	client, err := api.NewGlideClusterClient(config)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}

	res, err := client.Ping()
	if err != nil {
		log.Fatal("Glide example failed with an error: ", err)
	}
	fmt.Println(res) // PONG

	client.Close()
}
```

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](DEVELOPER.md) file.
