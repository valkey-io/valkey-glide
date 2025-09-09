/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import java.util.Optional;
import lombok.Getter;

/** PubSub message received by the client. */
@Getter
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PubSubMessage)) return false;
        PubSubMessage that = (PubSubMessage) o;
        if (!this.message.equals(that.message)) return false;
        if (!this.channel.equals(that.channel)) return false;
        if (this.pattern.isEmpty() && that.pattern.isEmpty()) return true;
        if (this.pattern.isPresent() != that.pattern.isPresent()) return false;
        return this.pattern.get().equals(that.pattern.get());
    }

    @Override
    public int hashCode() {
        int result = this.message.hashCode();
        result = 31 * result + this.channel.hashCode();
        result = 31 * result + (this.pattern.isPresent() ? this.pattern.get().hashCode() : 0);
        return result;
    }
}
