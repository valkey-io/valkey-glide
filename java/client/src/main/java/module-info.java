module glide.api {
    exports glide.api;
    exports glide.api.commands;
    exports glide.api.logging;
    exports glide.api.models;
    exports glide.api.models.commands;
    exports glide.api.models.commands.batch;
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
    // Export Jedis compatibility layer for drop-in replacement
    exports redis.clients.jedis;
    exports redis.clients.jedis.params;
    exports redis.clients.jedis.args;
    exports redis.clients.jedis.resps;

    requires java.logging; // required by shadowed protobuf
    requires static lombok;
    requires org.apache.commons.lang3;
    requires org.apache.commons.pool2; // Required for Jedis compatibility layer
}
