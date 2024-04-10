module glide.api {
    exports glide.api;
    exports glide.api.commands;
    exports glide.api.models;
    exports glide.api.models.commands;
    exports glide.api.models.configuration;
    exports glide.api.models.exceptions;

    requires org.apache.commons.lang3;
    requires com.google.protobuf;
    requires io.netty.common;
    requires io.netty.transport;
    requires io.netty.transport.classes.kqueue;
    requires lombok;
}
