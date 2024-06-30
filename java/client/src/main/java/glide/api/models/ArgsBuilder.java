/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import com.google.protobuf.ByteString;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;

// TODO docs for the god of docs
public class ArgsBuilder {
    ArgsArray.Builder commandArgs = null;

    public ArgsBuilder() {
        commandArgs = ArgsArray.newBuilder();
    }

    public <ArgType> ArgsBuilder add(ArgType[] args) {
        for (ArgType arg : args) {
            commandArgs.addArgs(ByteString.copyFrom(GlideString.of(arg).getBytes()));
        }
        return this;
    }

    public <ArgType> ArgsBuilder add(ArgType arg) {
        commandArgs.addArgs(ByteString.copyFrom(GlideString.of(arg).getBytes()));
        return this;
    }

    public <ArgType> ArgsBuilder add(String[] args) {
        for (String arg : args) {
            commandArgs.addArgs(ByteString.copyFromUtf8(arg));
        }
        return this;
    }

    public ArgsArray build() {
        return commandArgs.build();
    }
}
