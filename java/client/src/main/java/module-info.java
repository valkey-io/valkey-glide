module glide.api {
    exports glide.api;
    exports glide.api.commands;
    exports glide.api.logging;
    exports glide.api.models;
    exports glide.api.models.commands;
    exports glide.api.models.commands.bitmap;
    exports glide.api.models.commands.geospatial;
    exports glide.api.models.commands.function;
    exports glide.api.models.commands.scan;
    exports glide.api.models.commands.stream;
    exports glide.api.models.commands.FT;
    exports glide.api.models.commands.json;
    exports glide.api.models.configuration;
    exports glide.api.models.exceptions;
    exports glide.api.commands.servermodules;

    // TODO get rid of `java.logging` dependency if possible
    // See https://github.com/grpc/grpc-java/issues/2415 for reference
    // Probably we could create a custom `LogResolver` or `LogManager` to redirect
    // all logs into GLIDE's logging API
    requires java.logging; // required by shadowed protobuf
    requires static lombok;
    requires org.apache.commons.lang3;
}
