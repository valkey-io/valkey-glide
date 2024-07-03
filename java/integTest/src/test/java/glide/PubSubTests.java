/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.BaseClient;
import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.ClusterTransaction;
import glide.api.models.GlideString;
import glide.api.models.PubSubMessage;
import glide.api.models.Transaction;
import glide.api.models.configuration.BaseSubscriptionConfiguration.ChannelMode;
import glide.api.models.configuration.BaseSubscriptionConfiguration.MessageCallback;
import glide.api.models.configuration.ClusterSubscriptionConfiguration;
import glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30) // sec
public class PubSubTests {

    // TODO protocol version
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private <M extends ChannelMode> BaseClient createClientWithSubscriptions(
            boolean standalone,
            Map<M, Set<GlideString>> subscriptions,
            Optional<MessageCallback> callback,
            Optional<Object> context) {
        if (standalone) {
            var subConfigBuilder =
                    StandaloneSubscriptionConfiguration.builder()
                            .subscriptions((Map<PubSubChannelMode, Set<GlideString>>) subscriptions);

            if (callback.isPresent()) {
                subConfigBuilder.callback(callback.get(), context.get());
            }
            return RedisClient.CreateClient(
                            commonClientConfig().subscriptionConfiguration(subConfigBuilder.build()).build())
                    .get();
        } else {
            var subConfigBuilder =
                    ClusterSubscriptionConfiguration.builder()
                            .subscriptions((Map<PubSubClusterChannelMode, Set<GlideString>>) subscriptions);

            if (callback.isPresent()) {
                subConfigBuilder.callback(callback.get(), context.get());
            }

            return RedisClusterClient.CreateClient(
                            commonClusterClientConfig()
                                    .subscriptionConfiguration(subConfigBuilder.build())
                                    .build())
                    .get();
        }
    }

    private <M extends ChannelMode> BaseClient createClientWithSubscriptions(
            boolean standalone, Map<M, Set<GlideString>> subscriptions) {
        return createClientWithSubscriptions(
                standalone, subscriptions, Optional.empty(), Optional.empty());
    }

    @SneakyThrows
    private BaseClient createClient(boolean standalone) {
        if (standalone) {
            return RedisClient.CreateClient(commonClientConfig().build()).get();
        }
        return RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get();
    }

    /**
     * pubsubMessage queue used in callback to analyze received pubsubMessages. Number is a client ID.
     */
    private final ConcurrentLinkedDeque<Pair<Integer, PubSubMessage>> pubsubMessageQueue =
            new ConcurrentLinkedDeque<>();

    /** Clients used in a test. */
    private final List<BaseClient> clients = new ArrayList<>();

    private static final int MESSAGE_DELIVERY_DELAY = 500; // ms

    @BeforeEach
    @SneakyThrows
    public void cleanup() {
        for (var client : clients) {
            if (client instanceof RedisClusterClient) {
                ((RedisClusterClient) client).customCommand(new String[] {"unsubscribe"}, ALL_NODES).get();
                ((RedisClusterClient) client).customCommand(new String[] {"punsubscribe"}, ALL_NODES).get();
                ((RedisClusterClient) client).customCommand(new String[] {"sunsubscribe"}, ALL_NODES).get();
            } else {
                ((RedisClient) client).customCommand(new String[] {"unsubscribe"}).get();
                ((RedisClient) client).customCommand(new String[] {"punsubscribe"}).get();
            }
            client.close();
        }
        clients.clear();
        pubsubMessageQueue.clear();
    }

    private void verifyReceivedPubsubMessages(
            Set<Pair<Integer, PubSubMessage>> pubsubMessages, BaseClient listener, boolean callback) {
        if (callback) {
            assertEquals(pubsubMessages, new HashSet<>(pubsubMessageQueue));
        } else {
            var received = new HashSet<PubSubMessage>(pubsubMessages.size());
            PubSubMessage pubsubMessage;
            while ((pubsubMessage = listener.tryGetPubSubMessage()) != null) {
                received.add(pubsubMessage);
            }
            assertEquals(
                    pubsubMessages.stream().map(Pair::getValue).collect(Collectors.toSet()), received);
        }
    }

    private static Stream<Arguments> getTwoBoolPermutations() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false));
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
            boolean useCallback,
            int clientId,
            Map<? extends ChannelMode, Set<GlideString>> subscriptions) {
        MessageCallback callback =
                (msg, ctx) ->
                        ((ConcurrentLinkedDeque<Pair<Integer, PubSubMessage>>) ctx)
                                .push(Pair.of(clientId, msg));
        return useCallback
                ? createClientWithSubscriptions(
                        standalone, subscriptions, Optional.of(callback), Optional.of(pubsubMessageQueue))
                : createClientWithSubscriptions(standalone, subscriptions);
    }

    // TODO add following tests from https://github.com/aws/glide-for-redis/pull/1643
    //  test_pubsub_exact_happy_path_coexistence
    //  test_pubsub_exact_happy_path_many_channels_co_existence
    //  test_sharded_pubsub_co_existence
    //  test_pubsub_pattern_co_existence
    // TODO tests below blocked by https://github.com/aws/glide-for-redis/issues/1649
    //  test_pubsub_exact_max_size_PubsubMessage
    //  test_pubsub_sharded_max_size_PubsubMessage
    //  test_pubsub_exact_max_size_PubsubMessage_callback
    //  test_pubsub_sharded_max_size_PubsubMessage_callback

    // TODO why `publish` returns 0 on cluster or > 1 on standalone when there is only 1 receiver???
    //  meanwhile, all pubsubMessages are delivered.
    //  debug this and add checks for `publish` return value

    // TODO: remove once fixed
    private void skipTestsOnMac() {
        assumeFalse(
                System.getProperty("os.name").toLowerCase().contains("mac"),
                "PubSub doesn't work on mac OS");
    }

    /** Similar to `test_pubsub_exact_happy_path` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, use callback = {1}")
    @MethodSource("getTwoBoolPermutations")
    public void exact_happy_path(boolean standalone, boolean useCallback) {
        skipTestsOnMac();
        GlideString channel = gs(UUID.randomUUID().toString());
        GlideString message = gs(UUID.randomUUID().toString());
        var subscriptions = Map.of(exact(standalone), Set.of(channel));

        var listener = createListener(standalone, useCallback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        sender.publish(message, channel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the message

        verifyReceivedPubsubMessages(
                Set.of(Pair.of(1, new PubSubMessage(message, channel))), listener, useCallback);
    }

    /** Similar to `test_pubsub_exact_happy_path_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, use callback = {1}")
    @MethodSource("getTwoBoolPermutations")
    public void exact_happy_path_many_channels(boolean standalone, boolean useCallback) {
        skipTestsOnMac();
        int numChannels = 256;
        int messagesPerChannel = 256;
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

        var listener = createListener(standalone, useCallback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()),
                listener,
                useCallback);
    }

    /** Similar to `test_sharded_pubsub` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "use callback = {0}")
    @ValueSource(booleans = {true, false})
    public void sharded_pubsub(boolean useCallback) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        skipTestsOnMac();

        GlideString channel = gs(UUID.randomUUID().toString());
        GlideString pubsubMessage = gs(UUID.randomUUID().toString());
        var subscriptions = Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));

        var listener = createListener(false, useCallback, 1, subscriptions);
        var sender = (RedisClusterClient) createClient(false);
        clients.addAll(List.of(listener, sender));

        sender.publish(pubsubMessage, channel, true).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the message

        verifyReceivedPubsubMessages(
                Set.of(Pair.of(1, new PubSubMessage(pubsubMessage, channel))), listener, useCallback);
    }

    /** Similar to `test_sharded_pubsub_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "use callback = {0}")
    @ValueSource(booleans = {true, false})
    public void sharded_pubsub_many_channels(boolean useCallback) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        skipTestsOnMac();

        int numChannels = 256;
        int pubsubMessagesPerChannel = 256;
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

        var listener = createListener(false, useCallback, 1, subscriptions);
        var sender = (RedisClusterClient) createClient(false);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : pubsubMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }
        sender.publish(UUID.randomUUID().toString(), UUID.randomUUID().toString(), true).get();

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                pubsubMessages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()),
                listener,
                useCallback);
    }

    /** Similar to `test_pubsub_pattern` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, use callback = {1}")
    @MethodSource("getTwoBoolPermutations")
    public void pattern(boolean standalone, boolean useCallback) {
        skipTestsOnMac();
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

        var listener = createListener(standalone, useCallback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

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

        verifyReceivedPubsubMessages(expected, listener, useCallback);
    }

    /** Similar to `test_pubsub_pattern_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, use callback = {1}")
    @MethodSource("getTwoBoolPermutations")
    public void pattern_many_channels(boolean standalone, boolean useCallback) {
        skipTestsOnMac();
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 256;
        int messagesPerChannel = 256;
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

        var listener = createListener(standalone, useCallback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // need some time to propagate subscriptions - why?

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        sender.publish(UUID.randomUUID().toString(), "channel").get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()),
                listener,
                useCallback);
    }

    /** Similar to `test_pubsub_combined_exact_and_pattern_one_client` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, use callback = {1}")
    @MethodSource("getTwoBoolPermutations")
    public void combined_exact_and_pattern_one_client(boolean standalone, boolean useCallback) {
        skipTestsOnMac();
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 256;
        int messagesPerChannel = 256;
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

        var listener = createListener(standalone, useCallback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()),
                listener,
                useCallback);
    }

    /**
     * Similar to `test_pubsub_combined_exact_and_pattern_multiple_clients` in python client tests.
     */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, use callback = {1}")
    @MethodSource("getTwoBoolPermutations")
    public void combined_exact_and_pattern_multiple_clients(boolean standalone, boolean useCallback) {
        skipTestsOnMac();
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 256;
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

        var listenerExactSub = createListener(standalone, useCallback, 1, subscriptions);

        subscriptions = Map.of(pattern(standalone), Set.of(pattern));
        var listenerPatternSub = createListener(standalone, useCallback, 2, subscriptions);

        var sender = createClient(standalone);
        clients.addAll(List.of(listenerExactSub, listenerPatternSub, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (useCallback) {
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .map(m -> Pair.of(m.getPattern().isEmpty() ? 1 : 2, m))
                            .collect(Collectors.toSet()),
                    listenerExactSub,
                    useCallback);
        } else {
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .filter(m -> m.getPattern().isEmpty())
                            .map(m -> Pair.of(1, m))
                            .collect(Collectors.toSet()),
                    listenerExactSub,
                    useCallback);
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .filter(m -> m.getPattern().isPresent())
                            .map(m -> Pair.of(2, m))
                            .collect(Collectors.toSet()),
                    listenerPatternSub,
                    useCallback);
        }
    }

    /**
     * Similar to `test_pubsub_combined_exact_pattern_and_sharded_one_client` in python client tests.
     */
    @SneakyThrows
    @ParameterizedTest(name = "use callback = {0}")
    @ValueSource(booleans = {true, false})
    public void combined_exact_pattern_and_sharded_one_client(boolean useCallback) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        skipTestsOnMac();

        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        String shardPrefix = "{shard}";
        int numChannels = 256;
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

        var listener = createListener(false, useCallback, 1, subscriptions);
        var sender = (RedisClusterClient) createClient(false);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        messages.addAll(shardedMessages);
        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()),
                listener,
                useCallback);
    }

    /**
     * Similar to `test_pubsub_combined_exact_pattern_and_sharded_multi_client` in python client
     * tests.
     */
    @SneakyThrows
    @ParameterizedTest(name = "use callback = {0}")
    @ValueSource(booleans = {true, false})
    public void combined_exact_pattern_and_sharded_multi_client(boolean useCallback) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        skipTestsOnMac();

        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        String shardPrefix = "{shard}";
        int numChannels = 256;
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
                        false, useCallback, PubSubClusterChannelMode.EXACT.ordinal(), subscriptionsExact);
        var listenerPattern =
                createListener(
                        false, useCallback, PubSubClusterChannelMode.PATTERN.ordinal(), subscriptionsPattern);
        var listenerSharded =
                createListener(
                        false, useCallback, PubSubClusterChannelMode.SHARDED.ordinal(), subscriptionsSharded);

        var sender = (RedisClusterClient) createClient(false);
        clients.addAll(List.of(listenerExact, listenerPattern, listenerSharded, sender));

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

        if (useCallback) {
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

            verifyReceivedPubsubMessages(expected, listenerExact, useCallback);
        } else {
            verifyReceivedPubsubMessages(
                    exactMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerExact,
                    useCallback);
            verifyReceivedPubsubMessages(
                    patternMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerPattern,
                    useCallback);
            verifyReceivedPubsubMessages(
                    shardedMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerSharded,
                    useCallback);
        }
    }

    /**
     * Similar to `test_pubsub_three_publishing_clients_same_name_with_sharded` in python client
     * tests.
     */
    @SneakyThrows
    @ParameterizedTest(name = "use callback = {0}")
    @ValueSource(booleans = {true, false})
    public void three_publishing_clients_same_name_with_sharded(boolean useCallback) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        skipTestsOnMac();

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
                !useCallback
                        ? (RedisClusterClient) createClientWithSubscriptions(false, subscriptionsExact)
                        : (RedisClusterClient)
                                createListener(
                                        false, true, PubSubClusterChannelMode.EXACT.ordinal(), subscriptionsExact);

        var listenerPattern =
                !useCallback
                        ? (RedisClusterClient) createClientWithSubscriptions(false, subscriptionsPattern)
                        : createListener(
                                false, true, PubSubClusterChannelMode.PATTERN.ordinal(), subscriptionsPattern);

        var listenerSharded =
                !useCallback
                        ? (RedisClusterClient) createClientWithSubscriptions(false, subscriptionsSharded)
                        : createListener(
                                false, true, PubSubClusterChannelMode.SHARDED.ordinal(), subscriptionsSharded);

        clients.addAll(List.of(listenerExact, listenerPattern, listenerSharded));

        listenerPattern.publish(exactMessage.getMessage(), channel).get();
        listenerSharded.publish(patternMessage.getMessage(), channel).get();
        listenerExact.publish(shardedMessage.getMessage(), channel, true).get();

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (!useCallback) {
            verifyReceivedPubsubMessages(
                    Set.of(
                            Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), exactMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.EXACT.ordinal(),
                                    new PubSubMessage(patternMessage.getMessage(), channel))),
                    listenerExact,
                    false);
            verifyReceivedPubsubMessages(
                    Set.of(
                            Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), patternMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.PATTERN.ordinal(),
                                    new PubSubMessage(exactMessage.getMessage(), channel, channel))),
                    listenerPattern,
                    false);
            verifyReceivedPubsubMessages(
                    Set.of(Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), shardedMessage)),
                    listenerSharded,
                    false);
        } else {
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

            verifyReceivedPubsubMessages(expected, listenerExact, true);
        }
    }

    @SneakyThrows
    @Test
    public void error_cases() {
        skipTestsOnMac();
        // client isn't configured with subscriptions
        var client = createClient(true);
        assertThrows(ConfigurationError.class, client::tryGetPubSubMessage);
        client.close();

        // client configured with callback and doesn't return pubsubMessages via API
        MessageCallback callback = (msg, ctx) -> fail();
        client =
                createClientWithSubscriptions(
                        true, Map.of(), Optional.of(callback), Optional.of(pubsubMessageQueue));
        assertThrows(ConfigurationError.class, client::tryGetPubSubMessage);
        client.close();

        // using sharded channels from different slots in a transaction causes a cross slot error
        var clusterClient = (RedisClusterClient) createClient(false);
        var transaction =
                new ClusterTransaction()
                        .publish("one", "abc", true)
                        .publish("two", "mnk", true)
                        .publish("three", "xyz", true);
        var exception =
                assertThrows(ExecutionException.class, () -> clusterClient.exec(transaction).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().toLowerCase().contains("crossslot"));

        // TODO test when callback throws an exception - currently nothing happens now
        //  it should terminate the client
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, use callback = {1}")
    @MethodSource("getTwoBoolPermutations")
    public void transaction_with_all_types_of_PubsubMessages(
            boolean standalone, boolean useCallback) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        skipTestsOnMac();
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

        var listener = createListener(standalone, useCallback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        if (standalone) {
            var transaction =
                    new Transaction()
                            .publish(exactMessage.getMessage(), exactMessage.getChannel())
                            .publish(patternMessage.getMessage(), patternMessage.getChannel());
            ((RedisClient) sender).exec(transaction).get();
        } else {
            var transaction =
                    new ClusterTransaction()
                            .publish(shardedMessage.getMessage(), shardedMessage.getChannel(), true)
                            .publish(exactMessage.getMessage(), exactMessage.getChannel())
                            .publish(patternMessage.getMessage(), patternMessage.getChannel());
            ((RedisClusterClient) sender).exec(transaction).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        var expected =
                standalone
                        ? Set.of(Pair.of(1, exactMessage), Pair.of(1, patternMessage))
                        : Set.of(
                                Pair.of(1, exactMessage), Pair.of(1, patternMessage), Pair.of(1, shardedMessage));
        verifyReceivedPubsubMessages(expected, listener, useCallback);
    }
}
