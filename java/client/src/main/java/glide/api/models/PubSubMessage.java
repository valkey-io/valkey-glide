/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** PubSub message received by the client. */
@Getter
@EqualsAndHashCode
public class PubSubMessage {
    /** An incoming message received. */
    private final GlideString message;

    /** A name of the originating channel. */
    private final GlideString channel;

    /** A pattern matched to the channel name. */
    private final Optional<GlideString> pattern;

    public PubSubMessage(GlideString message, GlideString channel, GlideString pattern) {
        this.message = message;
        this.channel = channel;
        this.pattern = Optional.ofNullable(pattern);
    }

    public PubSubMessage(GlideString message, GlideString channel) {
        this.message = message;
        this.channel = channel;
        this.pattern = Optional.empty();
    }

    @Override
    public String toString() {
        String res = String.format("(%s, channel = %s", message, channel);
        if (pattern.isPresent()) {
            res += ", pattern = " + pattern.get();
        }
        return res + ")";
    }
}
