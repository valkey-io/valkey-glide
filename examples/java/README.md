## Run

Ensure that you have an instance of Valkey running on "localhost" on "6379". Otherwise, update glide.examples.StandaloneExample or glide.examples.ClusterExample with a configuration that matches your server settings.

To run the Standalone example:
```
cd valkey-glide/examples/java
./gradlew :runStandalone
```
To run the Cluster example:
```
cd valkey-glide/examples/java
./gradlew :runCluster
```
