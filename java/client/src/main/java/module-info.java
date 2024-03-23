module glide.api {
    exports glide.api;
    exports glide.api.commands;
    exports glide.api.models;
    exports glide.api.models.commands;
    exports glide.api.models.configuration;
    exports glide.api.models.exceptions;

    requires com.google.protobuf;
    requires io.netty.transport;
    requires lombok;
}
