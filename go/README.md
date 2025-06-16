# Welcome to Valkey GLIDE!

Valkey General Language Independent Driver for the Enterprise (GLIDE) is the official open-source Valkey client library, proudly part of the Valkey organization. Our mission is to make your experience with Valkey and Redis OSS seamless and enjoyable. Whether you're a seasoned developer or just starting out, Valkey GLIDE is here to support you every step of the way.

# Why Choose Valkey GLIDE?

- **Community and Open Source**: Join our vibrant community and contribute to the project. We are always here to respond, and the client is for the community.
- **Reliability**: Built with best practices learned from over a decade of operating Redis OSS-compatible services.
- **Performance**: Optimized for high performance and low latency.
- **High Availability**: Designed to ensure your applications are always up and running.
- **Cross-Language Support**: Implemented using a core driver framework written in Rust, with language-specific extensions to ensure consistency and reduce complexity.
- **Stability and Fault Tolerance**: We brought our years of experience to create a bulletproof client.
- **Backed and Supported by AWS and GCP**: Ensuring robust support and continuous improvement of the project.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - GO Wrapper

## System Requirements

The release of Valkey GLIDE was tested on the following platforms:

Linux:

-   Ubuntu 20 (x86_64/amd64 and arm64/aarch64)
-   Amazon Linux 2 (AL2) and 2023 (AL2023) (x86_64)

**Note: Currently Alpine Linux / MUSL is NOT supported.**

macOS:

- macOS 14.7 (Apple silicon/aarch_64)
- macOS 13.7 (x86_64/amd64)

## GO supported versions

Valkey GLIDE Go supports Go version 1.22 and above.

## Installation and Setup

To install Valkey GLIDE in your Go project, follow these steps:

1. Open your terminal in your project directory.
2. Execute the commands below:
    ```bash
    $ go get github.com/valkey-io/valkey-glide/go/v2
    $ go mod tidy
    ```
3. After installation, you can start up a Valkey server and run one of the examples in [Basic Examples](#basic-examples).


## Basic Examples


### Standalone Example:

```go
package main

import (
	"context"
	"fmt"

	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func main() {
	host := "localhost"
	port := 6379

	config := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{Host: host, Port: port})

	client, err := glide.NewClient(config)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}

	res, err := client.Ping(context.Background())
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}
	fmt.Println(res) // PONG

	client.Close()
}
```

### Cluster Example:

```go
package main

import (
	"context"
	"fmt"

	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func main() {
	host := "localhost"
	port := 7001

	config := config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{Host: host, Port: port})

	client, err := glide.NewClusterClient(config)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}

	res, err := client.Ping(context.Background())
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}
	fmt.Println(res) // PONG

	client.Close()
}
```

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](DEVELOPER.md) file.
