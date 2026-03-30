# Java 8 Example

This example demonstrates using Valkey GLIDE with Java 8.

## Requirements

- **Java 8** (JDK 1.8)
- A running Valkey server on `localhost:6379`

**Note:** While Java 8 is supported, **JDK 11 or later is strongly recommended** as Java 8 no longer receives free security updates from Oracle.

## Run

Ensure that you have a server running on "localhost" on port "6379".

To run the example:
```bash
cd valkey-glide/examples/java8
./gradlew run
```

## Version

This example uses `valkey-glide` version `1.+`. To change the version, update the following section in the `build.gradle` file:
```groovy
dependencies {
    implementation "io.valkey:valkey-glide:1.+:${osdetector.classifier}"
}
```

## Java 8 Compatibility

This project is configured to compile and run with Java 8:
- `sourceCompatibility = JavaVersion.VERSION_1_8`
- `targetCompatibility = JavaVersion.VERSION_1_8`

The Valkey GLIDE client library includes Java 8 compatibility utilities to replace Java 9+ APIs.
