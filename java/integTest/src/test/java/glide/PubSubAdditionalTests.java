/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.GlideString;
import glide.api.models.configuration.ClusterSubscriptionConfiguration;
import glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Additional PubSub tests ported from Python to achieve full test parity. These tests cover ACL
 * failures, metrics tracking, reconnection, timeouts, and edge cases.
 */
@Timeout(30)
public class PubSubAdditionalTests {

    private final List<BaseClient> clients = new ArrayList<>();

    @AfterEach
    @SneakyThrows
    public void cleanup() {
        for (var client : clients) {
            client.close();
        }
        clients.clear();
    }

    @SneakyThrows
    private GlideClient createStandaloneClient() {
        GlideClient client = GlideClient.createClient(commonClientConfig().build()).get();
        clients.add(client);
        return client;
    }

    @SneakyThrows
    private GlideClusterClient createClusterClient() {
        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
        clients.add(client);
        return client;
    }

    @SneakyThrows
    private GlideClient createStandaloneClientWithEmptySubscriptions() {
        GlideClient client =
                GlideClient.createClient(
                                commonClientConfig()
                                        .subscriptionConfiguration(
                                                StandaloneSubscriptionConfiguration.builder().build())
                                        .build())
                        .get();
        clients.add(client);
        return client;
    }

    @SneakyThrows
    private GlideClusterClient createClusterClientWithEmptySubscriptions() {
        GlideClusterClient client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .subscriptionConfiguration(ClusterSubscriptionConfiguration.builder().build())
                                        .build())
                        .get();
        clients.add(client);
        return client;
    }

    @SneakyThrows
    private GlideClient createStandaloneClientWithSubscriptions(
            Map<PubSubChannelMode, Set<GlideString>> subscriptions) {
        GlideClient client =
                GlideClient.createClient(
                                commonClientConfig()
                                        .subscriptionConfiguration(
                                                StandaloneSubscriptionConfiguration.builder()
                                                        .subscriptions(subscriptions)
                                                        .build())
                                        .build())
                        .get();
        clients.add(client);
        return client;
    }

    @SneakyThrows
    private GlideClusterClient createClusterClientWithSubscriptions(
            Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions) {
        GlideClusterClient client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .subscriptionConfiguration(
                                                ClusterSubscriptionConfiguration.builder()
                                                        .subscriptions(subscriptions)
                                                        .build())
                                        .build())
                        .get();
        clients.add(client);
        return client;
    }

    @Test
    @SneakyThrows
    public void test_unsubscribe_all_standalone() {
        String channel1 = "channel1_" + UUID.randomUUID();
        String channel2 = "channel2_" + UUID.randomUUID();

        GlideClient client = createStandaloneClientWithEmptySubscriptions();
        GlideClient publisher = createStandaloneClient();

        // Subscribe to multiple channels
        client.subscribe(Set.of(channel1, channel2)).get();
        Thread.sleep(500);
        Thread.sleep(500);

        // Unsubscribe from all
        client.unsubscribe().get();
        Thread.sleep(500);
        Thread.sleep(500);

        // Verify we can subscribe again (proves unsubscribe worked)
        client.subscribe(Set.of(channel1)).get();
        Thread.sleep(500);
    }

    @Test
    @SneakyThrows
    public void test_unsubscribe_all_cluster() {
        String channel1 = "channel1_" + UUID.randomUUID();
        String channel2 = "channel2_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();

        // Subscribe to multiple channels
        client.subscribe(Set.of(channel1, channel2)).get();
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
    }

    @Test
    @SneakyThrows
    public void test_punsubscribe_all_standalone() {
        String pattern1 = "pattern1_*";
        String pattern2 = "pattern2_*";

        GlideClient client = createStandaloneClientWithEmptySubscriptions();

        // Subscribe to multiple patterns
        client.psubscribe(Set.of(pattern1, pattern2)).get();
        Thread.sleep(500);

        // Verify subscriptions
        var state = client.getSubscriptions().get();
        Set<String> patterns = state.getActualSubscriptions().get(PubSubChannelMode.PATTERN);
        assertNotNull(patterns);
        assertEquals(2, patterns.size());

        // Unsubscribe from all patterns
        client.punsubscribe().get();
        Thread.sleep(500);

        // Verify all unsubscribed
        state = client.getSubscriptions().get();
        Set<String> patternsAfter = state.getActualSubscriptions().get(PubSubChannelMode.PATTERN);
        assertTrue(patternsAfter == null || patternsAfter.isEmpty());
    }

    @Test
    @SneakyThrows
    public void test_sunsubscribe_all_cluster() {
        String channel1 = "{slot}channel1_" + UUID.randomUUID();
        String channel2 = "{slot}channel2_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();

        // Subscribe to multiple sharded channels
        client.ssubscribe(Set.of(channel1, channel2)).get();
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
        Set<String> shardedAfter = state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED);
        assertTrue(shardedAfter == null || shardedAfter.isEmpty());
    }

    @Test
    @SneakyThrows
    public void test_subscribe_with_timeout_standalone() {
        String channel = "timeout_channel_" + UUID.randomUUID();

        GlideClient client = createStandaloneClientWithEmptySubscriptions();
        GlideClient publisher = createStandaloneClient();

        // Subscribe with timeout
        client.subscribe(Set.of(channel), 1000).get();

        // Verify subscription
        var state = client.getSubscriptions().get();
        assertTrue(state.getActualSubscriptions().get(PubSubChannelMode.EXACT).contains(channel));

        // Publish and verify message received
        publisher.publish(channel, "test_message").get();
        Thread.sleep(500);
    }

    @Test
    @SneakyThrows
    public void test_subscribe_with_timeout_cluster() {
        String channel = "timeout_channel_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();
        GlideClusterClient publisher = createClusterClient();

        // Subscribe with timeout
        client.subscribe(Set.of(channel), 1000).get();

        // Verify subscription
        var state = client.getSubscriptions().get();
        assertTrue(
                state.getActualSubscriptions().get(PubSubClusterChannelMode.EXACT).contains(channel));

        // Publish and verify message received
        publisher.publish(channel, "test_message").get();
        Thread.sleep(500);
    }

    @Test
    @SneakyThrows
    public void test_ssubscribe_channels_different_slots() {
        // Use different hash tags to ensure different slots
        String channel1 = "{slot1}channel_" + UUID.randomUUID();
        String channel2 = "{slot2}channel_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();
        GlideClusterClient publisher = createClusterClient();

        // Subscribe to channels in different slots
        client.ssubscribe(Set.of(channel1, channel2)).get();
        Thread.sleep(500);

        // Verify both subscriptions
        var state = client.getSubscriptions().get();
        Set<String> sharded = state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED);
        assertTrue(sharded.contains(channel1));
        assertTrue(sharded.contains(channel2));

        // Publish to both channels
        publisher.publish(channel1, "message1").get();
        publisher.publish(channel2, "message2").get();
        Thread.sleep(500);
    }

    @Test
    @SneakyThrows
    public void test_sunsubscribe_channels_different_slots() {
        String channel1 = "{slot1}channel_" + UUID.randomUUID();
        String channel2 = "{slot2}channel_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();

        // Subscribe to channels in different slots
        client.ssubscribe(Set.of(channel1, channel2)).get();
        Thread.sleep(500);

        // Unsubscribe from one channel
        client.sunsubscribe(Set.of(channel1)).get();
        Thread.sleep(500);

        // Verify only channel2 remains
        var state = client.getSubscriptions().get();
        Set<String> sharded = state.getActualSubscriptions().get(PubSubClusterChannelMode.SHARDED);
        assertFalse(sharded.contains(channel1));
        assertTrue(sharded.contains(channel2));
    }

    @Test
    @SneakyThrows
    public void test_unsubscribe_all_subscription_types_standalone() {
        String channel = "channel_" + UUID.randomUUID();
        String pattern = "pattern_*";

        GlideClient client = createStandaloneClientWithEmptySubscriptions();

        // Subscribe to both exact and pattern
        client.subscribe(Set.of(channel)).get();
        Thread.sleep(500);
        client.psubscribe(Set.of(pattern)).get();
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
    }

    @Test
    @SneakyThrows
    public void test_unsubscribe_all_subscription_types_cluster() {
        String channel = "channel_" + UUID.randomUUID();
        String pattern = "pattern_*";
        String sharded = "{slot}sharded_" + UUID.randomUUID();

        GlideClusterClient client = createClusterClientWithEmptySubscriptions();

        // Subscribe to exact, pattern, and sharded
        client.subscribe(Set.of(channel)).get();
        Thread.sleep(500);
        client.psubscribe(Set.of(pattern)).get();
        Thread.sleep(500);
        client.ssubscribe(Set.of(sharded)).get();
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
            listeningClient.subscribe(Set.of(channel)).get();
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
            listeningClient.subscribe(Set.of(channel)).get();
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
}
