/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.models.configuration.BaseSubscriptionConfiguration.ChannelMode;
import java.util.Map;
import java.util.Set;

/**
 * Represents the subscription state for a pubsub client.
 *
 * @param <T> The channel mode type (e.g., PubSubChannelMode or PubSubClusterChannelMode)
 */
public interface PubSubState<T extends ChannelMode> {
    /**
     * Gets the desired subscription state.
     *
     * @return Map of channel modes to their subscribed channels
     */
    Map<T, Set<String>> getDesiredSubscriptions();

    /**
     * Gets the actual subscription state.
     *
     * @return Map of channel modes to their subscribed channels
     */
    Map<T, Set<String>> getActualSubscriptions();
}
