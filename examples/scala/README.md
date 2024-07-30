# Run
Ensure that you have an instance of Valkey running on "localhost" on "6379". Otherwise, update StandaloneExample or ClusterExample with a configuration that matches your server settings.

To run the Standalone example:
```shell
cd valkey-glide/examples/scala
sbt "runMain StandaloneExample"
```

To run the Cluster example:
```
cd valkey-glide/examples/scala
sbt "runMain ClusterExample"
```
