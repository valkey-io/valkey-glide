## Run

Ensure that you have an instance of Valkey running on "localhost" on "6379". Otherwise, update glide.examples.ExamplesApp with a configuration that matches your server settings.

To run the example:
```
cd valkey-glide/examples/java
./gradlew :run
```

You should expect to see the output:
```
> Task :run
INFO logger_core: app - Set response is OK
INFO logger_core: app - Get response is bar
INFO logger_core: app - Ping response is PONG
```
