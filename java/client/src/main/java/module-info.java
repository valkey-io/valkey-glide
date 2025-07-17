module glide.client {
    // Core JNI classes
    exports io.valkey.glide.core.client;
    exports io.valkey.glide.core.commands;
    exports io.valkey.glide.managers;
    
    // Client API classes
    exports glide.api;
    exports glide.api.commands;
    exports glide.api.commands.servermodules; // Re-enabled for compilation compatibility
    exports glide.api.models;
    exports glide.api.models.commands;
    exports glide.api.models.commands.batch;
    exports glide.api.models.commands.bitmap;
    exports glide.api.models.commands.function;
    exports glide.api.models.commands.FT; // Re-enabled for compilation compatibility
    exports glide.api.models.commands.geospatial;
    exports glide.api.models.commands.json; // Re-enabled for JSON support
    exports glide.api.models.commands.scan;
    exports glide.api.models.commands.stream;
    exports glide.api.models.configuration;
    exports glide.api.models.exceptions;
    exports glide.api.logging;
    exports glide.utils;

    requires java.base;
    requires java.logging;
    requires static lombok;
    requires org.apache.commons.lang3;
}
