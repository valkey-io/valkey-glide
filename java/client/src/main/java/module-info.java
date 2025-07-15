module glide.client {
    // Core JNI classes
    exports io.valkey.glide.core.client;
    exports io.valkey.glide.core.commands;
    exports io.valkey.glide.managers;
    
    // Client API classes
    exports glide.api;
    exports glide.api.models;
    exports glide.utils;

    requires java.base;
    requires java.logging;
}
