module glide.api {
    exports glide.api;
    exports glide.api.commands;
    exports glide.api.models;
    exports glide.api.models.commands;
    exports glide.api.models.commands.batch;
    exports glide.api.models.commands.bitmap;
    exports glide.api.models.commands.geospatial;
    exports glide.api.models.commands.scan;
    exports glide.api.models.commands.stream;
    exports glide.api.models.configuration;
    exports glide.api.models.exceptions;

    requires java.logging; // required by shadowed protobuf
    requires static lombok;
    requires org.apache.commons.lang3;
    requires java.base;
    requires io.valkey.glide.core;
}
