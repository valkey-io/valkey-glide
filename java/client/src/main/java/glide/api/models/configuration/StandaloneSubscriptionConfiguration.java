/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.GlideClient;
import glide.api.models.GlideString;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * Subscription configuration for {@link GlideClient}.
 *
 * @example
 *     <pre>{@code
 * // Configuration with 2 subscriptions, a callback, and a context:
 * StandaloneSubscriptionConfiguration subscriptionConfiguration =
 *     StandaloneSubscriptionConfiguration.builder()
 *         .subscription(EXACT, "notifications")
 *         .subscription(PATTERN, "news.*")
 *         .callback(callback, messageConsumer)
 *         .build();
 * // Now it could be supplied to `GlideClientConfiguration`:
 * GlideClientConfiguration clientConfiguration =
 *     GlideClientConfiguration.builder()
 *         .address(NodeAddress.builder().port(6379).build())
 *         .subscriptionConfiguration(subscriptionConfiguration)
 *         .build();
 * }</pre>
 */
@Getter
public final class StandaloneSubscriptionConfiguration extends BaseSubscriptionConfiguration {

    /**
     * Describes subscription modes for standalone client.
     *
     * @see <a href="https://valkey.io/docs/topics/pubsub/">valkey.io</a> for details.
     */
    public enum PubSubChannelMode implements ChannelMode {
        /** Use exact channel names. */
        EXACT,
        /** Use glob-style channel name patterns. */
        PATTERN,
    }

    /**
     * PubSub subscriptions to be used for the client.<br>
     * Will be applied via <code>SUBSCRIBE</code>/<code>PSUBSCRIBE</code> commands during connection
     * establishment.
     */
    @Getter(
            onMethod_ = {
                @SuppressFBWarnings(
                        value = "EI_EXPOSE_REP",
                        justification = "Subscriptions map is wrapped as unmodifiable")
            })
    private final Map<PubSubChannelMode, Set<GlideString>> subscriptions;

    // All code below is a custom implementation of `SuperBuilder`
    public StandaloneSubscriptionConfiguration(
            Optional<MessageCallback> callback,
            Optional<Object> context,
            Map<PubSubChannelMode, Set<GlideString>> subscriptions) {
        super(callback, context);
        this.subscriptions =
                subscriptions.entrySet().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
    }

    public static StandaloneSubscriptionConfigurationBuilder builder() {
        return new StandaloneSubscriptionConfigurationBuilder();
    }

    /** Builder for {@link StandaloneSubscriptionConfiguration}. */
    public static final class StandaloneSubscriptionConfigurationBuilder
            extends BaseSubscriptionConfigurationBuilder<
                    StandaloneSubscriptionConfigurationBuilder, StandaloneSubscriptionConfiguration> {

        private StandaloneSubscriptionConfigurationBuilder() {}

        // Note: Use a LinkedHashMap to preserve order for ease of debugging and unit testing.
        private Map<PubSubChannelMode, Set<GlideString>> subscriptions = new LinkedHashMap<>(2);

        /**
         * Add a subscription to a channel or to multiple channels if {@link PubSubChannelMode#PATTERN}
         * is used.<br>
         * See {@link StandaloneSubscriptionConfiguration#subscriptions}.
         */
        public StandaloneSubscriptionConfigurationBuilder subscription(
                PubSubChannelMode mode, GlideString channelOrPattern) {
            addSubscription(subscriptions, mode, channelOrPattern);
            return self();
        }

        /**
         * Set all subscriptions in a bulk. Rewrites previously stored configurations.<br>
         * See {@link StandaloneSubscriptionConfiguration#subscriptions}.
         */
        public StandaloneSubscriptionConfigurationBuilder subscriptions(
                Map<PubSubChannelMode, Set<GlideString>> subscriptions) {
            this.subscriptions = new LinkedHashMap<>(subscriptions);
            return this;
        }

        /**
         * Set subscriptions in a bulk for the given mode. Rewrites previously stored configurations for
         * that mode.<br>
         * See {@link StandaloneSubscriptionConfiguration#subscriptions}.
         */
        public StandaloneSubscriptionConfigurationBuilder subscriptions(
                PubSubChannelMode mode, Set<GlideString> subscriptions) {
            this.subscriptions.put(mode, Set.copyOf(subscriptions));
            return this;
        }

        @Override
        protected StandaloneSubscriptionConfigurationBuilder self() {
            return this;
        }

        @Override
        public StandaloneSubscriptionConfiguration build() {
            return new StandaloneSubscriptionConfiguration(callback, context, subscriptions);
        }
    }
}
