/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.BatchTestUtilities.createMap;
import static glide.Java8Compat.repeat;
import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.utils.Java8Utils.createSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import glide.api.models.configuration.BaseSubscriptionConfiguration.ChannelMode;
import glide.api.models.configuration.BaseSubscriptionConfiguration.MessageCallback;
import glide.api.models.configuration.ClusterSubscriptionConfiguration;
import glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotType;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    // TODO protocol version
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private <M extends ChannelMode> BaseClient createClientWithSubscriptions(
            boolean standalone,
            Map<M, Set<GlideString>> subscriptions,
            Optional<MessageCallback> callback,
            Optional<Object> context) {
        if (standalone) {
            StandaloneSubscriptionConfiguration.StandaloneSubscriptionConfigurationBuilder
                    subConfigBuilder =
                            StandaloneSubscriptionConfiguration.builder()
                                    .subscriptions((Map<PubSubChannelMode, Set<GlideString>>) subscriptions);

            if (callback.isPresent()) {
                subConfigBuilder.callback(callback.get(), context.get());
            }
            GlideClient client =
                    GlideClient.createClient(
                                    commonClientConfig()
                                            .requestTimeout(5000)
                                            .subscriptionConfiguration(subConfigBuilder.build())
                                            .build())
                            .get();
            listeners.put(client, subscriptions);
            return client;
        } else {
            ClusterSubscriptionConfiguration.ClusterSubscriptionConfigurationBuilder subConfigBuilder =
                    ClusterSubscriptionConfiguration.builder()
                            .subscriptions((Map<PubSubClusterChannelMode, Set<GlideString>>) subscriptions);

            if (callback.isPresent()) {
                subConfigBuilder.callback(callback.get(), context.get());
            }

            GlideClusterClient client =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig()
                                            .requestTimeout(5000)
                                            .subscriptionConfiguration(subConfigBuilder.build())
                                            .build())
                            .get();
            listeners.put(client, subscriptions);
            return client;
        }
    }

    private <M extends ChannelMode> BaseClient createClientWithSubscriptions(
            boolean standalone, Map<M, Set<GlideString>> subscriptions) {
        BaseClient client =
                createClientWithSubscriptions(
                        standalone, subscriptions, Optional.empty(), Optional.empty());
        listeners.put(client, subscriptions);
        return client;
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
        for (Map.Entry<BaseClient, Map<? extends ChannelMode, Set<GlideString>>> pair :
                listeners.entrySet()) {
            BaseClient client = pair.getKey();
            Map<? extends ChannelMode, Set<GlideString>> subscriptionTypes = pair.getValue();
            if (client instanceof GlideClusterClient) {
                for (Map.Entry<? extends ChannelMode, Set<GlideString>> subscription :
                        subscriptionTypes.entrySet()) {
                    GlideString[] channels = subscription.getValue().toArray(new GlideString[0]);
                    for (GlideString channel : channels) {
                        switch ((PubSubClusterChannelMode) subscription.getKey()) {
                            case EXACT:
                                ((GlideClusterClient) client)
                                        .customCommand(new GlideString[] {gs("unsubscribe"), channel})
                                        .get();
                                break;
                            case PATTERN:
                                ((GlideClusterClient) client)
                                        .customCommand(new GlideString[] {gs("punsubscribe"), channel})
                                        .get();
                                break;
                            case SHARDED:
                                ((GlideClusterClient) client)
                                        .customCommand(new GlideString[] {gs("sunsubscribe"), channel})
                                        .get();
                                break;
                        }
                    }
                }
            } else {
                for (Map.Entry<? extends ChannelMode, Set<GlideString>> subscription :
                        subscriptionTypes.entrySet()) {
                    GlideString[] channels = subscription.getValue().toArray(new GlideString[0]);
                    switch ((PubSubChannelMode) subscription.getKey()) {
                        case EXACT:
                            ((GlideClient) client)
                                    .customCommand(ArrayUtils.addFirst(channels, gs("unsubscribe")))
                                    .get();
                            break;
                        case PATTERN:
                            ((GlideClient) client)
                                    .customCommand(ArrayUtils.addFirst(channels, gs("punsubscribe")))
                                    .get();
                            break;
                    }
                }
            }
        }
        listeners.clear();
        for (BaseClient client : senders) {
            client.close();
        }
        senders.clear();
        pubsubMessageQueue.clear();
    }

    private enum MessageReadMethod {
        Callback,
        Async,
        Sync
    }

    @SneakyThrows
    private void verifyReceivedPubsubMessages(
            Set<Pair<Integer, PubSubMessage>> pubsubMessages,
            BaseClient listener,
            MessageReadMethod method) {
        if (method == MessageReadMethod.Callback) {
            assertEquals(pubsubMessages, new HashSet<>(pubsubMessageQueue));
        } else if (method == MessageReadMethod.Async) {
            HashSet<PubSubMessage> received = new HashSet<PubSubMessage>(pubsubMessages.size());
            CompletableFuture<PubSubMessage> messagePromise;
            while ((messagePromise = listener.getPubSubMessage()).isDone()) {
                received.add(messagePromise.get());
            }
            assertEquals(
                    pubsubMessages.stream().map(Pair::getValue).collect(Collectors.toSet()), received);
        } else { // Sync
            HashSet<PubSubMessage> received = new HashSet<PubSubMessage>(pubsubMessages.size());
            PubSubMessage message;
            while ((message = listener.tryGetPubSubMessage()) != null) {
                received.add(message);
            }
            assertEquals(
                    pubsubMessages.stream().map(Pair::getValue).collect(Collectors.toSet()), received);
        }
    }

    /** Permute all combinations of `standalone` as bool vs {@link MessageReadMethod}. */
    private static Stream<Arguments> getTestScenarios() {
        return Stream.of(
                Arguments.of(true, MessageReadMethod.Callback),
                Arguments.of(true, MessageReadMethod.Sync),
                Arguments.of(true, MessageReadMethod.Async),
                Arguments.of(false, MessageReadMethod.Callback),
                Arguments.of(false, MessageReadMethod.Sync),
                Arguments.of(false, MessageReadMethod.Async));
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
            Map<? extends ChannelMode, Set<GlideString>> subscriptions) {
        MessageCallback callback =
                (msg, ctx) ->
                        ((ConcurrentLinkedDeque<Pair<Integer, PubSubMessage>>) ctx)
                                .push(Pair.of(clientId, msg));
        return withCallback
                ? createClientWithSubscriptions(
                        standalone, subscriptions, Optional.of(callback), Optional.of(pubsubMessageQueue))
                : createClientWithSubscriptions(standalone, subscriptions);
    }

    // TODO why `publish` returns 0 on cluster or > 1 on standalone when there is only 1 receiver???
    //  meanwhile, all pubsubMessages are delivered.
    //  debug this and add checks for `publish` return value

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void config_error_on_resp2(boolean standalone) {
        if (standalone) {
            GlideClientConfiguration config =
                    commonClientConfig()
                            .subscriptionConfiguration(StandaloneSubscriptionConfiguration.builder().build())
                            .protocol(ProtocolVersion.RESP2)
                            .build();
            ConfigurationError exception =
                    assertThrows(ConfigurationError.class, () -> GlideClient.createClient(config));
            assertTrue(exception.getMessage().contains("PubSub subscriptions require RESP3 protocol"));
        } else {
            GlideClusterClientConfiguration config =
                    commonClusterClientConfig()
                            .subscriptionConfiguration(ClusterSubscriptionConfiguration.builder().build())
                            .protocol(ProtocolVersion.RESP2)
                            .build();
            ConfigurationError exception =
                    assertThrows(ConfigurationError.class, () -> GlideClusterClient.createClient(config));
            assertTrue(exception.getMessage().contains("PubSub subscriptions require RESP3 protocol"));
        }
    }

    /** Similar to `test_pubsub_exact_happy_path` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void exact_happy_path(boolean standalone, MessageReadMethod method) {
        GlideString channel = gs(UUID.randomUUID().toString());
        GlideString message = gs(UUID.randomUUID().toString());
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(exact(standalone), Collections.singleton(channel));

        BaseClient listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        BaseClient sender = createClient(standalone);

        sender.publish(message, channel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the message

        verifyReceivedPubsubMessages(
                new HashSet<>(Arrays.asList(Pair.of(1, new PubSubMessage(message, channel)))),
                listener,
                method);
    }

    /** Similar to `test_pubsub_exact_happy_path_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void exact_happy_path_many_channels(boolean standalone, MessageReadMethod method) {
        int numChannels = 16;
        int messagesPerChannel = 16;
        ArrayList<PubSubMessage> messages =
                new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        ChannelMode mode = exact(standalone);
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(mode, new HashSet<>());

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (int j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel));
            }
        }

        BaseClient listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        BaseClient sender = createClient(standalone);

        for (PubSubMessage pubsubMessage : messages) {
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
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(PubSubClusterChannelMode.SHARDED, Collections.singleton(channel));

        BaseClient listener =
                createListener(false, method == MessageReadMethod.Callback, 1, subscriptions);
        GlideClusterClient sender = (GlideClusterClient) createClient(false);

        sender.publish(pubsubMessage, channel, true).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the message

        verifyReceivedPubsubMessages(
                new HashSet<>(Arrays.asList(Pair.of(1, new PubSubMessage(pubsubMessage, channel)))),
                listener,
                method);
    }

    /** Similar to `test_sharded_pubsub_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void sharded_pubsub_many_channels(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        int numChannels = 16;
        int pubsubMessagesPerChannel = 16;
        ArrayList<PubSubMessage> pubsubMessages =
                new ArrayList<PubSubMessage>(numChannels * pubsubMessagesPerChannel);
        PubSubClusterChannelMode mode = PubSubClusterChannelMode.SHARDED;
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(mode, new HashSet<>());

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (int j = 0; j < pubsubMessagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                pubsubMessages.add(new PubSubMessage(message, channel));
            }
        }

        BaseClient listener =
                createListener(false, method == MessageReadMethod.Callback, 1, subscriptions);
        GlideClusterClient sender = (GlideClusterClient) createClient(false);

        for (PubSubMessage pubsubMessage : pubsubMessages) {
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
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void pattern(boolean standalone, MessageReadMethod method) {
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        Map<GlideString, GlideString> message2channels =
                createMap(
                        gs(prefix + "1"),
                        gs(UUID.randomUUID().toString()),
                        gs(prefix + "2"),
                        gs(UUID.randomUUID().toString()));
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(
                        standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN,
                        Collections.singleton(pattern));

        BaseClient listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        BaseClient sender = createClient(standalone);

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // need some time to propagate subscriptions - why?

        for (Map.Entry<GlideString, GlideString> entry : message2channels.entrySet()) {
            sender.publish(entry.getValue(), entry.getKey()).get();
        }
        sender.publish(UUID.randomUUID().toString(), "channel").get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        Set<Pair<Integer, PubSubMessage>> expected =
                message2channels.entrySet().stream()
                        .map(e -> Pair.of(1, new PubSubMessage(e.getValue(), e.getKey(), pattern)))
                        .collect(Collectors.toSet());

        verifyReceivedPubsubMessages(expected, listener, method);
    }

    /** Similar to `test_pubsub_pattern_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void pattern_many_channels(boolean standalone, MessageReadMethod method) {
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 16;
        int messagesPerChannel = 16;
        ChannelMode mode = standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN;
        ArrayList<PubSubMessage> messages =
                new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(mode, Collections.singleton(pattern));

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(prefix + "-" + i + "-" + UUID.randomUUID());
            for (int j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel, pattern));
            }
        }

        BaseClient listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        BaseClient sender = createClient(standalone);

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // need some time to propagate subscriptions - why?

        for (PubSubMessage pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        sender.publish(UUID.randomUUID().toString(), "channel").get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /** Similar to `test_pubsub_combined_exact_and_pattern_one_client` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void combined_exact_and_pattern_one_client(boolean standalone, MessageReadMethod method) {
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 16;
        int messagesPerChannel = 16;
        ArrayList<PubSubMessage> messages =
                new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        ChannelMode mode = standalone ? PubSubChannelMode.EXACT : PubSubClusterChannelMode.EXACT;
        Map<ChannelMode, Set<GlideString>> subscriptions = new HashMap<>();
        subscriptions.put(mode, new HashSet<GlideString>());
        subscriptions.put(
                standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN,
                Collections.singleton(pattern));

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (int j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel));
            }
        }

        for (int j = 0; j < messagesPerChannel; j++) {
            GlideString pubsubMessage = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(pubsubMessage, channel, pattern));
        }

        BaseClient listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        BaseClient sender = createClient(standalone);

        for (PubSubMessage pubsubMessage : messages) {
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
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void combined_exact_and_pattern_multiple_clients(
            boolean standalone, MessageReadMethod method) {
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 16;
        ArrayList<PubSubMessage> messages = new ArrayList<PubSubMessage>(numChannels * 2);
        ChannelMode mode = exact(standalone);
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(mode, new HashSet<>());

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (int j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, pattern));
        }

        BaseClient listenerExactSub =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);

        subscriptions = Collections.singletonMap(pattern(standalone), Collections.singleton(pattern));
        BaseClient listenerPatternSub =
                createListener(standalone, method == MessageReadMethod.Callback, 2, subscriptions);

        BaseClient sender = createClient(standalone);

        for (PubSubMessage pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .map(m -> Pair.of(!m.getPattern().isPresent() ? 1 : 2, m))
                            .collect(Collectors.toSet()),
                    listenerExactSub,
                    method);
        } else {
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .filter(m -> !m.getPattern().isPresent())
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
        ArrayList<PubSubMessage> messages = new ArrayList<PubSubMessage>(numChannels * 2);
        ArrayList<PubSubMessage> shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions = new HashMap<>();
        subscriptions.put(PubSubClusterChannelMode.EXACT, new HashSet<GlideString>());
        subscriptions.put(PubSubClusterChannelMode.PATTERN, Collections.singleton(pattern));
        subscriptions.put(PubSubClusterChannelMode.SHARDED, new HashSet<GlideString>());

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.EXACT).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.SHARDED).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (int j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, pattern));
        }

        BaseClient listener =
                createListener(false, method == MessageReadMethod.Callback, 1, subscriptions);
        GlideClusterClient sender = (GlideClusterClient) createClient(false);

        for (PubSubMessage pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (PubSubMessage pubsubMessage : shardedMessages) {
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
        ArrayList<PubSubMessage> messages = new ArrayList<PubSubMessage>(numChannels * 2);
        ArrayList<PubSubMessage> shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions = new HashMap<>();
        subscriptions.put(PubSubClusterChannelMode.EXACT, new HashSet<GlideString>());
        subscriptions.put(PubSubClusterChannelMode.PATTERN, Collections.singleton(gs(pattern)));
        subscriptions.put(PubSubClusterChannelMode.SHARDED, new HashSet<GlideString>());

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.EXACT).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.SHARDED).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (int j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, gs(pattern)));
        }

        BaseClient listener = createListener(false, false, 1, subscriptions);
        GlideClusterClient sender = (GlideClusterClient) createClient(false);

        for (PubSubMessage pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (PubSubMessage pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        messages.addAll(shardedMessages);

        LinkedHashSet<PubSubMessage> received = new LinkedHashSet<PubSubMessage>(messages.size());
        Random rand = new Random();
        while (true) {
            if (rand.nextBoolean()) {
                CompletableFuture<PubSubMessage> messagePromise = listener.getPubSubMessage();
                if (messagePromise.isDone()) {
                    received.add(messagePromise.get());
                } else {
                    break; // all messages read
                }
            } else {
                PubSubMessage message = listener.tryGetPubSubMessage();
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

        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        String shardPrefix = "{shard}";
        int numChannels = 16;
        ArrayList<PubSubMessage> exactMessages = new ArrayList<PubSubMessage>(numChannels);
        ArrayList<PubSubMessage> patternMessages = new ArrayList<PubSubMessage>(numChannels);
        ArrayList<PubSubMessage> shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsExact =
                Collections.singletonMap(PubSubClusterChannelMode.EXACT, new HashSet<>());
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsPattern =
                Collections.singletonMap(PubSubClusterChannelMode.PATTERN, Collections.singleton(pattern));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsSharded =
                Collections.singletonMap(PubSubClusterChannelMode.SHARDED, new HashSet<>());

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptionsExact.get(PubSubClusterChannelMode.EXACT).add(channel);
            GlideString pubsubMessage = gs(i + "-" + UUID.randomUUID());
            exactMessages.add(new PubSubMessage(pubsubMessage, channel));
        }

        for (int i = 0; i < numChannels; i++) {
            GlideString channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptionsSharded.get(PubSubClusterChannelMode.SHARDED).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (int j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            patternMessages.add(new PubSubMessage(message, channel, pattern));
        }

        BaseClient listenerExact =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.EXACT.ordinal(),
                        subscriptionsExact);
        BaseClient listenerPattern =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.PATTERN.ordinal(),
                        subscriptionsPattern);
        BaseClient listenerSharded =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.SHARDED.ordinal(),
                        subscriptionsSharded);

        GlideClusterClient sender = (GlideClusterClient) createClient(false);

        for (PubSubMessage pubsubMessage : exactMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (PubSubMessage pubsubMessage : patternMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (PubSubMessage pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            Set<Pair<Integer, PubSubMessage>> expected = new HashSet<Pair<Integer, PubSubMessage>>();
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
        PubSubMessage exactMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        PubSubMessage patternMessage =
                new PubSubMessage(gs(UUID.randomUUID().toString()), channel, channel);
        PubSubMessage shardedMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsExact =
                Collections.singletonMap(PubSubClusterChannelMode.EXACT, Collections.singleton(channel));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsPattern =
                Collections.singletonMap(PubSubClusterChannelMode.PATTERN, Collections.singleton(channel));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsSharded =
                Collections.singletonMap(PubSubClusterChannelMode.SHARDED, Collections.singleton(channel));

        GlideClusterClient listenerExact =
                method == MessageReadMethod.Callback
                        ? (GlideClusterClient)
                                createListener(
                                        false, true, PubSubClusterChannelMode.EXACT.ordinal(), subscriptionsExact)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsExact);

        GlideClusterClient listenerPattern =
                method == MessageReadMethod.Callback
                        ? (GlideClusterClient)
                                createListener(
                                        false, true, PubSubClusterChannelMode.PATTERN.ordinal(), subscriptionsPattern)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsPattern);

        GlideClusterClient listenerSharded =
                method == MessageReadMethod.Callback
                        ? (GlideClusterClient)
                                createListener(
                                        false, true, PubSubClusterChannelMode.SHARDED.ordinal(), subscriptionsSharded)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsSharded);

        listenerPattern.publish(exactMessage.getMessage(), channel).get();
        listenerSharded.publish(patternMessage.getMessage(), channel).get();
        listenerExact.publish(shardedMessage.getMessage(), channel, true).get();

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            Set<Pair<Integer, PubSubMessage>> expected =
                    new HashSet<Pair<Integer, PubSubMessage>>(
                            Arrays.asList(
                                    Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), exactMessage),
                                    Pair.of(
                                            PubSubClusterChannelMode.EXACT.ordinal(),
                                            new PubSubMessage(patternMessage.getMessage(), channel)),
                                    Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), patternMessage),
                                    Pair.of(
                                            PubSubClusterChannelMode.PATTERN.ordinal(),
                                            new PubSubMessage(exactMessage.getMessage(), channel, channel)),
                                    Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), shardedMessage)));

            verifyReceivedPubsubMessages(expected, listenerExact, method);
        } else {
            verifyReceivedPubsubMessages(
                    new HashSet<Pair<Integer, PubSubMessage>>(
                            Arrays.asList(
                                    Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), exactMessage),
                                    Pair.of(
                                            PubSubClusterChannelMode.EXACT.ordinal(),
                                            new PubSubMessage(patternMessage.getMessage(), channel)))),
                    listenerExact,
                    method);
            verifyReceivedPubsubMessages(
                    new HashSet<Pair<Integer, PubSubMessage>>(
                            Arrays.asList(
                                    Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), patternMessage),
                                    Pair.of(
                                            PubSubClusterChannelMode.PATTERN.ordinal(),
                                            new PubSubMessage(exactMessage.getMessage(), channel, channel)))),
                    listenerPattern,
                    method);
            verifyReceivedPubsubMessages(
                    Collections.singleton(
                            Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), shardedMessage)),
                    listenerSharded,
                    method);
        }
    }

    @SneakyThrows
    @Test
    public void error_cases() {
        BaseClient client = createClient(true);

        // client configured with callback and doesn't return pubsubMessages via API
        MessageCallback callback = (msg, ctx) -> fail();
        client =
                createClientWithSubscriptions(
                        true, Collections.emptyMap(), Optional.of(callback), Optional.of(pubsubMessageQueue));
        assertThrows(ConfigurationError.class, client::tryGetPubSubMessage);
        client.close();

        // using sharded channels from different slots in a transaction causes a cross slot error
        GlideClusterClient clusterClient = (GlideClusterClient) createClient(false);
        ClusterBatch transaction =
                new ClusterBatch(true)
                        .publish("one", "abc", true)
                        .publish("two", "mnk", true)
                        .publish("three", "xyz", true);
        ExecutionException exception =
                assertThrows(ExecutionException.class, () -> clusterClient.exec(transaction, false).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().toLowerCase().contains("crossslot"));
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void transaction_with_all_types_of_messages(boolean standalone, MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        assumeTrue(
                standalone, // TODO activate tests after fix
                "Test doesn't work on cluster due to Cross Slot error, probably a bug in `redis-rs`");

        String prefix = "channel";
        GlideString pattern = gs(prefix + "*");
        GlideString shardPrefix = gs("{shard}");
        GlideString channel = gs(UUID.randomUUID().toString());
        PubSubMessage exactMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        PubSubMessage patternMessage =
                new PubSubMessage(gs(UUID.randomUUID().toString()), gs(prefix), pattern);
        PubSubMessage shardedMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), shardPrefix);

        Map<ChannelMode, Set<GlideString>> subscriptions;
        if (standalone) {
            subscriptions = new HashMap<>();
            subscriptions.put(PubSubChannelMode.EXACT, Collections.singleton(channel));
            subscriptions.put(PubSubChannelMode.PATTERN, Collections.singleton(pattern));
        } else {
            subscriptions = new HashMap<>();
            subscriptions.put(PubSubClusterChannelMode.EXACT, Collections.singleton(channel));
            subscriptions.put(PubSubClusterChannelMode.PATTERN, Collections.singleton(pattern));
            subscriptions.put(PubSubClusterChannelMode.SHARDED, Collections.singleton(shardPrefix));
        }

        BaseClient listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        BaseClient sender = createClient(standalone);

        if (standalone) {
            Batch transaction =
                    new Batch(true)
                            .publish(exactMessage.getMessage(), exactMessage.getChannel())
                            .publish(patternMessage.getMessage(), patternMessage.getChannel());
            ((GlideClient) sender).exec(transaction, false).get();
        } else {
            ClusterBatch transaction =
                    new ClusterBatch(true)
                            .publish(shardedMessage.getMessage(), shardedMessage.getChannel(), true)
                            .publish(exactMessage.getMessage(), exactMessage.getChannel())
                            .publish(patternMessage.getMessage(), patternMessage.getChannel());
            ((GlideClusterClient) sender).exec(transaction, false).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        Set<Pair<Integer, PubSubMessage>> expected =
                standalone
                        ? new HashSet<Pair<Integer, PubSubMessage>>(
                                Arrays.asList(Pair.of(1, exactMessage), Pair.of(1, patternMessage)))
                        : new HashSet<Pair<Integer, PubSubMessage>>(
                                Arrays.asList(
                                        Pair.of(1, exactMessage),
                                        Pair.of(1, patternMessage),
                                        Pair.of(1, shardedMessage)));
        verifyReceivedPubsubMessages(expected, listener, method);
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_exact_max_size_message(boolean standalone) {
        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs(repeat("1", 512 * 1024 * 1024)); // 512MB
        final GlideString message2 = gs(repeat("2", 1 << 25)); // 3MB

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Collections.singletonMap(PubSubChannelMode.EXACT, Collections.singleton(channel))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT, Collections.singleton(channel));
        BaseClient listener = createClientWithSubscriptions(standalone, subscriptions);
        BaseClient sender = createClient(standalone);

        assertEquals(OK, sender.publish(message, channel).get());
        assertEquals(OK, sender.publish(message2, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        PubSubMessage asyncMessage = listener.getPubSubMessage().get();
        assertEquals(message, asyncMessage.getMessage());
        assertEquals(channel, asyncMessage.getChannel());
        assertTrue(asyncMessage.getPattern().isPresent() == false);

        PubSubMessage syncMessage = listener.tryGetPubSubMessage();
        assertEquals(message2, syncMessage.getMessage());
        assertEquals(channel, syncMessage.getChannel());
        assertTrue(syncMessage.getPattern().isPresent() == false);

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
        final GlideString message = gs(repeat("1", 512 * 1024 * 1024)); // 512MB
        final GlideString message2 = gs(repeat("2", 1 << 25)); // 3MB

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(PubSubClusterChannelMode.SHARDED, Collections.singleton(channel));
        BaseClient listener = createClientWithSubscriptions(standalone, subscriptions);
        BaseClient sender = createClient(standalone);

        assertEquals(OK, sender.publish(message, channel).get());
        assertEquals(OK, ((GlideClusterClient) sender).publish(message2, channel, true).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        PubSubMessage asyncMessage = listener.getPubSubMessage().get();
        assertEquals(message, asyncMessage.getMessage());
        assertEquals(channel, asyncMessage.getChannel());
        assertTrue(asyncMessage.getPattern().isPresent() == false);

        PubSubMessage syncMessage = listener.tryGetPubSubMessage();
        assertEquals(message2, syncMessage.getMessage());
        assertEquals(channel, syncMessage.getChannel());
        assertTrue(syncMessage.getPattern().isPresent() == false);

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
        final GlideString message = gs(repeat("1", 512 * 1024 * 1024)); // 512MB

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Collections.singletonMap(PubSubChannelMode.EXACT, Collections.singleton(channel))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT, Collections.singleton(channel));

        BaseClient listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        BaseClient sender = createClient(standalone);

        assertEquals(OK, sender.publish(message, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        assertEquals(1, callbackMessages.size());
        assertEquals(message, callbackMessages.get(0).getMessage());
        assertEquals(channel, callbackMessages.get(0).getChannel());
        assertTrue(callbackMessages.get(0).getPattern().isPresent() == false);
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
        final GlideString message = gs(repeat("1", 512 * 1024 * 1024)); // 512MB

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(PubSubClusterChannelMode.SHARDED, Collections.singleton(channel));

        BaseClient listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        BaseClient sender = createClient(standalone);

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
                        ? Collections.singletonMap(PubSubChannelMode.EXACT, Collections.singleton(channel))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT, Collections.singleton(channel));

        BaseClient listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        BaseClient sender = createClient(standalone);

        assertEquals(OK, sender.publish(message1, channel).get());
        assertEquals(OK, sender.publish(message2, channel).get());
        assertEquals(OK, sender.publish(errorMsg, channel).get());
        assertEquals(OK, sender.publish(message3, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        assertEquals(3, callbackMessages.size());
        assertEquals(message1, callbackMessages.get(0).getMessage());
        assertEquals(channel, callbackMessages.get(0).getChannel());
        assertTrue(callbackMessages.get(0).getPattern().isPresent() == false);

        assertEquals(message2, callbackMessages.get(1).getMessage());
        assertEquals(channel, callbackMessages.get(1).getChannel());
        assertTrue(callbackMessages.get(1).getPattern().isPresent() == false);

        // Ensure we can receive message 3 which is after the message that triggers a throw.
        assertEquals(message3, callbackMessages.get(2).getMessage());
        assertEquals(channel, callbackMessages.get(2).getChannel());
        assertTrue(callbackMessages.get(2).getPattern().isPresent() == false);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_with_binary(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        GlideString channel = gs(new byte[] {(byte) 0xE2, 0x28, (byte) 0xA1});
        PubSubMessage message =
                new PubSubMessage(gs(new byte[] {(byte) 0xF0, 0x28, (byte) 0x8C, (byte) 0xBC}), channel);

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Collections.singletonMap(PubSubChannelMode.EXACT, Collections.singleton(channel))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT, Collections.singleton(channel));

        BaseClient listener = createClientWithSubscriptions(standalone, subscriptions);
        BaseClient listener2 =
                createClientWithSubscriptions(
                        standalone, subscriptions, Optional.of(callback), Optional.of(callbackMessages));
        BaseClient sender = createClient(standalone);

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

        // no channels exists yet
        BaseClient client = createClient(standalone);
        assertEquals(0, client.pubsubChannels().get().length);
        assertEquals(0, client.pubsubChannelsBinary().get().length);
        assertEquals(0, client.pubsubChannels("**").get().length);
        assertEquals(0, client.pubsubChannels(gs("**")).get().length);

        HashSet<String> channels =
                new HashSet<String>(Arrays.asList("test_channel1", "test_channel2", "some_channel"));
        String pattern = "test_*";

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Collections.singletonMap(
                                PubSubChannelMode.EXACT,
                                channels.stream().map(GlideString::gs).collect(Collectors.toSet()))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT,
                                channels.stream().map(GlideString::gs).collect(Collectors.toSet()));

        BaseClient listener = createClientWithSubscriptions(standalone, subscriptions);

        // test without pattern
        assertEquals(channels, new HashSet<>(Arrays.asList(client.pubsubChannels().get())));
        assertEquals(channels, new HashSet<>(Arrays.asList(listener.pubsubChannels().get())));
        assertEquals(
                channels.stream().map(GlideString::gs).collect(Collectors.toSet()),
                new HashSet<>(Arrays.asList(client.pubsubChannelsBinary().get())));
        assertEquals(
                channels.stream().map(GlideString::gs).collect(Collectors.toSet()),
                new HashSet<>(Arrays.asList(listener.pubsubChannelsBinary().get())));

        // test with pattern
        assertEquals(
                new HashSet<>(Arrays.asList("test_channel1", "test_channel2")),
                new HashSet<>(Arrays.asList(client.pubsubChannels(pattern).get())));
        assertEquals(
                new HashSet<>(Arrays.asList(gs("test_channel1"), gs("test_channel2"))),
                new HashSet<>(Arrays.asList(client.pubsubChannels(gs(pattern)).get())));
        assertEquals(
                new HashSet<>(Arrays.asList("test_channel1", "test_channel2")),
                new HashSet<>(Arrays.asList(listener.pubsubChannels(pattern).get())));
        assertEquals(
                new HashSet<>(Arrays.asList(gs("test_channel1"), gs("test_channel2"))),
                new HashSet<>(Arrays.asList(listener.pubsubChannels(gs(pattern)).get())));

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

        // no channels exists yet
        BaseClient client = createClient(standalone);
        assertEquals(0, client.pubsubNumPat().get());

        HashSet<String> patterns = new HashSet<String>(Arrays.asList("news.*", "announcements.*"));

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Collections.singletonMap(
                                PubSubChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()));

        BaseClient listener = createClientWithSubscriptions(standalone, subscriptions);

        assertEquals(2, client.pubsubNumPat().get());
        assertEquals(2, listener.pubsubNumPat().get());
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_numsub(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        // no channels exists yet
        BaseClient client = createClient(standalone);
        String[] channels = new String[] {"channel1", "channel2", "channel3"};
        assertEquals(
                Arrays.stream(channels).collect(Collectors.toMap(c -> c, c -> 0L)),
                client.pubsubNumSub(channels).get());

        Map<? extends ChannelMode, Set<GlideString>> subscriptions1 =
                standalone
                        ? Collections.singletonMap(
                                PubSubChannelMode.EXACT,
                                new HashSet<>(Arrays.asList(gs("channel1"), gs("channel2"), gs("channel3"))))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT,
                                new HashSet<>(Arrays.asList(gs("channel1"), gs("channel2"), gs("channel3"))));
        BaseClient listener1 = createClientWithSubscriptions(standalone, subscriptions1);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions2 =
                standalone
                        ? Collections.singletonMap(
                                PubSubChannelMode.EXACT,
                                new HashSet<>(Arrays.asList(gs("channel2"), gs("channel3"))))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT,
                                new HashSet<>(Arrays.asList(gs("channel2"), gs("channel3"))));
        BaseClient listener2 = createClientWithSubscriptions(standalone, subscriptions2);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions3 =
                standalone
                        ? Collections.singletonMap(
                                PubSubChannelMode.EXACT, Collections.singleton(gs("channel3")))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT, Collections.singleton(gs("channel3")));
        BaseClient listener3 = createClientWithSubscriptions(standalone, subscriptions3);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions4 =
                standalone
                        ? Collections.singletonMap(
                                PubSubChannelMode.PATTERN, Collections.singleton(gs("channel*")))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.PATTERN, Collections.singleton(gs("channel*")));
        BaseClient listener4 = createClientWithSubscriptions(standalone, subscriptions4);

        Map<String, Long> expected =
                createMap("channel1", 1L, "channel2", 2L, "channel3", 3L, "channel4", 0L);
        assertEquals(expected, client.pubsubNumSub(ArrayUtils.addFirst(channels, "channel4")).get());
        assertEquals(expected, listener1.pubsubNumSub(ArrayUtils.addFirst(channels, "channel4")).get());

        Map<GlideString, Object> expectedGs =
                createMap(gs("channel1"), 1L, gs("channel2"), 2L, gs("channel3"), 3L, gs("channel4"), 0L);
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

        String prefix = "{boo}-";
        SlotKeyRoute route = new SlotKeyRoute(prefix, SlotType.PRIMARY);
        BaseClient client = createClient(standalone);
        String[] channels =
                new String[] {prefix + "test_channel1", prefix + "test_channel2", prefix + "some_channel"};
        HashSet<String> patterns =
                new HashSet<String>(Arrays.asList(prefix + "news.*", prefix + "announcements.*"));
        String pattern = prefix + "test_*";

        Object transaction =
                (standalone ? new Batch(true) : new ClusterBatch(true))
                        .pubsubChannels()
                        .pubsubChannels(pattern)
                        .pubsubNumPat()
                        .pubsubNumSub(channels);
        ClusterBatchOptions options = ClusterBatchOptions.builder().route(route).build();

        // no channels exists yet
        Object[] result =
                standalone
                        ? ((GlideClient) client).exec((Batch) transaction, false).get()
                        : ((GlideClusterClient) client).exec((ClusterBatch) transaction, false, options).get();
        assertDeepEquals(
                new Object[] {
                    new String[0], // pubsubChannels()
                    new String[0], // pubsubChannels(pattern)
                    0L, // pubsubNumPat()
                    Arrays.stream(channels)
                            .collect(Collectors.toMap(c -> c, c -> 0L)), // pubsubNumSub(channels)
                },
                result);

        Map<ChannelMode, Set<GlideString>> subscriptions = new HashMap<>();
        if (standalone) {
            subscriptions.put(
                    PubSubChannelMode.EXACT,
                    Arrays.stream(channels).map(GlideString::gs).collect(Collectors.toSet()));
            subscriptions.put(
                    PubSubChannelMode.PATTERN,
                    patterns.stream().map(GlideString::gs).collect(Collectors.toSet()));
        } else {
            subscriptions.put(
                    PubSubClusterChannelMode.EXACT,
                    Arrays.stream(channels).map(GlideString::gs).collect(Collectors.toSet()));
            subscriptions.put(
                    PubSubClusterChannelMode.PATTERN,
                    patterns.stream().map(GlideString::gs).collect(Collectors.toSet()));
        }

        BaseClient listener = createClientWithSubscriptions(standalone, subscriptions);

        result =
                standalone
                        ? ((GlideClient) client).exec((Batch) transaction, false).get()
                        : ((GlideClusterClient) client).exec((ClusterBatch) transaction, false, options).get();

        // convert arrays to sets, because we can't compare arrays - they received reordered
        result[0] = new HashSet<>(Arrays.asList((Object[]) result[0]));
        result[1] = new HashSet<>(Arrays.asList((Object[]) result[1]));

        assertDeepEquals(
                new Object[] {
                    new HashSet<>(Arrays.asList(channels)), // pubsubChannels()
                    new HashSet<>(
                            Arrays.asList(
                                    "{boo}-test_channel1", "{boo}-test_channel2")), // pubsubChannels(pattern)
                    2L, // pubsubNumPat()
                    Arrays.stream(channels)
                            .collect(Collectors.toMap(c -> c, c -> 1L)), // pubsubNumSub(channels)
                },
                result);
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

        HashSet<String> channels =
                new HashSet<String>(
                        Arrays.asList("test_shardchannel1", "test_shardchannel2", "some_shardchannel3"));
        String pattern = "test_*";

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Collections.singletonMap(
                        PubSubClusterChannelMode.SHARDED,
                        channels.stream().map(GlideString::gs).collect(Collectors.toSet()));

        GlideClusterClient listener =
                (GlideClusterClient) createClientWithSubscriptions(false, subscriptions);

        // test without pattern
        assertEquals(channels, new HashSet<>(Arrays.asList(client.pubsubShardChannels().get())));
        assertEquals(channels, new HashSet<>(Arrays.asList(listener.pubsubShardChannels().get())));
        assertEquals(
                channels.stream().map(GlideString::gs).collect(Collectors.toSet()),
                new HashSet<>(Arrays.asList(client.pubsubShardChannelsBinary().get())));
        assertEquals(
                channels.stream().map(GlideString::gs).collect(Collectors.toSet()),
                new HashSet<>(Arrays.asList(listener.pubsubShardChannelsBinary().get())));

        // test with pattern
        assertEquals(
                new HashSet<>(Arrays.asList("test_shardchannel1", "test_shardchannel2")),
                new HashSet<>(Arrays.asList(client.pubsubShardChannels(pattern).get())));
        assertEquals(
                new HashSet<>(Arrays.asList(gs("test_shardchannel1"), gs("test_shardchannel2"))),
                new HashSet<>(Arrays.asList(client.pubsubShardChannels(gs(pattern)).get())));
        assertEquals(
                new HashSet<>(Arrays.asList("test_shardchannel1", "test_shardchannel2")),
                new HashSet<>(Arrays.asList(listener.pubsubShardChannels(pattern).get())));
        assertEquals(
                new HashSet<>(Arrays.asList(gs("test_shardchannel1"), gs("test_shardchannel2"))),
                new HashSet<>(Arrays.asList(listener.pubsubShardChannels(gs(pattern)).get())));

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
        String[] channels = new String[] {"channel1", "channel2", "channel3"};
        assertEquals(
                Arrays.stream(channels).collect(Collectors.toMap(c -> c, c -> 0L)),
                client.pubsubNumSub(channels).get());

        Map<? extends ChannelMode, Set<GlideString>> subscriptions1 =
                Collections.singletonMap(
                        PubSubClusterChannelMode.SHARDED,
                        new HashSet<>(Arrays.asList(gs("channel1"), gs("channel2"), gs("channel3"))));
        GlideClusterClient listener1 =
                (GlideClusterClient) createClientWithSubscriptions(false, subscriptions1);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions2 =
                Collections.singletonMap(
                        PubSubClusterChannelMode.SHARDED,
                        new HashSet<>(Arrays.asList(gs("channel2"), gs("channel3"))));
        GlideClusterClient listener2 =
                (GlideClusterClient) createClientWithSubscriptions(false, subscriptions2);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions3 =
                Collections.singletonMap(
                        PubSubClusterChannelMode.SHARDED, Collections.singleton(gs("channel3")));
        GlideClusterClient listener3 =
                (GlideClusterClient) createClientWithSubscriptions(false, subscriptions3);

        Map<String, Long> expected =
                createMap("channel1", 1L, "channel2", 2L, "channel3", 3L, "channel4", 0L);
        assertEquals(
                expected, client.pubsubShardNumSub(ArrayUtils.addFirst(channels, "channel4")).get());
        assertEquals(
                expected, listener1.pubsubShardNumSub(ArrayUtils.addFirst(channels, "channel4")).get());

        Map<GlideString, Object> expectedGs =
                createMap(gs("channel1"), 1L, gs("channel2"), 2L, gs("channel3"), 3L, gs("channel4"), 0L);
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
                        ? Collections.singletonMap(PubSubChannelMode.EXACT, Collections.singleton(channel))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT, Collections.singleton(channel));
        listeners.put(listener, subscriptions);

        // Create sender client
        BaseClient sender = createClient(standalone);

        // Publish messages
        sender.publish(message1, channel).get();
        sender.publish(message2, channel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        // Verify messages received via pull-based API
        verifyReceivedPubsubMessages(
                new HashSet<>(
                        Arrays.asList(
                                Pair.of(1, new PubSubMessage(message1, channel)),
                                Pair.of(1, new PubSubMessage(message2, channel)))),
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
                        ? Collections.singletonMap(PubSubChannelMode.EXACT, Collections.emptySet())
                        : Collections.singletonMap(PubSubClusterChannelMode.EXACT, Collections.emptySet());

        BaseClient listener = createListener(standalone, true, 1, subscriptions);

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
                        ? Collections.singletonMap(
                                PubSubChannelMode.EXACT, Collections.singleton(dynamicChannel))
                        : Collections.singletonMap(
                                PubSubClusterChannelMode.EXACT, Collections.singleton(dynamicChannel)));

        BaseClient sender = createClient(standalone);

        // Publish to both channels
        sender.publish(message, dynamicChannel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        // Verify both messages received via callback
        verifyReceivedPubsubMessages(
                new HashSet<>(Arrays.asList(Pair.of(1, new PubSubMessage(message, dynamicChannel)))),
                listener,
                MessageReadMethod.Callback);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void dynamic_subscribe_lazy(boolean standalone) {
        try (BaseClient listener = createClient(standalone);
                BaseClient sender = createClient(standalone)) {
            String channel = "test-channel-" + UUID.randomUUID();
            String message = "test-message";

            // Dynamic subscribe (lazy)
            Set<String> channels = createSet(channel);
            listener.subscribe(channels).get();
            Thread.sleep(MESSAGE_DELIVERY_DELAY);

            // Publish message
            sender.publish(message, channel).get();
            Thread.sleep(MESSAGE_DELIVERY_DELAY);

            // Receive message
            PubSubMessage msg = listener.getPubSubMessage().get(5, TimeUnit.SECONDS);
            assertEquals(message, msg.getMessage().getString());
            assertEquals(channel, msg.getChannel().getString());
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void dynamic_psubscribe_lazy(boolean standalone) {
        try (BaseClient listener = createClient(standalone);
                BaseClient sender = createClient(standalone)) {
            String pattern = "test-pattern-*";
            String channel = "test-pattern-" + UUID.randomUUID();
            String message = "test-message";

            // Dynamic psubscribe (lazy)
            Set<String> patterns = createSet(pattern);
            listener.psubscribe(patterns).get();
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
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void dynamic_unsubscribe(boolean standalone) {
        try (BaseClient listener = createClient(standalone);
                BaseClient sender = createClient(standalone)) {
            String channel = "test-channel-" + UUID.randomUUID();
            String message = "test-message";

            // Subscribe
            Set<String> channels = createSet(channel);
            listener.subscribe(channels).get();

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
        }
    }

    @Test
    @SneakyThrows
    public void dynamic_ssubscribe_lazy() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        try (GlideClusterClient listener = (GlideClusterClient) createClient(false);
                GlideClusterClient sender = (GlideClusterClient) createClient(false)) {
            String channel = "test-shard-channel-" + UUID.randomUUID();
            String message = "test-message";

            // Dynamic ssubscribe (lazy)
            Set<String> channels = createSet(channel);
            listener.ssubscribe(channels).get();
            Thread.sleep(MESSAGE_DELIVERY_DELAY);

            // Publish message
            sender.publish(message, channel, true).get();
            Thread.sleep(MESSAGE_DELIVERY_DELAY);

            // Receive message
            PubSubMessage msg = listener.getPubSubMessage().get(5, TimeUnit.SECONDS);
            assertEquals(message, msg.getMessage().getString());
            assertEquals(channel, msg.getChannel().getString());
        }
    }

    @Test
    @SneakyThrows
    public void dynamic_sunsubscribe() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        try (GlideClusterClient listener = (GlideClusterClient) createClient(false);
                GlideClusterClient sender = (GlideClusterClient) createClient(false)) {
            String channel = "test-shard-channel-" + UUID.randomUUID();
            String message = "test-message";

            // Subscribe
            Set<String> channels = createSet(channel);
            listener.ssubscribe(channels).get();
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
            StandaloneSubscriptionConfiguration subConfig =
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
            ClusterSubscriptionConfiguration subConfig =
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
        Set<String> channels = createSet(channel);
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
            // Get initial statistics
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
            Set<String> channels = createSet(channel);
            client.subscribe(channels).get();

            // Wait for reconciliation
            Thread.sleep(MESSAGE_DELIVERY_DELAY);

            // Get updated timestamp
            Map<String, String> updatedStats = client.getStatistics();
            long updatedTimestamp = Long.parseLong(updatedStats.get("subscription_last_sync_timestamp"));

            // Timestamp should have been updated (or at least not decreased)
            assertTrue(updatedTimestamp >= initialTimestamp);
        }
    }
}
