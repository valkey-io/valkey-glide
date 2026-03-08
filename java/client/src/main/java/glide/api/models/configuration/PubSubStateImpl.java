/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.models.configuration.BaseSubscriptionConfiguration.ChannelMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/** Package-private implementation of PubSubState. */
@Getter
public final class PubSubStateImpl<T extends ChannelMode> implements PubSubState<T> {
    private final Map<T, Set<String>> desiredSubscriptions;
    private final Map<T, Set<String>> actualSubscriptions;

    public PubSubStateImpl(
            Map<T, Set<String>> desiredSubscriptions, Map<T, Set<String>> actualSubscriptions) {
        this.desiredSubscriptions = deepCopy(desiredSubscriptions);
        this.actualSubscriptions = deepCopy(actualSubscriptions);
    }

    private static <T extends ChannelMode> Map<T, Set<String>> deepCopy(Map<T, Set<String>> map) {
        Map<T, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<T, Set<String>> entry : map.entrySet()) {
            Set<String> copiedSet = new LinkedHashSet<>(entry.getValue());
            result.put(entry.getKey(), Collections.unmodifiableSet(copiedSet));
        }
        return Collections.unmodifiableMap(result);
    }
}
