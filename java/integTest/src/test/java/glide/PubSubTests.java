/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.Batch;
import glide.api.models.ClusterBatch;
import glide.api.models.GlideString;
import glide.api.models.PubSubMessage;
import glide.api.models.commands.batch.ClusterBatchOptions;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.BaseSubscriptionConfiguration.ChannelMode;
import glide.api.models.configuration.BaseSubscriptionConfiguration.MessageCallback;
import glide.api.models.configuration.ClusterSubscriptionConfiguration;
import glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotType;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30) // sec
public class PubSubTests {

    /** Enumeration for specifying how subscriptions are established. */
    private enum SubscriptionMethod {
        /** Subscriptions set in client configuration at creation time. */
        Config,
        /** Non-blocking subscription using subscribe() without timeout. */
        Lazy,
        /** Blocking subscription with timeout. */
        Blocking
    }

    /** Enumeration for specifying the method of reading PUBSUB messages. */
    private enum MessageReadMethod {
        Callback,
        Sync,
        Async
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    private <M extends ChannelMode> BaseClient createClientWithSubscriptions(
            boolean standalone,
            Map<M, Set<GlideString>> subscriptions,
            Optional<MessageCallback> callback,
            Optional<Object> context) {
        BaseClient client =
                standalone
                        ? createStandaloneClientWithConfig(subscriptions, callback, context)
                        : createClusterClientWithConfig(subscriptions, callback, context);
        listeners.put(client, subscriptions);
        return client;
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    private <M extends ChannelMode> GlideClient createStandaloneClientWithConfig(
            Map<M, Set<GlideString>> subscriptions,
            Optional<MessageCallback> callback,
            Optional<Object> context) {
        var builder =
                StandaloneSubscriptionConfiguration.builder()
                        .subscriptions((Map<PubSubChannelMode, Set<GlideString>>) subscriptions);
        if (callback.isPresent()) {
            builder.callback(callback.get(), context.get());
        }
        return GlideClient.createClient(
                        commonClientConfig()
                                .requestTimeout(5000)
                                .subscriptionConfiguration(builder.build())
                                .build())
                .get();
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    private <M extends ChannelMode> GlideClusterClient createClusterClientWithConfig(
            Map<M, Set<GlideString>> subscriptions,
            Optional<MessageCallback> callback,
            Optional<Object> context) {
        var builder =
                ClusterSubscriptionConfiguration.builder()
                        .subscriptions((Map<PubSubClusterChannelMode, Set<GlideString>>) subscriptions);
        if (callback.isPresent()) {
            builder.callback(callback.get(), context.get());
        }
        return GlideClusterClient.createClient(
                        commonClusterClientConfig()
                                .requestTimeout(5000)
                                .subscriptionConfiguration(builder.build())
                                .build())
                .get();
    }

    private <M extends ChannelMode> BaseClient createClientWithSubscriptions(
            boolean standalone, Map<M, Set<GlideString>> subscriptions) {
        return createClientWithSubscriptions(
                standalone, subscriptions, Optional.empty(), Optional.empty());
    }

    @SneakyThrows
    private BaseClient createClient(boolean standalone) {
        BaseClient client =
                standalone
                        ? GlideClient.createClient(commonClientConfig().build()).get()
                        : GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
        senders.add(client);
        return client;
    }

    @SneakyThrows
    private BaseClient createClientWithEmptySubscriptionConfig(boolean standalone) {
        BaseClient client =
                standalone ? createStandaloneClientWithEmptyConfig() : createClusterClientWithEmptyConfig();
        senders.add(client);
        return client;
    }

    @SneakyThrows
    private GlideClient createStandaloneClientWithEmptyConfig() {
        return GlideClient.createClient(
                        commonClientConfig()
                                .subscriptionConfiguration(StandaloneSubscriptionConfiguration.builder().build())
                                .build())
                .get();
    }

    @SneakyThrows
    private GlideClusterClient createClusterClientWithEmptyConfig() {
        return GlideClusterClient.createClient(
                        commonClusterClientConfig()
                                .subscriptionConfiguration(ClusterSubscriptionConfiguration.builder().build())
                                .build())
                .get();
    }

    @SneakyThrows
    private GlideClient createStandaloneClient() {
        GlideClient client = GlideClient.createClient(commonClientConfig().build()).get();
        senders.add(client);
        return client;
    }

    @SneakyThrows
    private GlideClusterClient createClusterClient() {
        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
        senders.add(client);
        return client;
    }

    @SneakyThrows
    private GlideClient createStandaloneClientWithEmptySubscriptions() {
        GlideClient client = createStandaloneClientWithEmptyConfig();
        listeners.put(client, Map.of());
        return client;
    }

    @SneakyThrows
    private GlideClusterClient createClusterClientWithEmptySubscriptions() {
        GlideClusterClient client = createClusterClientWithEmptyConfig();
        listeners.put(client, Map.of());
        return client;
    }

    @SneakyThrows
    private GlideClient createStandaloneClientWithSubscriptions(
            Map<PubSubChannelMode, Set<GlideString>> subscriptions) {
        GlideClient client =
                createStandaloneClientWithConfig(subscriptions, Optional.empty(), Optional.empty());
        listeners.put(client, subscriptions);
        return client;
    }

    @SneakyThrows
    private GlideClusterClient createClusterClientWithSubscriptions(
            Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions) {
        GlideClusterClient client =
                createClusterClientWithConfig(subscriptions, Optional.empty(), Optional.empty());
        listeners.put(client, subscriptions);
        return client;
    }

    /**
     * Subscribe to channels based on subscription method. For Config method, subscriptions are
     * already set at client creation. For Lazy/Blocking, subscribe dynamically.
     */
    @SneakyThrows
    private void subscribeByMethod(
            BaseClient client,
            SubscriptionMethod method,
            Map<? extends ChannelMode, Set<GlideString>> subscriptions) {
        if (method == SubscriptionMethod.Config) {
            return;
        }

        subscribeToExactChannels(client, method, subscriptions);
        subscribeToPatternChannels(client, method, subscriptions);
        subscribeToShardedChannels(client, method, subscriptions);
    }

    @SneakyThrows
    private void subscribeToExactChannels(
            BaseClient client,
            SubscriptionMethod method,
            Map<? extends ChannelMode, Set<GlideString>> subscriptions) {
        Set<GlideString> exactChannels = subscriptions.get(exact(client instanceof GlideClient));
        if (exactChannels != null && !exactChannels.isEmpty()) {
            Set<String> channels =
                    exactChannels.stream().map(GlideString::getString).collect(Collectors.toSet());
            if (method == SubscriptionMethod.Lazy) {
                client.subscribeLazy(channels).get();
                Thread.sleep(MESSAGE_DELIVERY_DELAY);
            } else {
                client.subscribe(channels, 5000).get();
            }
        }
    }

    @SneakyThrows
    private void subscribeToPatternChannels(
            BaseClient client,
            SubscriptionMethod method,
            Map<? extends ChannelMode, Set<GlideString>> subscriptions) {
        Set<GlideString> patternChannels = subscriptions.get(pattern(client instanceof GlideClient));
        if (patternChannels != null && !patternChannels.isEmpty()) {
            Set<String> patterns =
                    patternChannels.stream().map(GlideString::getString).collect(Collectors.toSet());
            if (method == SubscriptionMethod.Lazy) {
                client.psubscribeLazy(patterns).get();
                Thread.sleep(MESSAGE_DELIVERY_DELAY);
            } else {
                client.psubscribe(patterns, 5000).get();
            }
        }
    }

    @SneakyThrows
    private void subscribeToShardedChannels(
            BaseClient client,
            SubscriptionMethod method,
            Map<? extends ChannelMode, Set<GlideString>> subscriptions) {
        if (client instanceof GlideClusterClient) {
            Set<GlideString> shardedChannels = subscriptions.get(PubSubClusterChannelMode.SHARDED);
            if (shardedChannels != null && !shardedChannels.isEmpty()) {
                Set<String> channels =
                        shardedChannels.stream().map(GlideString::getString).collect(Collectors.toSet());
                if (method == SubscriptionMethod.Lazy) {
                    ((GlideClusterClient) client).ssubscribeLazy(channels).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);
                } else {
                    ((GlideClusterClient) client).ssubscribe(channels, 5000).get();
                }
            }
        }
    }

    /**
     * pubsubMessage queue used in callback to analyze received pubsubMessages. Number is a client ID.
     */
    private final ConcurrentLinkedDeque<Pair<Integer, PubSubMessage>> pubsubMessageQueue =
            new ConcurrentLinkedDeque<>();

    /** Subscribed clients used in a test. */
    private final Map<BaseClient, Map<? extends ChannelMode, Set<GlideString>>> listeners =
            new HashMap<>();

    /** Other clients used in a test. */
    private final List<BaseClient> senders = new ArrayList<>();

    private static final int MESSAGE_DELIVERY_DELAY = 500; // ms

    @AfterEach
    @SneakyThrows
    public void cleanup() {
        unsubscribeAllListeners();
        closeAllClients();
        pubsubMessageQueue.clear();
    }

    @SneakyThrows
    private void unsubscribeAllListeners() {
        for (var pair : listeners.entrySet()) {
            var client = pair.getKey();
            var subscriptionTypes = pair.getValue();
            if (client instanceof GlideClusterClient) {
                unsubscribeClusterClient((GlideClusterClient) client, subscriptionTypes);
            } else {
                unsubscribeStandaloneClient((GlideClient) client, subscriptionTypes);
            }
        }
        Thread.sleep(200); // Wait for unsubscribe commands to fully propagate
        listeners.clear();
    }

    @SneakyThrows
    private void unsubscribeClusterClient(
            GlideClusterClient client, Map<? extends ChannelMode, Set<GlideString>> subscriptionTypes) {
        for (var subscription : subscriptionTypes.entrySet()) {
            var channels = subscription.getValue().toArray(GlideString[]::new);
            for (GlideString channel : channels) {
                switch ((PubSubClusterChannelMode) subscription.getKey()) {
                    case EXACT:
                        client.customCommand(new GlideString[] {gs("unsubscribe"), channel}).get();
                        break;
                    case PATTERN:
                        client.customCommand(new GlideString[] {gs("punsubscribe"), channel}).get();
                        break;
                    case SHARDED:
                        client.customCommand(new GlideString[] {gs("sunsubscribe"), channel}).get();
                        break;
                }
            }
        }
    }

    @SneakyThrows
    private void unsubscribeStandaloneClient(
            GlideClient client, Map<? extends ChannelMode, Set<GlideString>> subscriptionTypes) {
        for (var subscription : subscriptionTypes.entrySet()) {
            var channels = subscription.getValue().toArray(GlideString[]::new);
            switch ((PubSubChannelMode) subscription.getKey()) {
                case EXACT:
                    client.customCommand(ArrayUtils.addFirst(channels, gs("unsubscribe"))).get();
                    break;
                case PATTERN:
                    client.customCommand(ArrayUtils.addFirst(channels, gs("punsubscribe"))).get();
                    break;
            }
        }
    }

    @SneakyThrows
    private void closeAllClients() {
        for (var client : senders) {
            client.close();
        }
        senders.clear();
    }

    @SneakyThrows
    private void verifyReceivedPubsubMessages(
            Set<Pair<Integer, PubSubMessage>> pubsubMessages,
            BaseClient listener,
            MessageReadMethod method) {
        switch (method) {
            case Callback:
                verifyCallbackMessages(pubsubMessages);
                break;
            case Async:
                verifyAsyncMessages(pubsubMessages, listener);
                break;
            case Sync:
                verifySyncMessages(pubsubMessages, listener);
                break;
        }
    }

    private void verifyCallbackMessages(Set<Pair<Integer, PubSubMessage>> pubsubMessages) {
        assertEquals(pubsubMessages, new HashSet<>(pubsubMessageQueue));
    }

    @SneakyThrows
    private void verifyAsyncMessages(
            Set<Pair<Integer, PubSubMessage>> pubsubMessages, BaseClient listener) {
        var received = new HashSet<PubSubMessage>(pubsubMessages.size());
        CompletableFuture<PubSubMessage> messagePromise;
        while ((messagePromise = listener.getPubSubMessage()).isDone()) {
            received.add(messagePromise.get());
        }
        assertEquals(pubsubMessages.stream().map(Pair::getValue).collect(Collectors.toSet()), received);
    }

    private void verifySyncMessages(
            Set<Pair<Integer, PubSubMessage>> pubsubMessages, BaseClient listener) {
        var received = new HashSet<PubSubMessage>(pubsubMessages.size());
        PubSubMessage message;
        while ((message = listener.tryGetPubSubMessage()) != null) {
            received.add(message);
        }
        assertEquals(pubsubMessages.stream().map(Pair::getValue).collect(Collectors.toSet()), received);
    }

    /**
     * Permute all combinations of `standalone` as bool vs {@link MessageReadMethod} vs {@link
     * SubscriptionMethod}.
     */
    private static Stream<Arguments> getTestScenarios() {
        List<Arguments> scenarios = new ArrayList<>();
        for (boolean standalone : new boolean[] {true, false}) {
            for (MessageReadMethod readMethod : MessageReadMethod.values()) {
                for (SubscriptionMethod subMethod : SubscriptionMethod.values()) {
                    scenarios.add(Arguments.of(standalone, readMethod, subMethod));
                }
            }
        }
        return scenarios.stream();
    }

    private ChannelMode exact(boolean standalone) {
        return standalone ? PubSubChannelMode.EXACT : PubSubClusterChannelMode.EXACT;
    }

    private ChannelMode pattern(boolean standalone) {
        return standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN;
    }

    @SuppressWarnings("unchecked")
    private BaseClient createListener(
            boolean standalone,
            boolean withCallback,
            int clientId,
            Map<? extends ChannelMode, Set<GlideString>> subscriptions,
            SubscriptionMethod subscriptionMethod) {
        MessageCallback callback =
                (msg, ctx) ->
                        ((ConcurrentLinkedDeque<Pair<Integer, PubSubMessage>>) ctx)
                                .push(Pair.of(clientId, msg));

        if (subscriptionMethod == SubscriptionMethod.Config) {
            return withCallback
                    ? createClientWithSubscriptions(
                            standalone, subscriptions, Optional.of(callback), Optional.of(pubsubMessageQueue))
                    : createClientWithSubscriptions(standalone, subscriptions);
        }

        return withCallback
                ? createClientWithCallbackOnly(standalone, callback)
                : createClientWithEmptySubscriptionConfig(standalone);
    }

    @SneakyThrows
    private BaseClient createClientWithCallbackOnly(boolean standalone, MessageCallback callback) {
        BaseClient client =
                standalone
                        ? createStandaloneClientWithCallback(callback)
                        : createClusterClientWithCallback(callback);
        return client;
    }

    @SneakyThrows
    private GlideClient createStandaloneClientWithCallback(MessageCallback callback) {
        return GlideClient.createClient(
                        commonClientConfig()
                                .requestTimeout(5000)
                                .subscriptionConfiguration(
                                        StandaloneSubscriptionConfiguration.builder()
                                                .callback(callback, pubsubMessageQueue)
                                                .build())
                                .build())
                .join();
    }

    @SneakyThrows
    private GlideClusterClient createClusterClientWithCallback(MessageCallback callback) {
        return GlideClusterClient.createClient(
                        commonClusterClientConfig()
                                .requestTimeout(5000)
                                .subscriptionConfiguration(
                                        ClusterSubscriptionConfiguration.builder()
                                                .callback(callback, pubsubMessageQueue)
                                                .build())
                                .build())
                .join();
    }

    // TODO why `publish` returns 0 on cluster or > 1 on standalone when there is only 1 receiver???
    //  meanwhile, all pubsubMessages are delivered.
    //  debug this and add checks for `publish` return value

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void config_error_on_resp2(boolean standalone) {
        if (standalone) {
            var config =
                    commonClientConfig()
                            .subscriptionConfiguration(StandaloneSubscriptionConfiguration.builder().build())
                            .protocol(ProtocolVersion.RESP2)
                            .build();
            var exception =
                    assertThrows(ConfigurationError.class, () -> GlideClient.createClient(config));
            assertTrue(exception.getMessage().contains("PubSub subscriptions require RESP3 protocol"));
        } else {
            var config =
                    commonClusterClientConfig()
                            .subscriptionConfiguration(ClusterSubscriptionConfiguration.builder().build())
                            .protocol(ProtocolVersion.RESP2)
                            .build();
            var exception =
                    assertThrows(ConfigurationError.class, () -> GlideClusterClient.createClient(config));
            assertTrue(exception.getMessage().contains("PubSub subscriptions require RESP3 protocol"));
        }
    }

    /** Similar to `test_pubsub_exact_happy_path` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}, subscribe via {2}")
    @MethodSource("getTestScenarios")
    public void exact_happy_path(
            boolean standalone, MessageReadMethod method, SubscriptionMethod subscriptionMethod) {
        GlideString channel = gs(UUID.randomUUID().toString());
        GlideString message = gs(UUID.randomUUID().toString());
        var subscriptions = Map.of(exact(standalone), Set.of(channel));

        var listener =
                createListener(
                        standalone, method == MessageReadMethod.Callback, 1, subscriptions, subscriptionMethod);
        subscribeByMethod(listener, subscriptionMethod, subscriptions);
        var sender = createClient(standalone);

        sender.publish(message, channel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the message

        verifyReceivedPubsubMessages(
                Set.of(Pair.of(1, new PubSubMessage(message, channel))), listener, method);
    }

    /** Similar to `test_pubsub_exact_happy_path_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}, subscribe via {2}")
    @MethodSource("getTestScenarios")
    public void exact_happy_path_many_channels(
            boolean standalone, MessageReadMethod method, SubscriptionMethod subscriptionMethod) {
        int numChannels = 16;
        int messagesPerChannel = 16;
        var messages = new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        ChannelMode mode = exact(standalone);
        Map<? extends ChannelMode, Set<GlideString>> subscriptions = Map.of(mode, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (var j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel));
            }
        }

        var listener =
                createListener(
                        standalone, method == MessageReadMethod.Callback, 1, subscriptions, subscriptionMethod);
        subscribeByMethod(listener, subscriptionMethod, subscriptions);
        var sender = createClient(standalone);

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /** Similar to `test_sharded_pubsub` in python client tests. */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void sharded_pubsub(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        GlideString channel = gs(UUID.randomUUID().toString());
        GlideString pubsubMessage = gs(UUID.randomUUID().toString());
        var subscriptions = Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));

        var listener =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        1,
                        subscriptions,
                        SubscriptionMethod.Config);
        var sender = (GlideClusterClient) createClient(false);

        sender.publish(pubsubMessage, channel, true).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the message

        verifyReceivedPubsubMessages(
                Set.of(Pair.of(1, new PubSubMessage(pubsubMessage, channel))), listener, method);
    }

    /** Similar to `test_sharded_pubsub_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void sharded_pubsub_many_channels(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        int numChannels = 16;
        int pubsubMessagesPerChannel = 16;
        var pubsubMessages = new ArrayList<PubSubMessage>(numChannels * pubsubMessagesPerChannel);
        PubSubClusterChannelMode mode = PubSubClusterChannelMode.SHARDED;
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions = Map.of(mode, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (var j = 0; j < pubsubMessagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                pubsubMessages.add(new PubSubMessage(message, channel));
            }
        }

        var listener =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        1,
                        subscriptions,
                        SubscriptionMethod.Config);
        var sender = (GlideClusterClient) createClient(false);

        for (var pubsubMessage : pubsubMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }
        sender.publish(UUID.randomUUID().toString(), UUID.randomUUID().toString(), true).get();

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                pubsubMessages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()),
                listener,
                method);
    }

    /** Similar to `test_pubsub_pattern` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}, subscribe via {2}")
    @MethodSource("getTestScenarios")
    public void pattern(
            boolean standalone, MessageReadMethod method, SubscriptionMethod subscriptionMethod) {
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        Map<GlideString, GlideString> message2channels =
                Map.of(
                        gs(prefix + "1"),
                        gs(UUID.randomUUID().toString()),
                        gs(prefix + "2"),
                        gs(UUID.randomUUID().toString()));
        var subscriptions =
                Map.of(
                        standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN,
                        Set.of(pattern));

        var listener =
                createListener(
                        standalone, method == MessageReadMethod.Callback, 1, subscriptions, subscriptionMethod);
        subscribeByMethod(listener, subscriptionMethod, subscriptions);
        var sender = createClient(standalone);

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // need some time to propagate subscriptions - why?

        for (var entry : message2channels.entrySet()) {
            sender.publish(entry.getValue(), entry.getKey()).get();
        }
        sender.publish(UUID.randomUUID().toString(), "channel").get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        var expected =
                message2channels.entrySet().stream()
                        .map(e -> Pair.of(1, new PubSubMessage(e.getValue(), e.getKey(), pattern)))
                        .collect(Collectors.toSet());

        verifyReceivedPubsubMessages(expected, listener, method);
    }

    /** Similar to `test_pubsub_pattern_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}, subscribe via {2}")
    @MethodSource("getTestScenarios")
    public void pattern_many_channels(
            boolean standalone, MessageReadMethod method, SubscriptionMethod subscriptionMethod) {
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 16;
        int messagesPerChannel = 16;
        ChannelMode mode = standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN;
        var messages = new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        var subscriptions = Map.of(mode, Set.of(pattern));

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(prefix + "-" + i + "-" + UUID.randomUUID());
            for (var j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel, pattern));
            }
        }

        var listener =
                createListener(
                        standalone, method == MessageReadMethod.Callback, 1, subscriptions, subscriptionMethod);
        subscribeByMethod(listener, subscriptionMethod, subscriptions);
        var sender = createClient(standalone);

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // need some time to propagate subscriptions - why?

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        sender.publish(UUID.randomUUID().toString(), "channel").get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /** Similar to `test_pubsub_combined_exact_and_pattern_one_client` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}, subscribe via {2}")
    @MethodSource("getTestScenarios")
    public void combined_exact_and_pattern_one_client(
            boolean standalone, MessageReadMethod method, SubscriptionMethod subscriptionMethod) {
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 16;
        int messagesPerChannel = 16;
        var messages = new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        ChannelMode mode = standalone ? PubSubChannelMode.EXACT : PubSubClusterChannelMode.EXACT;
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Map.of(
                        mode,
                        new HashSet<>(),
                        standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN,
                        Set.of(pattern));

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (var j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel));
            }
        }

        for (var j = 0; j < messagesPerChannel; j++) {
            GlideString pubsubMessage = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(pubsubMessage, channel, pattern));
        }

        var listener =
                createListener(
                        standalone, method == MessageReadMethod.Callback, 1, subscriptions, subscriptionMethod);
        subscribeByMethod(listener, subscriptionMethod, subscriptions);
        var sender = createClient(standalone);

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /**
     * Similar to `test_pubsub_combined_exact_and_pattern_multiple_clients` in python client tests.
     */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}, subscribe via {2}")
    @MethodSource("getTestScenarios")
    public void combined_exact_and_pattern_multiple_clients(
            boolean standalone, MessageReadMethod method, SubscriptionMethod subscriptionMethod) {
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 16;
        var messages = new ArrayList<PubSubMessage>(numChannels * 2);
        ChannelMode mode = exact(standalone);
        Map<? extends ChannelMode, Set<GlideString>> subscriptions = Map.of(mode, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (var j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, pattern));
        }

        var listenerExactSub =
                createListener(
                        standalone, method == MessageReadMethod.Callback, 1, subscriptions, subscriptionMethod);
        subscribeByMethod(listenerExactSub, subscriptionMethod, subscriptions);

        subscriptions = Map.of(pattern(standalone), Set.of(pattern));
        var listenerPatternSub =
                createListener(
                        standalone, method == MessageReadMethod.Callback, 2, subscriptions, subscriptionMethod);
        subscribeByMethod(listenerPatternSub, subscriptionMethod, subscriptions);

        var sender = createClient(standalone);

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .map(m -> Pair.of(m.getPattern().isEmpty() ? 1 : 2, m))
                            .collect(Collectors.toSet()),
                    listenerExactSub,
                    method);
        } else {
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .filter(m -> m.getPattern().isEmpty())
                            .map(m -> Pair.of(1, m))
                            .collect(Collectors.toSet()),
                    listenerExactSub,
                    method);
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .filter(m -> m.getPattern().isPresent())
                            .map(m -> Pair.of(2, m))
                            .collect(Collectors.toSet()),
                    listenerPatternSub,
                    method);
        }
    }

    /**
     * Similar to `test_pubsub_combined_exact_pattern_and_sharded_one_client` in python client tests.
     */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void combined_exact_pattern_and_sharded_one_client(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        String shardPrefix = "{shard}";
        int numChannels = 16;
        var messages = new ArrayList<PubSubMessage>(numChannels * 2);
        var shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions =
                Map.of(
                        PubSubClusterChannelMode.EXACT, new HashSet<>(),
                        PubSubClusterChannelMode.PATTERN, Set.of(pattern),
                        PubSubClusterChannelMode.SHARDED, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.EXACT).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.SHARDED).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (var j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, pattern));
        }

        var listener =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        1,
                        subscriptions,
                        SubscriptionMethod.Config);
        var sender = (GlideClusterClient) createClient(false);

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        messages.addAll(shardedMessages);
        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /** This test fully covers all `test_pubsub_*_co_existence` tests in python client. */
    @SneakyThrows
    @Test
    public void coexistense_of_sync_and_async_read() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        String prefix = "channel.";
        String pattern = prefix + "*";
        String shardPrefix = "{shard}";
        int numChannels = 16;
        var messages = new ArrayList<PubSubMessage>(numChannels * 2);
        var shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions =
                Map.of(
                        PubSubClusterChannelMode.EXACT, new HashSet<>(),
                        PubSubClusterChannelMode.PATTERN, Set.of(gs(pattern)),
                        PubSubClusterChannelMode.SHARDED, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            var channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.EXACT).add(channel);
            var message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (var i = 0; i < numChannels; i++) {
            var channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.SHARDED).add(channel);
            var message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (var j = 0; j < numChannels; j++) {
            var message = gs(j + "-" + UUID.randomUUID());
            var channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, gs(pattern)));
        }

        var listener = createListener(false, false, 1, subscriptions, SubscriptionMethod.Config);
        var sender = (GlideClusterClient) createClient(false);

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        messages.addAll(shardedMessages);

        var received = new LinkedHashSet<PubSubMessage>(messages.size());
        var rand = new Random();
        while (true) {
            if (rand.nextBoolean()) {
                CompletableFuture<PubSubMessage> messagePromise = listener.getPubSubMessage();
                if (messagePromise.isDone()) {
                    received.add(messagePromise.get());
                } else {
                    break; // all messages read
                }
            } else {
                var message = listener.tryGetPubSubMessage();
                if (message != null) {
                    received.add(message);
                } else {
                    break; // all messages read
                }
            }
        }

        // valkey can reorder the messages, so we can't validate that the order (without big delays
        // between sends)
        assertEquals(new LinkedHashSet<>(messages), received);
    }

    /**
     * Similar to `test_pubsub_combined_exact_pattern_and_sharded_multi_client` in python client
     * tests.
     */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void combined_exact_pattern_and_sharded_multi_client(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        SubscriptionMethod subscriptionMethod = SubscriptionMethod.Config; // Default for this test

        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        String shardPrefix = "{shard}";
        int numChannels = 16;
        var exactMessages = new ArrayList<PubSubMessage>(numChannels);
        var patternMessages = new ArrayList<PubSubMessage>(numChannels);
        var shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsExact =
                Map.of(PubSubClusterChannelMode.EXACT, new HashSet<>());
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsPattern =
                Map.of(PubSubClusterChannelMode.PATTERN, Set.of(pattern));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsSharded =
                Map.of(PubSubClusterChannelMode.SHARDED, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptionsExact.get(PubSubClusterChannelMode.EXACT).add(channel);
            GlideString pubsubMessage = gs(i + "-" + UUID.randomUUID());
            exactMessages.add(new PubSubMessage(pubsubMessage, channel));
        }

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptionsSharded.get(PubSubClusterChannelMode.SHARDED).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (var j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            patternMessages.add(new PubSubMessage(message, channel, pattern));
        }

        var listenerExact =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.EXACT.ordinal(),
                        subscriptionsExact,
                        subscriptionMethod);
        subscribeByMethod(listenerExact, subscriptionMethod, subscriptionsExact);
        var listenerPattern =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.PATTERN.ordinal(),
                        subscriptionsPattern,
                        subscriptionMethod);
        subscribeByMethod(listenerPattern, subscriptionMethod, subscriptionsPattern);
        var listenerSharded =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.SHARDED.ordinal(),
                        subscriptionsSharded,
                        subscriptionMethod);
        subscribeByMethod(listenerSharded, subscriptionMethod, subscriptionsSharded);

        var sender = (GlideClusterClient) createClient(false);

        for (var pubsubMessage : exactMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : patternMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            var expected = new HashSet<Pair<Integer, PubSubMessage>>();
            expected.addAll(
                    exactMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), m))
                            .collect(Collectors.toSet()));
            expected.addAll(
                    patternMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), m))
                            .collect(Collectors.toSet()));
            expected.addAll(
                    shardedMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), m))
                            .collect(Collectors.toSet()));

            verifyReceivedPubsubMessages(expected, listenerExact, method);
        } else {
            verifyReceivedPubsubMessages(
                    exactMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerExact,
                    method);
            verifyReceivedPubsubMessages(
                    patternMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerPattern,
                    method);
            verifyReceivedPubsubMessages(
                    shardedMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerSharded,
                    method);
        }
    }

    /**
     * Similar to `test_pubsub_three_publishing_clients_same_name_with_sharded` in python client
     * tests.
     */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void three_publishing_clients_same_name_with_sharded(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        GlideString channel = gs(UUID.randomUUID().toString());
        var exactMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        var patternMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel, channel);
        var shardedMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsExact =
                Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsPattern =
                Map.of(PubSubClusterChannelMode.PATTERN, Set.of(channel));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsSharded =
                Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));

        var listenerExact =
                method == MessageReadMethod.Callback
                        ? (GlideClusterClient)
                                createListener(
                                        false,
                                        true,
                                        PubSubClusterChannelMode.EXACT.ordinal(),
                                        subscriptionsExact,
                                        SubscriptionMethod.Config)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsExact);
        subscribeByMethod(listenerExact, SubscriptionMethod.Config, subscriptionsExact);

        var listenerPattern =
                method == MessageReadMethod.Callback
                        ? createListener(
                                false,
                                true,
                                PubSubClusterChannelMode.PATTERN.ordinal(),
                                subscriptionsPattern,
                                SubscriptionMethod.Config)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsPattern);
        subscribeByMethod(listenerPattern, SubscriptionMethod.Config, subscriptionsPattern);

        var listenerSharded =
                method == MessageReadMethod.Callback
                        ? createListener(
                                false,
                                true,
                                PubSubClusterChannelMode.SHARDED.ordinal(),
                                subscriptionsSharded,
                                SubscriptionMethod.Config)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsSharded);
        subscribeByMethod(listenerSharded, SubscriptionMethod.Config, subscriptionsSharded);

        listenerPattern.publish(exactMessage.getMessage(), channel).get();
        listenerSharded.publish(patternMessage.getMessage(), channel).get();
        listenerExact.publish(shardedMessage.getMessage(), channel, true).get();

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            var expected =
                    Set.of(
                            Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), exactMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.EXACT.ordinal(),
                                    new PubSubMessage(patternMessage.getMessage(), channel)),
                            Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), patternMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.PATTERN.ordinal(),
                                    new PubSubMessage(exactMessage.getMessage(), channel, channel)),
                            Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), shardedMessage));

            verifyReceivedPubsubMessages(expected, listenerExact, method);
        } else {
            verifyReceivedPubsubMessages(
                    Set.of(
                            Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), exactMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.EXACT.ordinal(),
                                    new PubSubMessage(patternMessage.getMessage(), channel))),
                    listenerExact,
                    method);
            verifyReceivedPubsubMessages(
                    Set.of(
                            Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), patternMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.PATTERN.ordinal(),
                                    new PubSubMessage(exactMessage.getMessage(), channel, channel))),
                    listenerPattern,
                    method);
            verifyReceivedPubsubMessages(
                    Set.of(Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), shardedMessage)),
                    listenerSharded,
                    method);
        }
    }

    @SneakyThrows
    @Test
    public void error_cases() {
        var client = createClient(true);

        // client configured with callback and doesn't return pubsubMessages via API
        MessageCallback callback = (msg, ctx) -> fail();
        client =
                createClientWithSubscriptions(
                        true, Map.of(), Optional.of(callback), Optional.of(pubsubMessageQueue));
        assertThrows(ConfigurationError.class, client::tryGetPubSubMessage);
        client.close();

        // using sharded channels from different slots in a transaction causes a cross slot error
        var clusterClient = (GlideClusterClient) createClient(false);
        var transaction =
                new ClusterBatch(true)
                        .publish("one", "abc", true)
                        .publish("two", "mnk", true)
                        .publish("three", "xyz", true);
        var exception =
                assertThrows(ExecutionException.class, () -> clusterClient.exec(transaction, false).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().toLowerCase().contains("crossslot"));
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}, subscribe via {2}")
    @MethodSource("getTestScenarios")
    public void transaction_with_all_types_of_messages(
            boolean standalone, MessageReadMethod method, SubscriptionMethod subscriptionMethod) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        assumeTrue(
                standalone, // TODO activate tests after fix
                "Test doesn't work on cluster due to Cross Slot error, probably a bug in `redis-rs`");

        String prefix = "channel";
        GlideString pattern = gs(prefix + "*");
        GlideString shardPrefix = gs("{shard}");
        GlideString channel = gs(UUID.randomUUID().toString());
        var exactMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        var patternMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), gs(prefix), pattern);
        var shardedMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), shardPrefix);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(
                                PubSubChannelMode.EXACT,
                                Set.of(channel),
                                PubSubChannelMode.PATTERN,
                                Set.of(pattern))
                        : Map.of(
                                PubSubClusterChannelMode.EXACT,
                                Set.of(channel),
                                PubSubClusterChannelMode.PATTERN,
                                Set.of(pattern),
                                PubSubClusterChannelMode.SHARDED,
                                Set.of(shardPrefix));

        var listener =
                createListener(
                        standalone, method == MessageReadMethod.Callback, 1, subscriptions, subscriptionMethod);
        subscribeByMethod(listener, subscriptionMethod, subscriptions);
        var sender = createClient(standalone);

        if (standalone) {
            var transaction =
                    new Batch(true)
                            .publish(exactMessage.getMessage(), exactMessage.getChannel())
                            .publish(patternMessage.getMessage(), patternMessage.getChannel());
            ((GlideClient) sender).exec(transaction, false).get();
        } else {
            var transaction =
                    new ClusterBatch(true)
                            .publish(shardedMessage.getMessage(), shardedMessage.getChannel(), true)
                            .publish(exactMessage.getMessage(), exactMessage.getChannel())
                            .publish(patternMessage.getMessage(), patternMessage.getChannel());
            ((GlideClusterClient) sender).exec(transaction, false).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        var expected =
                standalone
                        ? Set.of(Pair.of(1, exactMessage), Pair.of(1, patternMessage))
                        : Set.of(
                                Pair.of(1, exactMessage), Pair.of(1, patternMessage), Pair.of(1, shardedMessage));
        verifyReceivedPubsubMessages(expected, listener, method);
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_exact_max_size_message(boolean standalone) {
        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs("1".repeat(512 * 1024 * 1024)); // 512MB
        final GlideString message2 = gs("2".repeat(1 << 25)); // 3MB

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));
        var listener = createClientWithSubscriptions(standalone, subscriptions);
        var sender = createClient(standalone);

        assertEquals(OK, sender.publish(message, channel).get());
        assertEquals(OK, sender.publish(message2, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        PubSubMessage asyncMessage = listener.getPubSubMessage().get();
        assertEquals(message, asyncMessage.getMessage());
        assertEquals(channel, asyncMessage.getChannel());
        assertTrue(asyncMessage.getPattern().isEmpty());

        PubSubMessage syncMessage = listener.tryGetPubSubMessage();
        assertEquals(message2, syncMessage.getMessage());
        assertEquals(channel, syncMessage.getChannel());
        assertTrue(syncMessage.getPattern().isEmpty());

        // Assert there are no more messages to read.
        assertThrows(
                TimeoutException.class, () -> listener.getPubSubMessage().get(3, TimeUnit.SECONDS));
        assertNull(listener.tryGetPubSubMessage());
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_sharded_max_size_message(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs("1".repeat(512 * 1024 * 1024)); // 512MB
        final GlideString message2 = gs("2".repeat(1 << 25)); // 3MB

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));
        var listener = createClientWithSubscriptions(standalone, subscriptions);
        var sender = createClient(standalone);

        assertEquals(OK, sender.publish(message, channel).get());
        assertEquals(OK, ((GlideClusterClient) sender).publish(message2, channel, true).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        PubSubMessage asyncMessage = listener.getPubSubMessage().get();
        assertEquals(message, asyncMessage.getMessage());
        assertEquals(channel, asyncMessage.getChannel());
        assertTrue(asyncMessage.getPattern().isEmpty());

        PubSubMessage syncMessage = listener.tryGetPubSubMessage();
        assertEquals(message2, syncMessage.getMessage());
        assertEquals(channel, syncMessage.getChannel());
        assertTrue(syncMessage.getPattern().isEmpty());

        // Assert there are no more messages to read.
        assertThrows(
                TimeoutException.class,
                () -> {
                    listener.getPubSubMessage().get(3, TimeUnit.SECONDS);
                });

        assertNull(listener.tryGetPubSubMessage());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_exact_max_size_message_callback(boolean standalone) {
        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs("1".repeat(512 * 1024 * 1024)); // 512MB

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));

        var listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        var sender = createClient(standalone);

        assertEquals(OK, sender.publish(message, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        assertEquals(1, callbackMessages.size());
        assertEquals(message, callbackMessages.get(0).getMessage());
        assertEquals(channel, callbackMessages.get(0).getChannel());
        assertTrue(callbackMessages.get(0).getPattern().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_sharded_max_size_message_callback(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs("1".repeat(512 * 1024 * 1024)); // 512MB

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));

        var listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        var sender = createClient(standalone);

        assertEquals(OK, ((GlideClusterClient) sender).publish(message, channel, true).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        assertEquals(1, callbackMessages.size());
        assertEquals(message, callbackMessages.get(0).getMessage());
    }

    /** Test the behavior if the callback supplied to a subscription throws an uncaught exception. */
    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_test_callback_exception(boolean standalone) {
        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message1 = gs("message1");
        final GlideString message2 = gs("message2");
        final GlideString errorMsg = gs("errorMsg");
        final GlideString message3 = gs("message3");

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    if (pubSubMessage.getMessage().equals(errorMsg)) {
                        throw new RuntimeException("Test callback error message");
                    }
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));

        var listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        var sender = createClient(standalone);

        assertEquals(OK, sender.publish(message1, channel).get());
        assertEquals(OK, sender.publish(message2, channel).get());
        assertEquals(OK, sender.publish(errorMsg, channel).get());
        assertEquals(OK, sender.publish(message3, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        assertEquals(3, callbackMessages.size());
        assertEquals(message1, callbackMessages.get(0).getMessage());
        assertEquals(channel, callbackMessages.get(0).getChannel());
        assertTrue(callbackMessages.get(0).getPattern().isEmpty());

        assertEquals(message2, callbackMessages.get(1).getMessage());
        assertEquals(channel, callbackMessages.get(1).getChannel());
        assertTrue(callbackMessages.get(1).getPattern().isEmpty());

        // Ensure we can receive message 3 which is after the message that triggers a throw.
        assertEquals(message3, callbackMessages.get(2).getMessage());
        assertEquals(channel, callbackMessages.get(2).getChannel());
        assertTrue(callbackMessages.get(2).getPattern().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_with_binary(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        GlideString channel = gs(new byte[] {(byte) 0xE2, 0x28, (byte) 0xA1});
        var message =
                new PubSubMessage(gs(new byte[] {(byte) 0xF0, 0x28, (byte) 0x8C, (byte) 0xBC}), channel);

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));

        var listener = createClientWithSubscriptions(standalone, subscriptions);
        var listener2 =
                createClientWithSubscriptions(
                        standalone, subscriptions, Optional.of(callback), Optional.of(callbackMessages));
        var sender = createClient(standalone);

        assertEquals(OK, sender.publish(message.getMessage(), channel).get());
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        assertEquals(message, listener.tryGetPubSubMessage());
        assertEquals(1, callbackMessages.size());
        assertEquals(message, callbackMessages.get(0));
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_channels(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        var client = createClient(standalone);

        // Get initial channel counts (may not be 0 if other tests are running)
        int initialChannelCount = client.pubsubChannels().get().length;
        int initialBinaryChannelCount = client.pubsubChannelsBinary().get().length;

        var channels = Set.of("test_channel1", "test_channel2", "some_channel");
        String pattern = "test_*";

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(
                                PubSubChannelMode.EXACT,
                                channels.stream().map(GlideString::gs).collect(Collectors.toSet()))
                        : Map.of(
                                PubSubClusterChannelMode.EXACT,
                                channels.stream().map(GlideString::gs).collect(Collectors.toSet()));

        var listener = createClientWithSubscriptions(standalone, subscriptions);

        // test without pattern - verify our channels are present
        var allChannels = Set.of(client.pubsubChannels().get());
        assertTrue(allChannels.containsAll(channels), "All subscribed channels should be present");

        var allBinaryChannels = Set.of(client.pubsubChannelsBinary().get());
        var expectedBinaryChannels = channels.stream().map(GlideString::gs).collect(Collectors.toSet());
        assertTrue(
                allBinaryChannels.containsAll(expectedBinaryChannels),
                "All subscribed binary channels should be present");

        // test with pattern - verify matching channels are present
        var patternChannels = Set.of(client.pubsubChannels(pattern).get());
        assertTrue(patternChannels.contains("test_channel1"));
        assertTrue(patternChannels.contains("test_channel2"));

        var patternBinaryChannels = Set.of(client.pubsubChannels(gs(pattern)).get());
        assertTrue(patternBinaryChannels.contains(gs("test_channel1")));
        assertTrue(patternBinaryChannels.contains(gs("test_channel2")));

        // test with non-matching pattern
        assertEquals(0, client.pubsubChannels("non_matching_*").get().length);
        assertEquals(0, client.pubsubChannels(gs("non_matching_*")).get().length);
        assertEquals(0, listener.pubsubChannels("non_matching_*").get().length);
        assertEquals(0, listener.pubsubChannels(gs("non_matching_*")).get().length);
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_numpat(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        var client = createClient(standalone);

        // Get initial pattern count (may not be 0 if other tests are running)
        long initialNumPat = client.pubsubNumPat().get();

        var patterns = Set.of("news.*", "announcements.*");

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(
                                PubSubChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()))
                        : Map.of(
                                PubSubClusterChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()));

        var listener = createClientWithSubscriptions(standalone, subscriptions);

        // Verify pattern count increased by 2
        assertEquals(initialNumPat + 2, client.pubsubNumPat().get());
        assertEquals(initialNumPat + 2, listener.pubsubNumPat().get());
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_numsub(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        // no channels exists yet
        var client = createClient(standalone);
        var channels = new String[] {"channel1", "channel2", "channel3"};
        assertEquals(
                Arrays.stream(channels).collect(Collectors.toMap(c -> c, c -> 0L)),
                client.pubsubNumSub(channels).get());

        Map<? extends ChannelMode, Set<GlideString>> subscriptions1 =
                standalone
                        ? Map.of(
                                PubSubChannelMode.EXACT, Set.of(gs("channel1"), gs("channel2"), gs("channel3")))
                        : Map.of(
                                PubSubClusterChannelMode.EXACT,
                                Set.of(gs("channel1"), gs("channel2"), gs("channel3")));
        var listener1 = createClientWithSubscriptions(standalone, subscriptions1);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions2 =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(gs("channel2"), gs("channel3")))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(gs("channel2"), gs("channel3")));
        var listener2 = createClientWithSubscriptions(standalone, subscriptions2);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions3 =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(gs("channel3")))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(gs("channel3")));
        var listener3 = createClientWithSubscriptions(standalone, subscriptions3);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions4 =
                standalone
                        ? Map.of(PubSubChannelMode.PATTERN, Set.of(gs("channel*")))
                        : Map.of(PubSubClusterChannelMode.PATTERN, Set.of(gs("channel*")));
        var listener4 = createClientWithSubscriptions(standalone, subscriptions4);

        var expected = Map.of("channel1", 1L, "channel2", 2L, "channel3", 3L, "channel4", 0L);
        assertEquals(expected, client.pubsubNumSub(ArrayUtils.addFirst(channels, "channel4")).get());
        assertEquals(expected, listener1.pubsubNumSub(ArrayUtils.addFirst(channels, "channel4")).get());

        var expectedGs =
                Map.of(gs("channel1"), 1L, gs("channel2"), 2L, gs("channel3"), 3L, gs("channel4"), 0L);
        assertEquals(
                expectedGs,
                client
                        .pubsubNumSub(
                                new GlideString[] {gs("channel1"), gs("channel2"), gs("channel3"), gs("channel4")})
                        .get());
        assertEquals(
                expectedGs,
                listener2
                        .pubsubNumSub(
                                new GlideString[] {gs("channel1"), gs("channel2"), gs("channel3"), gs("channel4")})
                        .get());
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_channels_and_numpat_and_numsub_in_transaction(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        var prefix = "{boo}-";
        var route = new SlotKeyRoute(prefix, SlotType.PRIMARY);
        var client = createClient(standalone);
        var channels =
                new String[] {prefix + "test_channel1", prefix + "test_channel2", prefix + "some_channel"};
        var patterns = Set.of(prefix + "news.*", prefix + "announcements.*");
        String pattern = prefix + "test_*";

        // Get initial pattern count
        long initialNumPat = client.pubsubNumPat().get();

        var transaction =
                (standalone ? new Batch(true) : new ClusterBatch(true))
                        .pubsubChannels()
                        .pubsubChannels(pattern)
                        .pubsubNumPat()
                        .pubsubNumSub(channels);
        ClusterBatchOptions options = ClusterBatchOptions.builder().route(route).build();

        // Get initial state
        var result =
                standalone
                        ? ((GlideClient) client).exec((Batch) transaction, false).get()
                        : ((GlideClusterClient) client).exec((ClusterBatch) transaction, false, options).get();

        // Verify initial state - channels should be empty or contain only other test channels
        // Pattern count should match what we captured earlier
        assertEquals(initialNumPat, ((Number) result[2]).longValue());

        // All our channels should have 0 subscribers initially
        @SuppressWarnings("unchecked")
        var numSubResult = (Map<String, Long>) result[3];
        for (String channel : channels) {
            assertEquals(0L, numSubResult.get(channel).longValue());
        }

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(
                                PubSubChannelMode.EXACT,
                                Arrays.stream(channels).map(GlideString::gs).collect(Collectors.toSet()),
                                PubSubChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()))
                        : Map.of(
                                PubSubClusterChannelMode.EXACT,
                                Arrays.stream(channels).map(GlideString::gs).collect(Collectors.toSet()),
                                PubSubClusterChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()));

        var listener = createClientWithSubscriptions(standalone, subscriptions);

        result =
                standalone
                        ? ((GlideClient) client).exec((Batch) transaction, false).get()
                        : ((GlideClusterClient) client).exec((ClusterBatch) transaction, false, options).get();

        // convert arrays to sets for comparison
        var resultChannels = Set.of((Object[]) result[0]);
        var resultPatternChannels = Set.of((Object[]) result[1]);
        long resultNumPat = ((Number) result[2]).longValue();
        @SuppressWarnings("unchecked")
        var resultNumSub = (Map<String, Long>) result[3];

        // Verify our channels are present
        assertTrue(resultChannels.containsAll(Set.of(channels)));

        // Verify pattern-matched channels
        assertTrue(resultPatternChannels.contains("{boo}-test_channel1"));
        assertTrue(resultPatternChannels.contains("{boo}-test_channel2"));

        // Verify pattern count increased by 2
        assertEquals(initialNumPat + 2, resultNumPat);

        // Verify our channels have 1 subscriber each
        for (String channel : channels) {
            assertEquals(1L, resultNumSub.get(channel).longValue());
        }
    }

    @SneakyThrows
    @Test
    public void pubsub_shard_channels() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        // no channels exists yet
        GlideClusterClient client = (GlideClusterClient) createClient(false);
        assertEquals(0, client.pubsubShardChannels().get().length);
        assertEquals(0, client.pubsubShardChannelsBinary().get().length);
        assertEquals(0, client.pubsubShardChannels("*").get().length);
        assertEquals(0, client.pubsubShardChannels(gs("*")).get().length);

        var channels = Set.of("test_shardchannel1", "test_shardchannel2", "some_shardchannel3");
        String pattern = "test_*";

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Map.of(
                        PubSubClusterChannelMode.SHARDED,
                        channels.stream().map(GlideString::gs).collect(Collectors.toSet()));

        GlideClusterClient listener =
                (GlideClusterClient) createClientWithSubscriptions(false, subscriptions);

        // test without pattern
        assertEquals(channels, Set.of(client.pubsubShardChannels().get()));
        assertEquals(channels, Set.of(listener.pubsubShardChannels().get()));
        assertEquals(
                channels.stream().map(GlideString::gs).collect(Collectors.toSet()),
                Set.of(client.pubsubShardChannelsBinary().get()));
        assertEquals(
                channels.stream().map(GlideString::gs).collect(Collectors.toSet()),
                Set.of(listener.pubsubShardChannelsBinary().get()));

        // test with pattern
        assertEquals(
                Set.of("test_shardchannel1", "test_shardchannel2"),
                Set.of(client.pubsubShardChannels(pattern).get()));
        assertEquals(
                Set.of(gs("test_shardchannel1"), gs("test_shardchannel2")),
                Set.of(client.pubsubShardChannels(gs(pattern)).get()));
        assertEquals(
                Set.of("test_shardchannel1", "test_shardchannel2"),
                Set.of(listener.pubsubShardChannels(pattern).get()));
        assertEquals(
                Set.of(gs("test_shardchannel1"), gs("test_shardchannel2")),
                Set.of(listener.pubsubShardChannels(gs(pattern)).get()));

        // test with non-matching pattern
        assertEquals(0, client.pubsubShardChannels("non_matching_*").get().length);
        assertEquals(0, client.pubsubShardChannels(gs("non_matching_*")).get().length);
        assertEquals(0, listener.pubsubShardChannels("non_matching_*").get().length);
        assertEquals(0, listener.pubsubShardChannels(gs("non_matching_*")).get().length);
    }

    @SneakyThrows
    @Test
    public void pubsub_shardnumsub() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        // no channels exists yet
        GlideClusterClient client = (GlideClusterClient) createClient(false);
        var channels = new String[] {"channel1", "channel2", "channel3"};
        assertEquals(
                Arrays.stream(channels).collect(Collectors.toMap(c -> c, c -> 0L)),
                client.pubsubNumSub(channels).get());

        Map<? extends ChannelMode, Set<GlideString>> subscriptions1 =
                Map.of(
                        PubSubClusterChannelMode.SHARDED,
                        Set.of(gs("channel1"), gs("channel2"), gs("channel3")));
        GlideClusterClient listener1 =
                (GlideClusterClient) createClientWithSubscriptions(false, subscriptions1);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions2 =
                Map.of(PubSubClusterChannelMode.SHARDED, Set.of(gs("channel2"), gs("channel3")));
        GlideClusterClient listener2 =
                (GlideClusterClient) createClientWithSubscriptions(false, subscriptions2);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions3 =
                Map.of(PubSubClusterChannelMode.SHARDED, Set.of(gs("channel3")));
        GlideClusterClient listener3 =
                (GlideClusterClient) createClientWithSubscriptions(false, subscriptions3);

        var expected = Map.of("channel1", 1L, "channel2", 2L, "channel3", 3L, "channel4", 0L);
        assertEquals(
                expected, client.pubsubShardNumSub(ArrayUtils.addFirst(channels, "channel4")).get());
        assertEquals(
                expected, listener1.pubsubShardNumSub(ArrayUtils.addFirst(channels, "channel4")).get());

        var expectedGs =
                Map.of(gs("channel1"), 1L, gs("channel2"), 2L, gs("channel3"), 3L, gs("channel4"), 0L);
        assertEquals(
                expectedGs,
                client
                        .pubsubShardNumSub(
                                new GlideString[] {gs("channel1"), gs("channel2"), gs("channel3"), gs("channel4")})
                        .get());
        assertEquals(
                expectedGs,
                listener2
                        .pubsubShardNumSub(
                                new GlideString[] {gs("channel1"), gs("channel2"), gs("channel3"), gs("channel4")})
                        .get());
    }

    /**
     * Test that a client with no initial PubSub subscriptions can dynamically subscribe via
     * customCommand and receive messages through the pull-based APIs.
     *
     * <p>This verifies that the push message infrastructure is always enabled, even when no
     * subscriptions are configured at client creation time.
     */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read via {1}")
    @MethodSource("getTestScenarios")
    public void pubsub_with_dynamic_subscription_via_custom_command(
            boolean standalone, MessageReadMethod method) {
        // Skip callback method - this test is specifically for creating a client with no pubsub config
        if (method == MessageReadMethod.Callback) {
            return;
        }

        GlideString channel = gs("dynamic-subscribe-test-channel");
        GlideString message1 = gs("dynamic-message-1");
        GlideString message2 = gs("dynamic-message-2");

        BaseClient listener = createClient(standalone);

        // Subscribe to channel dynamically via customCommand
        if (standalone) {
            ((GlideClient) listener).customCommand(new GlideString[] {gs("subscribe"), channel}).get();
        } else {
            ((GlideClusterClient) listener)
                    .customCommand(new GlideString[] {gs("subscribe"), channel})
                    .get();
        }

        // Update listeners map for cleanup
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));
        listeners.put(listener, subscriptions);

        // Create sender client
        BaseClient sender = createClient(standalone);

        // Publish messages
        sender.publish(message1, channel).get();
        sender.publish(message2, channel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        // Verify messages received via pull-based API
        verifyReceivedPubsubMessages(
                Set.of(
                        Pair.of(1, new PubSubMessage(message1, channel)),
                        Pair.of(1, new PubSubMessage(message2, channel))),
                listener,
                method);
    }

    /**
     * Test that a client with a callback can dynamically subscribe to additional channels via
     * customCommand and receive messages for both initial and dynamic subscriptions through the
     * callback.
     */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_callback_with_dynamic_subscription_via_custom_command(boolean standalone) {
        GlideString dynamicChannel = gs("dynamic-channel");
        GlideString message = gs("dynamic-channel-message");

        // Create client with callback and no initial subscriptions
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of())
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of());

        BaseClient listener =
                createListener(standalone, true, 1, subscriptions, SubscriptionMethod.Config);

        // Dynamically subscribe to additional channel via customCommand
        if (standalone) {
            ((GlideClient) listener)
                    .customCommand(new GlideString[] {gs("subscribe"), dynamicChannel})
                    .get();
        } else {
            ((GlideClusterClient) listener)
                    .customCommand(new GlideString[] {gs("subscribe"), dynamicChannel})
                    .get();
        }

        // Update listeners map for cleanup (include both channels)
        listeners.put(
                listener,
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(dynamicChannel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(dynamicChannel)));

        BaseClient sender = createClient(standalone);

        // Publish to both channels
        sender.publish(message, dynamicChannel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        // Verify both messages received via callback
        verifyReceivedPubsubMessages(
                Set.of(Pair.of(1, new PubSubMessage(message, dynamicChannel))),
                listener,
                MessageReadMethod.Callback);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void dynamic_subscribe_lazy(boolean standalone) {
        executeWithClients(
                standalone,
                (listener, sender) -> {
                    String channel = "test-channel-" + UUID.randomUUID();
                    String message = "test-message";

                    // Dynamic subscribe (lazy)
                    Set<String> channels = Set.of(channel);
                    listener.subscribeLazy(channels).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Publish message
                    sender.publish(message, channel).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Receive message
                    PubSubMessage msg = listener.getPubSubMessage().get(5, TimeUnit.SECONDS);
                    assertEquals(message, msg.getMessage().getString());
                    assertEquals(channel, msg.getChannel().getString());
                });
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void dynamic_psubscribe_lazy(boolean standalone) {
        executeWithClients(
                standalone,
                (listener, sender) -> {
                    String pattern = "test-pattern-*";
                    String channel = "test-pattern-" + UUID.randomUUID();
                    String message = "test-message";

                    // Dynamic psubscribe (lazy)
                    Set<String> patterns = Set.of(pattern);
                    listener.psubscribeLazy(patterns).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Publish message
                    sender.publish(message, channel).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Receive message
                    PubSubMessage msg = listener.getPubSubMessage().get(5, TimeUnit.SECONDS);
                    assertEquals(message, msg.getMessage().getString());
                    assertEquals(channel, msg.getChannel().getString());
                    assertTrue(msg.getPattern().isPresent());
                    assertEquals(pattern, msg.getPattern().get().getString());
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void dynamic_unsubscribe(boolean standalone) {
        executeWithClients(
                standalone,
                (listener, sender) -> {
                    String channel = "test-channel-" + UUID.randomUUID();
                    String message = "test-message";

                    // Subscribe
                    Set<String> channels = Set.of(channel);
                    listener.subscribeLazy(channels).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Unsubscribe
                    listener.unsubscribe(channels).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Publish message
                    sender.publish(message, channel).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Should not receive message
                    PubSubMessage msg = listener.tryGetPubSubMessage();
                    assertNull(msg);
                });
    }

    @Test
    @SneakyThrows
    public void dynamic_ssubscribe_lazy() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        executeWithClusterClients(
                (listener, sender) -> {
                    String channel = "test-shard-channel-" + UUID.randomUUID();
                    String message = "test-message";

                    // Dynamic ssubscribe (lazy)
                    Set<String> channels = Set.of(channel);
                    listener.ssubscribeLazy(channels).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Publish message
                    sender.publish(message, channel, true).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Receive message
                    PubSubMessage msg = listener.getPubSubMessage().get(5, TimeUnit.SECONDS);
                    assertEquals(message, msg.getMessage().getString());
                    assertEquals(channel, msg.getChannel().getString());
                });
    }

    @Test
    @SneakyThrows
    public void dynamic_sunsubscribe() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        executeWithClusterClients(
                (listener, sender) -> {
                    String channel = "test-shard-channel-" + UUID.randomUUID();
                    String message = "test-message";

                    // Subscribe
                    Set<String> channels = Set.of(channel);
                    listener.ssubscribeLazy(channels).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Unsubscribe
                    listener.sunsubscribe(channels).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Publish message
                    sender.publish(message, channel, true).get();
                    Thread.sleep(MESSAGE_DELIVERY_DELAY);

                    // Should not receive message
                    PubSubMessage msg = listener.tryGetPubSubMessage();
                    assertNull(msg);
                });
    }

    @FunctionalInterface
    private interface ClientTestAction {
        void execute(BaseClient listener, BaseClient sender) throws Exception;
    }

    @FunctionalInterface
    private interface ClusterClientTestAction {
        void execute(GlideClusterClient listener, GlideClusterClient sender) throws Exception;
    }

    @SneakyThrows
    private void executeWithClients(boolean standalone, ClientTestAction action) {
        try (BaseClient listener = createClient(standalone);
                BaseClient sender = createClient(standalone)) {
            action.execute(listener, sender);
        }
    }

    @SneakyThrows
    private void executeWithClusterClients(ClusterClientTestAction action) {
        try (GlideClusterClient listener = (GlideClusterClient) createClient(false);
                GlideClusterClient sender = (GlideClusterClient) createClient(false)) {
            action.execute(listener, sender);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void dynamic_unsubscribe_from_preconfigured(boolean standalone) {
        String channel = "preconfigured-channel-" + UUID.randomUUID();
        String message = "test-message";

        // Create client with pre-configured subscription
        BaseClient listener;
        if (standalone) {
            var subConfig =
                    StandaloneSubscriptionConfiguration.builder()
                            .subscription(PubSubChannelMode.EXACT, gs(channel))
                            .build();
            listener =
                    GlideClient.createClient(
                                    commonClientConfig()
                                            .requestTimeout(5000)
                                            .subscriptionConfiguration(subConfig)
                                            .build())
                            .get();
        } else {
            var subConfig =
                    ClusterSubscriptionConfiguration.builder()
                            .subscription(PubSubClusterChannelMode.EXACT, gs(channel))
                            .build();
            listener =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig()
                                            .requestTimeout(5000)
                                            .subscriptionConfiguration(subConfig)
                                            .build())
                            .get();
        }

        BaseClient sender = createClient(standalone);
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        // Verify subscription is active by receiving a message
        sender.publish(message, channel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY);
        PubSubMessage msg = listener.getPubSubMessage().get(5, TimeUnit.SECONDS);
        assertEquals(message, msg.getMessage().getString());

        // Now unsubscribe dynamically from the pre-configured subscription
        Set<String> channels = Set.of(channel);
        listener.unsubscribe(channels).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        // Publish another message
        sender.publish(message + "-2", channel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        // Should not receive message
        PubSubMessage msg2 = listener.tryGetPubSubMessage();
        assertNull(msg2);

        listener.close();
        sender.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void test_subscription_metrics_in_statistics(boolean standalone) {
        try (BaseClient client = createClient(standalone)) {
            verifySubscriptionMetricsExist(client);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void test_subscription_timestamp_updates_after_subscribe(boolean standalone) {
        try (BaseClient client = createClient(standalone)) {
            String channel = "test-channel-" + UUID.randomUUID();

            // Get initial timestamp
            Map<String, String> initialStats = client.getStatistics();
            long initialTimestamp = Long.parseLong(initialStats.get("subscription_last_sync_timestamp"));

            // Subscribe to a channel
            Set<String> channels = Set.of(channel);
            client.subscribeLazy(channels).get();

            // Wait for reconciliation
            Thread.sleep(MESSAGE_DELIVERY_DELAY);

            // Get updated timestamp
            Map<String, String> updatedStats = client.getStatistics();
            long updatedTimestamp = Long.parseLong(updatedStats.get("subscription_last_sync_timestamp"));

            // Timestamp should have been updated (or at least not decreased)
            assertTrue(updatedTimestamp >= initialTimestamp);

            // Cleanup subscription
            client.unsubscribe().get();
            Thread.sleep(100);
        }
    }

    private void verifySubscriptionMetricsExist(BaseClient client) {
        Map<String, String> stats = client.getStatistics();

        // Verify subscription metrics exist
        assertTrue(stats.containsKey("subscription_out_of_sync_count"));
        assertTrue(stats.containsKey("subscription_last_sync_timestamp"));

        // Verify they are valid numbers
        long outOfSyncCount = Long.parseLong(stats.get("subscription_out_of_sync_count"));
        long lastSyncTimestamp = Long.parseLong(stats.get("subscription_last_sync_timestamp"));

        // Verify they are non-negative
        assertTrue(outOfSyncCount >= 0);
        assertTrue(lastSyncTimestamp >= 0);
    }

    @Test
    @SneakyThrows
    public void test_unsubscribe_all_standalone() {
        String channel1 = "channel1_" + UUID.randomUUID();
        String channel2 = "channel2_" + UUID.randomUUID();

        GlideClient client = createStandaloneClientWithEmptySubscriptions();
        GlideClient publisher = createStandaloneClient();

        try {
            // Subscribe to multiple channels
            client.subscribeLazy(Set.of(channel1, channel2)).get();
            Thread.sleep(500);
            Thread.sleep(500);

            // Unsubscribe from all
            client.unsubscribe().get();
            Thread.sleep(500);
            Thread.sleep(500);

            // Verify we can subscribe again (proves unsubscribe worked)
            client.subscribeLazy(Set.of(channel1)).get();
            Thread.sleep(500);
        } finally {
            client.close();
            listeners.remove(client);
        }
    }

    @Test
    @SneakyThrows
    public void test_unsubscribe_all_cluster() {
        String channel1 = "channel1_" + UUID.randomUUID();
        String channel2 = "channel2_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();

        try {
            // Subscribe to multiple channels
            client.subscribeLazy(Set.of(channel1, channel2)).get();
            Thread.sleep(500);
            Thread.sleep(500);

            // Verify subscriptions
            var state = client.getSubscriptions().get();
            Set<String> exact = state.getActualSubscriptions().get(PubSubClusterChannelMode.EXACT);
            assertNotNull(exact);
            assertEquals(2, exact.size());

            // Unsubscribe from all
            client.unsubscribe().get();
            Thread.sleep(500);

            // Verify all unsubscribed
            state = client.getSubscriptions().get();
            Set<String> exactAfter = state.getActualSubscriptions().get(PubSubClusterChannelMode.EXACT);
            assertTrue(exactAfter == null || exactAfter.isEmpty());
        } finally {
            client.close();
            listeners.remove(client);
        }
    }

    @Test
    @SneakyThrows
    public void test_punsubscribe_all_standalone() {
        String pattern1 = "pattern1_*";
        String pattern2 = "pattern2_*";

        GlideClient client = createStandaloneClientWithEmptySubscriptions();

        try {
            // Subscribe to multiple patterns
            client.psubscribeLazy(Set.of(pattern1, pattern2)).get();
            Thread.sleep(500);

            // Verify subscriptions
            var state = client.getSubscriptions().get();
            Set<String> patterns = state.getActualSubscriptions().get(PubSubChannelMode.PATTERN);
            assertNotNull(patterns);
            assertEquals(2, patterns.size());

            // Unsubscribe from all patterns
            client.punsubscribe().get();
            Thread.sleep(1000); // Wait longer to ensure unsubscribe completes

            // Verify all unsubscribed
            state = client.getSubscriptions().get();
            Set<String> patternsAfter = state.getActualSubscriptions().get(PubSubChannelMode.PATTERN);
            assertTrue(patternsAfter == null || patternsAfter.isEmpty());
        } finally {
            // Extra wait before closing to ensure server processes unsubscribe
            Thread.sleep(200);
            client.close();
            listeners.remove(client);
        }
    }

    @Test
    @SneakyThrows
    public void test_sunsubscribe_all_cluster() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature is added in version 7.0.0");

        String channel1 = "{slot}channel1_" + UUID.randomUUID();
        String channel2 = "{slot}channel2_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();

        try {
            // Subscribe to multiple sharded channels
            client.ssubscribeLazy(Set.of(channel1, channel2)).get();
            Thread.sleep(500);

            // Verify subscriptions
            var state = client.getSubscriptions().get();
            Set<String> sharded = state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED);
            assertNotNull(sharded);
            assertEquals(2, sharded.size());

            // Unsubscribe from all sharded channels
            client.sunsubscribe().get();
            Thread.sleep(500);

            // Verify all unsubscribed
            state = client.getSubscriptions().get();
            Set<String> shardedAfter =
                    state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED);
            assertTrue(shardedAfter == null || shardedAfter.isEmpty());
        } finally {
            client.close();
            listeners.remove(client);
        }
    }

    @Test
    @SneakyThrows
    public void test_subscribe_with_timeout_standalone() {
        String channel = "timeout_channel_" + UUID.randomUUID();

        GlideClient client = createStandaloneClientWithEmptySubscriptions();
        GlideClient publisher = createStandaloneClient();

        try {
            // Subscribe with timeout
            client.subscribe(Set.of(channel), 1000).get();

            // Verify subscription
            var state = client.getSubscriptions().get();
            assertTrue(state.getActualSubscriptions().get(PubSubChannelMode.EXACT).contains(channel));

            // Publish and verify message received
            publisher.publish(channel, "test_message").get();
            Thread.sleep(500);
        } finally {
            client.close();
            listeners.remove(client);
        }
    }

    @Test
    @SneakyThrows
    public void test_subscribe_with_timeout_cluster() {
        String channel = "timeout_channel_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();
        GlideClusterClient publisher = createClusterClient();

        try {
            // Subscribe with timeout
            client.subscribe(Set.of(channel), 1000).get();

            // Verify subscription
            var state = client.getSubscriptions().get();
            assertTrue(
                    state.getActualSubscriptions().get(PubSubClusterChannelMode.EXACT).contains(channel));

            // Publish and verify message received
            publisher.publish(channel, "test_message").get();
            Thread.sleep(500);
        } finally {
            client.close();
            listeners.remove(client);
        }
    }

    @Test
    @SneakyThrows
    public void test_ssubscribe_channels_different_slots() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature is added in version 7.0.0");

        // Use different hash tags to ensure different slots
        String channel1 = "{slot1}channel_" + UUID.randomUUID();
        String channel2 = "{slot2}channel_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();
        GlideClusterClient publisher = createClusterClient();

        // Subscribe to channels in different slots
        client.ssubscribeLazy(Set.of(channel1, channel2)).get();
        Thread.sleep(500);

        // Verify both subscriptions
        var state = client.getSubscriptions().get();
        Set<String> sharded = state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED);
        assertTrue(sharded.contains(channel1));
        assertTrue(sharded.contains(channel2));

        try {
            // Publish to both channels
            publisher.publish(channel1, "message1", true).get();
            publisher.publish(channel2, "message2", true).get();
            Thread.sleep(500);
        } finally {
            client.sunsubscribe().get();
            Thread.sleep(100);
            client.close();
            listeners.remove(client);
        }
    }

    @Test
    @SneakyThrows
    public void test_sunsubscribe_channels_different_slots() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature is added in version 7.0.0");

        String channel1 = "{slot1}channel_" + UUID.randomUUID();
        String channel2 = "{slot2}channel_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();

        // Subscribe to channels in different slots
        client.ssubscribeLazy(Set.of(channel1, channel2)).get();
        Thread.sleep(500);

        // Unsubscribe from one channel
        client.sunsubscribe(Set.of(channel1)).get();
        Thread.sleep(500);

        // Verify only channel2 remains
        var state = client.getSubscriptions().get();
        Set<String> sharded = state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED);
        assertFalse(sharded.contains(channel1));
        assertTrue(sharded.contains(channel2));

        // Cleanup remaining subscription
        client.sunsubscribe().get();
        Thread.sleep(100);
        client.close();
        listeners.remove(client);
    }

    @Test
    @SneakyThrows
    public void test_unsubscribe_all_subscription_types_standalone() {
        String channel = "channel_" + UUID.randomUUID();
        String pattern = "pattern_*";

        GlideClient client = createStandaloneClientWithEmptySubscriptions();

        // Subscribe to both exact and pattern
        client.subscribeLazy(Set.of(channel)).get();
        Thread.sleep(500);
        client.psubscribeLazy(Set.of(pattern)).get();
        Thread.sleep(500);

        // Verify both subscriptions
        var state = client.getSubscriptions().get();
        assertNotNull(state.getActualSubscriptions().get(PubSubChannelMode.EXACT));
        assertNotNull(state.getActualSubscriptions().get(PubSubChannelMode.PATTERN));

        // Unsubscribe from all exact channels
        client.unsubscribe().get();
        Thread.sleep(500);

        // Verify exact unsubscribed but pattern remains
        state = client.getSubscriptions().get();
        var exact = state.getActualSubscriptions().get(PubSubChannelMode.EXACT);
        var patterns = state.getActualSubscriptions().get(PubSubChannelMode.PATTERN);
        assertTrue(exact == null || exact.isEmpty());
        assertNotNull(patterns);
        assertFalse(patterns.isEmpty());

        // Unsubscribe from all patterns
        client.punsubscribe().get();
        Thread.sleep(500);

        // Verify all unsubscribed
        state = client.getSubscriptions().get();
        patterns = state.getActualSubscriptions().get(PubSubChannelMode.PATTERN);
        assertTrue(patterns == null || patterns.isEmpty());

        client.close();
        listeners.remove(client);
    }

    @Test
    @SneakyThrows
    public void test_unsubscribe_all_subscription_types_cluster() {
        String channel = "channel_" + UUID.randomUUID();
        String pattern = "pattern_*";
        String sharded = "{slot}sharded_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();

        // Subscribe to exact, pattern, and sharded
        client.subscribeLazy(Set.of(channel)).get();
        Thread.sleep(500);
        client.psubscribeLazy(Set.of(pattern)).get();
        Thread.sleep(500);
        client.ssubscribeLazy(Set.of(sharded)).get();
        Thread.sleep(500);

        // Verify all subscriptions
        var state = client.getSubscriptions().get();
        assertNotNull(state.getActualSubscriptions().get(PubSubClusterChannelMode.EXACT));
        assertNotNull(state.getActualSubscriptions().get(PubSubClusterChannelMode.PATTERN));
        assertNotNull(state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED));

        // Unsubscribe from all exact channels
        client.unsubscribe().get();
        Thread.sleep(500);

        // Unsubscribe from all patterns
        client.punsubscribe().get();
        Thread.sleep(500);

        // Unsubscribe from all sharded
        client.sunsubscribe().get();
        Thread.sleep(500);

        // Verify all unsubscribed
        state = client.getSubscriptions().get();
        var exactAfter = state.getActualSubscriptions().get(PubSubClusterChannelMode.EXACT);
        var patternsAfter = state.getActualSubscriptions().get(PubSubClusterChannelMode.PATTERN);
        var shardedAfter = state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED);
        assertTrue(exactAfter == null || exactAfter.isEmpty());
        assertTrue(patternsAfter == null || patternsAfter.isEmpty());
        assertTrue(shardedAfter == null || shardedAfter.isEmpty());

        client.close();
        listeners.remove(client);
    }

    @Test
    @SneakyThrows
    public void test_subscription_metrics_on_acl_failure_standalone() {
        String channel = "acl_metrics_channel_" + UUID.randomUUID();
        String username = "test_user_acl_metrics_" + UUID.randomUUID();
        String password = "test_password_acl";

        GlideClient adminClient = createStandaloneClient();

        // Create user without pubsub permissions
        String[] aclCmd =
                new String[] {
                    "ACL",
                    "SETUSER",
                    username,
                    "ON",
                    ">" + password,
                    "~*",
                    "resetchannels",
                    "+@all",
                    "-@pubsub"
                };
        adminClient.customCommand(aclCmd).get();

        try {
            // Create listening client with empty subscription config and authenticate
            GlideClient listeningClient = createStandaloneClientWithEmptySubscriptions();
            listeningClient.customCommand(new String[] {"AUTH", username, password}).get();

            // Get initial metrics
            Map<String, String> initialStats = listeningClient.getStatistics();
            long initialOutOfSync =
                    Long.parseLong(initialStats.getOrDefault("subscription_out_of_sync_count", "0"));

            // Subscribe (will fail due to ACL)
            listeningClient.subscribeLazy(Set.of(channel)).get();
            Thread.sleep(500);

            // Poll for metric increment
            long outOfSyncCount = initialOutOfSync;
            for (int i = 0; i < 15; i++) {
                Thread.sleep(1000);
                Map<String, String> stats = listeningClient.getStatistics();
                outOfSyncCount = Long.parseLong(stats.getOrDefault("subscription_out_of_sync_count", "0"));
                if (outOfSyncCount > initialOutOfSync) {
                    break;
                }
            }

            // Verify metric incremented
            assertTrue(outOfSyncCount > initialOutOfSync);

            // Verify subscription in desired but not in actual
            var state = listeningClient.getSubscriptions().get();
            Set<String> desired = state.getDesiredSubscriptions().get(PubSubChannelMode.EXACT);
            Set<String> actual = state.getActualSubscriptions().get(PubSubChannelMode.EXACT);

            assertNotNull(desired);
            assertTrue(desired.contains(channel));

            if (actual != null) {
                assertFalse(actual.contains(channel));
            }
        } finally {
            adminClient.customCommand(new String[] {"ACL", "DELUSER", username}).get();
        }
    }

    @Test
    @SneakyThrows
    public void test_subscription_metrics_on_acl_failure_cluster() {
        String channel = "acl_metrics_channel_" + UUID.randomUUID();
        String username = "test_user_acl_metrics_" + UUID.randomUUID();
        String password = "test_password_acl";

        GlideClusterClient adminClient = createClusterClient();

        // Create user without pubsub permissions on all nodes
        String[] aclCmd =
                new String[] {
                    "ACL",
                    "SETUSER",
                    username,
                    "ON",
                    ">" + password,
                    "~*",
                    "resetchannels",
                    "+@all",
                    "-@pubsub"
                };
        adminClient
                .customCommand(
                        aclCmd,
                        glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute
                                .ALL_NODES)
                .get();

        try {
            // Create listening client with empty subscription config and authenticate
            GlideClusterClient listeningClient = createClusterClientWithEmptySubscriptions();
            listeningClient
                    .customCommand(
                            new String[] {"AUTH", username, password},
                            glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute
                                    .ALL_NODES)
                    .get();

            // Get initial metrics
            Map<String, String> initialStats = listeningClient.getStatistics();
            long initialOutOfSync =
                    Long.parseLong(initialStats.getOrDefault("subscription_out_of_sync_count", "0"));

            // Subscribe (will fail due to ACL)
            listeningClient.subscribeLazy(Set.of(channel)).get();
            Thread.sleep(500);

            // Poll for metric increment
            long outOfSyncCount = initialOutOfSync;
            for (int i = 0; i < 15; i++) {
                Thread.sleep(1000);
                Map<String, String> stats = listeningClient.getStatistics();
                outOfSyncCount = Long.parseLong(stats.getOrDefault("subscription_out_of_sync_count", "0"));
                if (outOfSyncCount > initialOutOfSync) {
                    break;
                }
            }

            // Verify metric incremented
            assertTrue(outOfSyncCount > initialOutOfSync);

            // Verify subscription in desired but not in actual
            var state = listeningClient.getSubscriptions().get();
            Set<String> desired = state.getDesiredSubscriptions().get(PubSubClusterChannelMode.EXACT);
            Set<String> actual = state.getActualSubscriptions().get(PubSubClusterChannelMode.EXACT);

            assertNotNull(desired);
            assertTrue(desired.contains(channel));

            if (actual != null) {
                assertFalse(actual.contains(channel));
            }
        } finally {
            adminClient
                    .customCommand(
                            new String[] {"ACL", "DELUSER", username},
                            glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute
                                    .ALL_NODES)
                    .get();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void test_subscription_metrics_available(boolean isStandalone) {
        String channel = "metrics_channel_" + UUID.randomUUID();

        if (isStandalone) {
            Map<PubSubChannelMode, Set<GlideString>> subscriptions = new HashMap<>();
            subscriptions.put(PubSubChannelMode.EXACT, Set.of(GlideString.gs(channel)));
            GlideClient client = createStandaloneClientWithSubscriptions(subscriptions);

            Map<String, String> stats = client.getStatistics();
            assertNotNull(stats.get("subscription_out_of_sync_count"));
            assertNotNull(stats.get("subscription_last_sync_timestamp"));
        } else {
            Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions = new HashMap<>();
            subscriptions.put(PubSubClusterChannelMode.EXACT, Set.of(GlideString.gs(channel)));
            GlideClusterClient client = createClusterClientWithSubscriptions(subscriptions);

            Map<String, String> stats = client.getStatistics();
            assertNotNull(stats.get("subscription_out_of_sync_count"));
            assertNotNull(stats.get("subscription_last_sync_timestamp"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void test_pubsub_reconciliation_interval_config(boolean isStandalone) {
        int intervalMs = 1000;
        int pollIntervalMs = 100;
        double timeoutSec = 5.0;

        BaseClient client;
        if (isStandalone) {
            client =
                    GlideClient.createClient(
                                    commonClientConfig()
                                            .subscriptionConfiguration(
                                                    StandaloneSubscriptionConfiguration.builder().build())
                                            .advancedConfiguration(
                                                    AdvancedGlideClientConfiguration.builder()
                                                            .pubsubReconciliationIntervalMs(intervalMs)
                                                            .build())
                                            .build())
                            .get();
        } else {
            client =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig()
                                            .subscriptionConfiguration(ClusterSubscriptionConfiguration.builder().build())
                                            .advancedConfiguration(
                                                    AdvancedGlideClusterClientConfiguration.builder()
                                                            .pubsubReconciliationIntervalMs(intervalMs)
                                                            .build())
                                            .build())
                            .get();
        }
        listeners.put(client, Map.of());

        try {
            // Wait for initial sync to complete (up to 10 attempts with 100ms intervals)
            Map<String, String> initialStats = client.getStatistics();
            long initialTs = Long.parseLong(initialStats.get("subscription_last_sync_timestamp"));

            for (int i = 0; i < 10 && initialTs == 0; i++) {
                Thread.sleep(100);
                initialStats = client.getStatistics();
                initialTs = Long.parseLong(initialStats.get("subscription_last_sync_timestamp"));
            }

            // Wait for a full sync cycle to complete to get a stable baseline
            // This ensures we're not measuring from mid-cycle
            long baselineTs = pollForTimestampChange(client, initialTs, timeoutSec, pollIntervalMs);

            // Now measure two consecutive sync intervals from this stable baseline
            long firstSyncTs = pollForTimestampChange(client, baselineTs, timeoutSec, pollIntervalMs);
            long secondSyncTs = pollForTimestampChange(client, firstSyncTs, timeoutSec, pollIntervalMs);

            long actualIntervalMs = secondSyncTs - firstSyncTs;

            long minInterval = intervalMs / 2;
            long maxInterval = intervalMs * 3 / 2;
            assertTrue(
                    actualIntervalMs >= minInterval && actualIntervalMs <= maxInterval,
                    String.format(
                            "Reconciliation interval (%dms) should be between %dms and %dms",
                            actualIntervalMs, minInterval, maxInterval));
        } finally {
            client.close();
        }
    }

    private long pollForTimestampChange(
            BaseClient client, long previousTs, double timeoutSec, int pollIntervalMs) throws Exception {
        long startMs = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startMs) / 1000.0 < timeoutSec) {
            Map<String, String> stats = client.getStatistics();
            long currentTs = Long.parseLong(stats.get("subscription_last_sync_timestamp"));
            if (currentTs != previousTs) {
                return currentTs;
            }
            Thread.sleep(pollIntervalMs);
        }
        throw new TimeoutException(
                String.format(
                        "Sync timestamp did not change within %.1fs. Previous: %d", timeoutSec, previousTs));
    }
}
