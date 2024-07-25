/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import {
    BaseClientConfiguration,
    ClusterClientConfiguration,
    ConfigurationError,
    GlideClient,
    GlideClientConfiguration,
    GlideClusterClient,
    ProtocolVersion,
    PubSubMsg,
    TimeoutError,
} from "..";
import RedisCluster from "../../utils/TestUtils";
import { flushAndCloseClient } from "./TestUtilities";

export type TGlideClient = GlideClient | GlideClusterClient;

/**
 * Enumeration for specifying the method of PUBSUB subscription.
 */
const MethodTesting = {
    Async: 0, // Uses asynchronous subscription method.
    Sync: 1, // Uses synchronous subscription method.
    Callback: 2, // Uses callback-based subscription method.
};

const TIMEOUT = 120000;
describe("PubSub", () => {
    let cmeCluster: RedisCluster;
    let cmdCluster: RedisCluster;
    beforeAll(async () => {
        cmdCluster = await RedisCluster.createCluster(false, 1, 1);
        cmeCluster = await RedisCluster.createCluster(true, 3, 1);
    }, 40000);
    afterEach(async () => {
        await flushAndCloseClient(false, cmdCluster.getAddresses());
        await flushAndCloseClient(true, cmeCluster.getAddresses());
    });
    afterAll(async () => {
        await cmeCluster.close();
        await cmdCluster.close();
    });

    async function createClients(
        clusterMode: boolean,
        options: ClusterClientConfiguration | GlideClientConfiguration,
        options2: ClusterClientConfiguration | GlideClientConfiguration,
        pubsubSubscriptions:
            | GlideClientConfiguration.PubSubSubscriptions
            | ClusterClientConfiguration.PubSubSubscriptions,
        pubsubSubscriptions2?:
            | GlideClientConfiguration.PubSubSubscriptions
            | ClusterClientConfiguration.PubSubSubscriptions,
    ): Promise<[TGlideClient, TGlideClient]> {
        let client: TGlideClient | undefined;

        if (clusterMode) {
            try {
                options.pubsubSubscriptions = pubsubSubscriptions;
                client = await GlideClusterClient.createClient(options);
                options2.pubsubSubscriptions = pubsubSubscriptions2;
                const client2 = await GlideClusterClient.createClient(options2);
                return [client, client2];
            } catch (error) {
                if (client) {
                    client.close();
                }

                throw error;
            }
        } else {
            try {
                options.pubsubSubscriptions = pubsubSubscriptions;
                client = await GlideClient.createClient(options);
                options2.pubsubSubscriptions = pubsubSubscriptions2;
                const client2 = await GlideClient.createClient(options2);
                return [client, client2];
            } catch (error) {
                if (client) {
                    client.close();
                }

                throw error;
            }
        }
    }

    const getOptions = (
        clusterMode: boolean,
        protocol: ProtocolVersion = ProtocolVersion.RESP3,
    ): BaseClientConfiguration => {
        if (clusterMode) {
            return {
                addresses: cmeCluster.ports().map((port) => ({
                    host: "localhost",
                    port,
                })),
                protocol,
            };
        }

        return {
            addresses: cmdCluster.ports().map((port) => ({
                host: "localhost",
                port,
            })),
            protocol,
        };
    };

    function decodePubSubMsg(msg: PubSubMsg | null = null) {
        if (!msg) {
            return {
                message: "",
                channel: "",
                pattern: null,
            };
        }

        const stringMsg = Buffer.from(msg.message).toString("utf-8");
        const stringChannel = Buffer.from(msg.channel).toString("utf-8");
        const stringPattern = msg.pattern
            ? Buffer.from(msg.pattern).toString("utf-8")
            : null;

        return {
            message: stringMsg,
            channel: stringChannel,
            pattern: stringPattern,
        };
    }

    async function getMessageByMethod(
        method: number,
        client: TGlideClient,
        messages: PubSubMsg[] | null = null,
        index?: number,
    ) {
        if (method === MethodTesting.Async) {
            const pubsubMessage = await client.getPubSubMessage();
            return decodePubSubMsg(pubsubMessage);
        } else if (method === MethodTesting.Sync) {
            const pubsubMessage = client.tryGetPubSubMessage();
            return decodePubSubMsg(pubsubMessage);
        } else {
            if (messages && index !== null) {
                return decodePubSubMsg(messages[index!]);
            }

            throw new Error(
                "Messages and index must be provided for this method.",
            );
        }
    }

    async function checkNoMessagesLeft(
        method: number,
        client: TGlideClient,
        callback: PubSubMsg[] | null = [],
        expectedCallbackMessagesCount = 0,
    ) {
        if (method === MethodTesting.Async) {
            try {
                // Assert there are no messages to read
                await Promise.race([
                    client.getPubSubMessage(),
                    new Promise((_, reject) =>
                        setTimeout(
                            () => reject(new TimeoutError("TimeoutError")),
                            3000,
                        ),
                    ),
                ]);
                throw new Error("Expected TimeoutError but got a message.");
            } catch (error) {
                if (!(error instanceof TimeoutError)) {
                    throw error;
                }
            }
        } else if (method === MethodTesting.Sync) {
            const message = client.tryGetPubSubMessage();
            expect(message).toBe(null);
        } else {
            if (callback === null) {
                throw new Error("Callback must be provided.");
            }

            expect(callback.length).toBe(expectedCallbackMessagesCount);
        }
    }

    function createPubSubSubscription(
        clusterMode: boolean,
        clusterChannelsAndPatterns: Partial<
            Record<ClusterClientConfiguration.PubSubChannelModes, Set<string>>
        >,
        standaloneChannelsAndPatterns: Partial<
            Record<GlideClientConfiguration.PubSubChannelModes, Set<string>>
        >,
        callback?: (msg: PubSubMsg, context: PubSubMsg[]) => void,
        context: PubSubMsg[] | null = null,
    ) {
        if (clusterMode) {
            const mySubscriptions: ClusterClientConfiguration.PubSubSubscriptions =
                {
                    channelsAndPatterns: clusterChannelsAndPatterns,
                    callback: callback,
                    context: context,
                };
            return mySubscriptions;
        }

        const mySubscriptions: GlideClientConfiguration.PubSubSubscriptions = {
            channelsAndPatterns: standaloneChannelsAndPatterns,
            callback: callback,
            context: context,
        };
        return mySubscriptions;
    }

    async function clientCleanup(
        client: TGlideClient,
        clusterModeSubs?: ClusterClientConfiguration.PubSubSubscriptions,
    ) {
        if (client === null) {
            return;
        }

        if (clusterModeSubs) {
            for (const [channelType, channelPatterns] of Object.entries(
                clusterModeSubs.channelsAndPatterns,
            )) {
                let cmd;

                if (
                    channelType ===
                    ClusterClientConfiguration.PubSubChannelModes.Exact.toString()
                ) {
                    cmd = "UNSUBSCRIBE";
                } else if (
                    channelType ===
                    ClusterClientConfiguration.PubSubChannelModes.Pattern.toString()
                ) {
                    cmd = "PUNSUBSCRIBE";
                } else if (!cmeCluster.checkIfServerVersionLessThan("7.0.0")) {
                    cmd = "SUNSUBSCRIBE";
                } else {
                    // Disregard sharded config for versions < 7.0.0
                    continue;
                }

                for (const channelPattern of channelPatterns) {
                    await client.customCommand([cmd, channelPattern]);
                }
            }
        }

        client.close();
        // Wait briefly to ensure closure is completed
        await new Promise((resolve) => setTimeout(resolve, 1000));
    }

    function newMessage(msg: PubSubMsg, context: PubSubMsg[]): void {
        context.push(msg);
    }

    const testCases: [
        boolean,
        (typeof MethodTesting)[keyof typeof MethodTesting],
    ][] = [
        [true, MethodTesting.Async],
        [true, MethodTesting.Sync],
        [true, MethodTesting.Callback],
        [false, MethodTesting.Async],
        [false, MethodTesting.Sync],
        [false, MethodTesting.Callback],
    ];

    /**
     * Tests the basic happy path for exact PUBSUB functionality.
     *
     * This test covers the basic PUBSUB flow using three different methods:
     * Async, Sync, and Callback. It verifies that a message published to a
     * specific channel is correctly received by a subscriber.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each(testCases)(
        `pubsub exact happy path test_%p%p`,
        async (clusterMode, method) => {
            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient;
            let publishingClient: TGlideClient;

            try {
                const channel = uuidv4();
                const message = uuidv4();
                const options = getOptions(clusterMode);
                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel]),
                    },
                    callback,
                    context,
                );
                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    options,
                    getOptions(clusterMode),
                    pubSub,
                );

                const result = await publishingClient.publish(message, channel);

                if (clusterMode) {
                    expect(result).toEqual(1);
                }

                // Allow the message to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const pubsubMessage = await getMessageByMethod(
                    method,
                    listeningClient,
                    context,
                    0,
                );
                expect(pubsubMessage.message).toEqual(message);
                expect(pubsubMessage.channel).toEqual(channel);
                expect(pubsubMessage.pattern).toEqual(null);

                await checkNoMessagesLeft(method, listeningClient, context, 1);
            } finally {
                await clientCleanup(publishingClient!);
                await clientCleanup(
                    listeningClient!,
                    clusterMode ? pubSub! : undefined,
                );
            }
        },
        TIMEOUT,
    );

    /**
     * Test the coexistence of async and sync message retrieval methods in exact PUBSUB.
     *
     * This test covers the scenario where messages are published to a channel
     * and received using both async and sync methods to ensure that both methods
     * can coexist and function correctly.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "pubsub exact happy path coexistence test_%p",
        async (clusterMode) => {
            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const channel = uuidv4();
                const message = uuidv4();
                const message2 = uuidv4();

                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel]),
                    },
                );

                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                for (const msg of [message, message2]) {
                    const result = await publishingClient.publish(msg, channel);

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the message to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const asyncMsgRes = await listeningClient.getPubSubMessage();
                const syncMsgRes = listeningClient.tryGetPubSubMessage();
                expect(syncMsgRes).toBeTruthy();

                const asyncMsg = decodePubSubMsg(asyncMsgRes);
                const syncMsg = decodePubSubMsg(syncMsgRes);

                expect([message, message2]).toContain(asyncMsg.message);
                expect(asyncMsg.channel).toEqual(channel);
                expect(asyncMsg.pattern).toBeNull();

                expect([message, message2]).toContain(syncMsg.message);
                expect(syncMsg.channel).toEqual(channel);
                expect(syncMsg.pattern).toBeNull();

                expect(asyncMsg.message).not.toEqual(syncMsg.message);

                // Assert there are no messages to read
                await checkNoMessagesLeft(MethodTesting.Async, listeningClient);
                expect(listeningClient.tryGetPubSubMessage()).toBeNull();
            } finally {
                await clientCleanup(publishingClient!);
                await clientCleanup(
                    listeningClient!,
                    clusterMode ? pubSub! : undefined,
                );
            }
        },
        TIMEOUT,
    );

    /**
     * Tests publishing and receiving messages across many channels in exact PUBSUB.
     *
     * This test covers the scenario where multiple channels each receive their own
     * unique message. It verifies that messages are correctly published and received
     * using different retrieval methods: async, sync, and callback.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each(testCases)(
        "pubsub exact happy path many channels test_%p_%p",
        async (clusterMode, method) => {
            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";

            try {
                // Create a map of channels to random messages with shard prefix
                const channelsAndMessages: Record<string, string> = {};

                for (let i = 0; i < NUM_CHANNELS; i++) {
                    const channel = `${shardPrefix}${uuidv4()}`;
                    const message = uuidv4();
                    channelsAndMessages[channel] = message;
                }

                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(channelsAndMessages)),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(channelsAndMessages)),
                    },
                    callback,
                    context,
                );

                // Create clients for listening and publishing
                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Publish messages to each channel
                for (const [channel, message] of Object.entries(
                    channelsAndMessages,
                )) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Check if all messages are received correctly
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    );
                    expect(
                        pubsubMsg.channel in channelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        channelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toBeNull();
                    delete channelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all messages
                expect(Object.keys(channelsAndMessages).length).toEqual(0);

                // Check no messages left
                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS,
                );
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests publishing and receiving messages across many channels in exact PUBSUB,
     * ensuring coexistence of async and sync retrieval methods.
     *
     * This test covers scenarios where multiple channels each receive their own unique message.
     * It verifies that messages are correctly published and received using both async and sync methods
     * to ensure that both methods can coexist and function correctly.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "pubsub exact happy path many channels coexistence test_%p",
        async (clusterMode) => {
            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";

            try {
                // Create a map of channels to random messages with shard prefix
                const channelsAndMessages: Record<string, string> = {};

                for (let i = 0; i < NUM_CHANNELS; i++) {
                    const channel = `${shardPrefix}${uuidv4()}`;
                    const message = uuidv4();
                    channelsAndMessages[channel] = message;
                }

                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(channelsAndMessages)),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(channelsAndMessages)),
                    },
                );

                // Create clients for listening and publishing
                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Publish messages to each channel
                for (const [channel, message] of Object.entries(
                    channelsAndMessages,
                )) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Check if all messages are received correctly by each method
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const method =
                        index % 2 === 0
                            ? MethodTesting.Sync
                            : MethodTesting.Async;
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                    );

                    expect(
                        pubsubMsg.channel in channelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        channelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toBeNull();
                    delete channelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all messages
                expect(Object.keys(channelsAndMessages).length).toEqual(0);

                // Assert there are no messages to read
                await checkNoMessagesLeft(MethodTesting.Async, listeningClient);
                expect(listeningClient.tryGetPubSubMessage()).toBeNull();
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test sharded PUBSUB functionality with different message retrieval methods.
     *
     * This test covers the sharded PUBSUB flow using three different methods:
     * Async, Sync, and Callback. It verifies that a message published to a
     * specific sharded channel is correctly received by a subscriber.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each([
        [true, MethodTesting.Async],
        [true, MethodTesting.Sync],
        [true, MethodTesting.Callback],
    ])(
        "sharded pubsub test_%p_%p",
        async (clusterMode, method) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const channel = uuidv4();
            const message = uuidv4();
            const publishResponse = 1;

            try {
                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Sharded]:
                            new Set([channel]),
                    },
                    {},
                    callback,
                    context,
                );

                // Create clients for listening and publishing
                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                const result = await (
                    publishingClient as GlideClusterClient
                ).publish(message, channel, true);

                expect(result).toEqual(publishResponse);

                // Allow the message to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const pubsubMsg = await getMessageByMethod(
                    method,
                    listeningClient,
                    context,
                    0,
                );

                expect(pubsubMsg.message).toEqual(message);
                expect(pubsubMsg.channel).toEqual(channel);
                expect(pubsubMsg.pattern).toBeNull();

                // Assert there are no messages to read
                await checkNoMessagesLeft(method, listeningClient, context, 1);
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test sharded PUBSUB with co-existence of multiple messages.
     *
     * This test verifies the behavior of sharded PUBSUB when multiple messages are published
     * to the same sharded channel. It ensures that both async and sync methods of message retrieval
     * function correctly in this scenario.
     *
     * It covers the scenario where messages are published to a sharded channel and received using
     * both async and sync methods. This ensures that the asynchronous and synchronous message
     * retrieval methods can coexist without interfering with each other and operate as expected.
     */
    it(
        "sharded pubsub co-existence test",
        async () => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const channel = uuidv4();
            const message = uuidv4();
            const message2 = uuidv4();

            try {
                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    true,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Sharded]:
                            new Set([channel]),
                    },
                    {},
                );

                // Create clients for listening and publishing
                [listeningClient, publishingClient] = await createClients(
                    true,
                    getOptions(true),
                    getOptions(true),
                    pubSub,
                );

                let result = await (
                    publishingClient as GlideClusterClient
                ).publish(message, channel, true);
                expect(result).toEqual(1);

                result = await (publishingClient as GlideClusterClient).publish(
                    message2,
                    channel,
                    true,
                );
                expect(result).toEqual(1);

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const asyncMsgRes = await listeningClient.getPubSubMessage();
                const syncMsgRes = listeningClient.tryGetPubSubMessage();
                expect(syncMsgRes).toBeTruthy();

                const asyncMsg = decodePubSubMsg(asyncMsgRes);
                const syncMsg = decodePubSubMsg(syncMsgRes);

                expect([message, message2]).toContain(asyncMsg.message);
                expect(asyncMsg.channel).toEqual(channel);
                expect(asyncMsg.pattern).toBeNull();

                expect([message, message2]).toContain(syncMsg.message);
                expect(syncMsg.channel).toEqual(channel);
                expect(syncMsg.pattern).toBeNull();

                expect(asyncMsg.message).not.toEqual(syncMsg.message);

                // Assert there are no messages to read
                await checkNoMessagesLeft(MethodTesting.Async, listeningClient);
                expect(listeningClient.tryGetPubSubMessage()).toBeNull();
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(listeningClient, pubSub!);
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test sharded PUBSUB with multiple channels and different message retrieval methods.
     *
     * This test verifies the behavior of sharded PUBSUB when multiple messages are published
     * across multiple sharded channels. It covers three different message retrieval methods:
     * Async, Sync, and Callback.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each([
        [true, MethodTesting.Async],
        [true, MethodTesting.Sync],
        [true, MethodTesting.Callback],
    ])(
        "sharded pubsub many channels test_%p_%p",
        async (clusterMode, method) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";
            const publishResponse = 1;

            // Create a map of channels to random messages with shard prefix
            const channelsAndMessages: Record<string, string> = {};

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const channel = `${shardPrefix}${uuidv4()}`;
                const message = uuidv4();
                channelsAndMessages[channel] = message;
            }

            try {
                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Sharded]:
                            new Set(Object.keys(channelsAndMessages)),
                    },
                    {},
                    callback,
                    context,
                );

                // Create clients for listening and publishing
                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Publish messages to each channel
                for (const [channel, message] of Object.entries(
                    channelsAndMessages,
                )) {
                    const result = await (
                        publishingClient as GlideClusterClient
                    ).publish(message, channel, true);
                    expect(result).toEqual(publishResponse);
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Check if all messages are received correctly
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    );

                    expect(
                        pubsubMsg.channel in channelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        channelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toBeNull();
                    delete channelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all messages
                expect(Object.keys(channelsAndMessages).length).toEqual(0);

                // Assert there are no more messages to read
                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS,
                );
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test PUBSUB with pattern subscription using different message retrieval methods.
     *
     * This test verifies the behavior of PUBSUB when subscribing to a pattern and receiving
     * messages using three different methods: Async, Sync, and Callback.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each(testCases)(
        "pubsub pattern test_%p_%p",
        async (clusterMode, method) => {
            const PATTERN = `{{channel}}:*`;
            const channels: Record<string, string> = {
                [`{{channel}}:${uuidv4()}`]: uuidv4(),
                [`{{channel}}:${uuidv4()}`]: uuidv4(),
            };

            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            let context: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                context = [];
                callback = newMessage;
            }

            try {
                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    callback,
                    context,
                );

                // Create clients for listening and publishing
                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Publish messages to each channel
                for (const [channel, message] of Object.entries(channels)) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Check if all messages are received correctly
                for (let index = 0; index < 2; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    );
                    expect(pubsubMsg.channel in channels).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        channels[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                    delete channels[pubsubMsg.channel];
                }

                // Check that we received all messages
                expect(Object.keys(channels).length).toEqual(0);

                // Assert there are no more messages to read
                await checkNoMessagesLeft(method, listeningClient, context, 2);
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests the coexistence of async and sync message retrieval methods in pattern-based PUBSUB.
     *
     * This test covers the scenario where messages are published to a channel that match a specified pattern
     * and received using both async and sync methods to ensure that both methods
     * can coexist and function correctly.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "pubsub pattern coexistence test_%p",
        async (clusterMode) => {
            const PATTERN = `{{channel}}:*`;
            const channels: Record<string, string> = {
                [`{{channel}}:${uuidv4()}`]: uuidv4(),
                [`{{channel}}:${uuidv4()}`]: uuidv4(),
            };

            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                );

                // Create clients for listening and publishing
                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Publish messages to each channel
                for (const [channel, message] of Object.entries(channels)) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Check if all messages are received correctly by each method
                for (let index = 0; index < 2; index++) {
                    const method =
                        index % 2 === 0
                            ? MethodTesting.Async
                            : MethodTesting.Sync;
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                    );

                    expect(Object.keys(channels)).toContain(pubsubMsg.channel);
                    expect(pubsubMsg.message).toEqual(
                        channels[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                    delete channels[pubsubMsg.channel];
                }

                // Check that we received all messages
                expect(Object.keys(channels).length).toEqual(0);

                // Assert there are no more messages to read
                await checkNoMessagesLeft(MethodTesting.Async, listeningClient);
                expect(listeningClient.tryGetPubSubMessage()).toBeNull();
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests publishing and receiving messages across many channels in pattern-based PUBSUB.
     *
     * This test covers the scenario where messages are published to multiple channels that match a specified pattern
     * and received. It verifies that messages are correctly published and received
     * using different retrieval methods: async, sync, and callback.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each(testCases)(
        "pubsub pattern many channels test_%p",
        async (clusterMode, method) => {
            const NUM_CHANNELS = 256;
            const PATTERN = "{{channel}}:*";
            const channels: Record<string, string> = {};

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const channel = `{{channel}}:${uuidv4()}`;
                const message = uuidv4();
                channels[channel] = message;
            }

            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            let context: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                context = [];
                callback = newMessage;
            }

            try {
                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    callback,
                    context,
                );

                // Create clients for listening and publishing
                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Publish messages to each channel
                for (const [channel, message] of Object.entries(channels)) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Check if all messages are received correctly
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    );
                    expect(pubsubMsg.channel in channels).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        channels[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                    delete channels[pubsubMsg.channel];
                }

                // Check that we received all messages
                expect(Object.keys(channels).length).toEqual(0);

                // Assert there are no more messages to read
                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS,
                );
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests combined exact and pattern PUBSUB with one client.
     *
     * This test verifies that a single client can correctly handle both exact and pattern PUBSUB
     * subscriptions. It covers the following scenarios:
     * - Subscribing to multiple channels with exact names and verifying message reception.
     * - Subscribing to channels using a pattern and verifying message reception.
     * - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each(testCases)(
        "pubsub combined exact and pattern test_%p_%p",
        async (clusterMode, method) => {
            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: Record<string, string> = {};
            const patternChannelsAndMessages: Record<string, string> = {};

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${uuidv4()}`;
                const patternChannel = `{{pattern}}:${uuidv4()}`;
                exactChannelsAndMessages[exactChannel] = uuidv4();
                patternChannelsAndMessages[patternChannel] = uuidv4();
            }

            const allChannelsAndMessages: Record<string, string> = {
                ...exactChannelsAndMessages,
                ...patternChannelsAndMessages,
            };

            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            let context: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                context = [];
                callback = newMessage;
            }

            try {
                // Setup PUBSUB for exact channels and pattern
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(exactChannelsAndMessages)),
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(exactChannelsAndMessages)),
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    callback,
                    context,
                );

                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Publish messages to all channels
                for (const [channel, message] of Object.entries(
                    allChannelsAndMessages,
                )) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const length = Object.keys(allChannelsAndMessages).length;

                // Check if all messages are received correctly
                for (let index = 0; index < length; index++) {
                    const pubsubMsg: PubSubMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    );
                    const pattern =
                        pubsubMsg.channel in patternChannelsAndMessages
                            ? PATTERN
                            : null;
                    expect(
                        pubsubMsg.channel in allChannelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        allChannelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toEqual(pattern);
                    delete allChannelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all messages
                expect(Object.keys(allChannelsAndMessages).length).toEqual(0);

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS * 2,
                );
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests combined exact and pattern PUBSUB with multiple clients, one for each subscription.
     *
     * This test verifies that separate clients can correctly handle both exact and pattern PUBSUB
     * subscriptions. It covers the following scenarios:
     * - Subscribing to multiple channels with exact names and verifying message reception.
     * - Subscribing to channels using a pattern and verifying message reception.
     * - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
     * - Verifying that no messages are left unread.
     * - Properly unsubscribing from all channels to avoid interference with other tests.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each(testCases)(
        "pubsub combined exact and pattern multiple clients test_%p_%p",
        async (clusterMode, method) => {
            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: Record<string, string> = {};
            const patternChannelsAndMessages: Record<string, string> = {};

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${uuidv4()}`;
                const patternChannel = `{{pattern}}:${uuidv4()}`;
                exactChannelsAndMessages[exactChannel] = uuidv4();
                patternChannelsAndMessages[patternChannel] = uuidv4();
            }

            const allChannelsAndMessages = {
                ...exactChannelsAndMessages,
                ...patternChannelsAndMessages,
            };

            let pubSubExact:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClientExact: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            let listeningClientPattern: TGlideClient | null = null;
            let clientDontCare: TGlideClient | null = null;
            let contextExact: PubSubMsg[] | null = null;
            let contextPattern: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                contextExact = [];
                contextPattern = [];
                callback = newMessage;
            }

            try {
                // Setup PUBSUB for exact channels
                pubSubExact = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(exactChannelsAndMessages)),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(exactChannelsAndMessages)),
                    },
                    callback,
                    contextExact,
                );

                [listeningClientExact, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSubExact,
                );

                // Setup PUBSUB for pattern channels
                pubSubPattern = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    callback,
                    contextPattern,
                );

                [listeningClientPattern, clientDontCare] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSubPattern,
                );

                // Publish messages to all channels
                for (const [channel, message] of Object.entries(
                    allChannelsAndMessages,
                )) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                let length = Object.keys(exactChannelsAndMessages).length;

                // Verify messages for exact PUBSUB
                for (let index = 0; index < length; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClientExact,
                        contextExact,
                        index,
                    );
                    expect(
                        pubsubMsg.channel in exactChannelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        exactChannelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toBeNull();
                    delete exactChannelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all exact messages
                expect(Object.keys(exactChannelsAndMessages).length).toEqual(0);

                length = Object.keys(patternChannelsAndMessages).length;

                // Verify messages for pattern PUBSUB
                for (let index = 0; index < length; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClientPattern,
                        contextPattern,
                        index,
                    );
                    expect(
                        pubsubMsg.channel in patternChannelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        patternChannelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                    delete patternChannelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all pattern messages
                expect(Object.keys(patternChannelsAndMessages).length).toEqual(
                    0,
                );

                // Assert no messages are left unread
                await checkNoMessagesLeft(
                    method,
                    listeningClientExact,
                    contextExact,
                    NUM_CHANNELS,
                );
                await checkNoMessagesLeft(
                    method,
                    listeningClientPattern,
                    contextPattern,
                    NUM_CHANNELS,
                );
            } finally {
                // Cleanup clients
                if (listeningClientExact) {
                    await clientCleanup(
                        listeningClientExact,
                        clusterMode ? pubSubExact! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }

                if (listeningClientPattern) {
                    await clientCleanup(
                        listeningClientPattern,
                        clusterMode ? pubSubPattern! : undefined,
                    );
                }

                if (clientDontCare) {
                    await clientCleanup(clientDontCare);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests combined exact, pattern, and sharded PUBSUB with one client.
     *
     * This test verifies that a single client can correctly handle exact, pattern, and sharded PUBSUB
     * subscriptions. It covers the following scenarios:
     * - Subscribing to multiple channels with exact names and verifying message reception.
     * - Subscribing to channels using a pattern and verifying message reception.
     * - Subscribing to channels using a sharded subscription and verifying message reception.
     * - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each([
        [true, MethodTesting.Async],
        [true, MethodTesting.Sync],
        [true, MethodTesting.Callback],
    ])(
        "pubsub combined exact, pattern, and sharded test_%p_%p",
        async (clusterMode, method) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";
            const SHARD_PREFIX = "{same-shard}";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: Record<string, string> = {};
            const patternChannelsAndMessages: Record<string, string> = {};
            const shardedChannelsAndMessages: Record<string, string> = {};

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${uuidv4()}`;
                const patternChannel = `{{pattern}}:${uuidv4()}`;
                const shardedChannel = `${SHARD_PREFIX}:${uuidv4()}`;
                exactChannelsAndMessages[exactChannel] = uuidv4();
                patternChannelsAndMessages[patternChannel] = uuidv4();
                shardedChannelsAndMessages[shardedChannel] = uuidv4();
            }

            const publishResponse = 1;
            let pubSub:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            let context: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                context = [];
                callback = newMessage;
            }

            try {
                // Setup PUBSUB for exact, pattern, and sharded channels
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(exactChannelsAndMessages)),
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                        [ClusterClientConfiguration.PubSubChannelModes.Sharded]:
                            new Set(Object.keys(shardedChannelsAndMessages)),
                    },
                    {},
                    callback,
                    context,
                );

                [listeningClient, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Publish messages to exact and pattern channels
                for (const [channel, message] of Object.entries({
                    ...exactChannelsAndMessages,
                    ...patternChannelsAndMessages,
                })) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );
                    expect(result).toEqual(publishResponse);
                }

                // Publish sharded messages
                for (const [channel, message] of Object.entries(
                    shardedChannelsAndMessages,
                )) {
                    const result = await (
                        publishingClient as GlideClusterClient
                    ).publish(message, channel, true);
                    expect(result).toEqual(publishResponse);
                }

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const allChannelsAndMessages = {
                    ...exactChannelsAndMessages,
                    ...patternChannelsAndMessages,
                    ...shardedChannelsAndMessages,
                };

                // Check if all messages are received correctly
                for (let index = 0; index < NUM_CHANNELS * 3; index++) {
                    const pubsubMsg: PubSubMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    );
                    const pattern =
                        pubsubMsg.channel in patternChannelsAndMessages
                            ? PATTERN
                            : null;
                    expect(
                        pubsubMsg.channel in allChannelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        allChannelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toEqual(pattern);
                    delete allChannelsAndMessages[pubsubMsg.channel];
                }

                // Assert we received all messages
                expect(Object.keys(allChannelsAndMessages).length).toEqual(0);

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS * 3,
                );
            } finally {
                // Cleanup clients
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        clusterMode ? pubSub! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests combined exact, pattern, and sharded PUBSUB with multiple clients, one for each subscription.
     *
     * This test verifies that separate clients can correctly handle exact, pattern, and sharded PUBSUB
     * subscriptions. It covers the following scenarios:
     * - Subscribing to multiple channels with exact names and verifying message reception.
     * - Subscribing to channels using a pattern and verifying message reception.
     * - Subscribing to channels using a sharded subscription and verifying message reception.
     * - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
     * - Verifying that no messages are left unread.
     * - Properly unsubscribing from all channels to avoid interference with other tests.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each([
        [true, MethodTesting.Async],
        [true, MethodTesting.Sync],
        [true, MethodTesting.Callback],
    ])(
        "pubsub combined exact, pattern, and sharded multi-client test_%p_%p",
        async (clusterMode, method) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";
            const SHARD_PREFIX = "{same-shard}";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: Record<string, string> = {};
            const patternChannelsAndMessages: Record<string, string> = {};
            const shardedChannelsAndMessages: Record<string, string> = {};

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${uuidv4()}`;
                const patternChannel = `{{pattern}}:${uuidv4()}`;
                const shardedChannel = `${SHARD_PREFIX}:${uuidv4()}`;
                exactChannelsAndMessages[exactChannel] = uuidv4();
                patternChannelsAndMessages[patternChannel] = uuidv4();
                shardedChannelsAndMessages[shardedChannel] = uuidv4();
            }

            const publishResponse = 1;
            let listeningClientExact: TGlideClient | null = null;
            let listeningClientPattern: TGlideClient | null = null;
            let listeningClientSharded: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            let pubSubExact:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubSharded:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;

            let context: PubSubMsg[] | null = null;
            let callback;

            const callbackMessagesExact: PubSubMsg[] = [];
            const callbackMessagesPattern: PubSubMsg[] = [];
            const callbackMessagesSharded: PubSubMsg[] = [];

            if (method === MethodTesting.Callback) {
                callback = newMessage;
                context = callbackMessagesExact;
            }

            try {
                // Setup PUBSUB for exact channels
                pubSubExact = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(Object.keys(exactChannelsAndMessages)),
                    },
                    {},
                    callback,
                    context,
                );

                [listeningClientExact, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSubExact,
                );

                if (method === MethodTesting.Callback) {
                    context = callbackMessagesPattern;
                }

                // Setup PUBSUB for pattern channels
                pubSubPattern = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([PATTERN]),
                    },
                    {},
                    callback,
                    context,
                );

                if (method === MethodTesting.Callback) {
                    context = callbackMessagesSharded;
                }

                pubSubSharded = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Sharded]:
                            new Set(Object.keys(shardedChannelsAndMessages)),
                    },
                    {},
                    callback,
                    context,
                );

                [listeningClientPattern, listeningClientSharded] =
                    await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSubPattern,
                        pubSubSharded,
                    );

                // Publish messages to exact and pattern channels
                for (const [channel, message] of Object.entries({
                    ...exactChannelsAndMessages,
                    ...patternChannelsAndMessages,
                })) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );
                    expect(result).toEqual(publishResponse);
                }

                // Publish sharded messages to all channels
                for (const [channel, message] of Object.entries(
                    shardedChannelsAndMessages,
                )) {
                    const result = await (
                        publishingClient as GlideClusterClient
                    ).publish(message, channel, true);
                    expect(result).toEqual(publishResponse);
                }

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Verify messages for exact PUBSUB
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClientExact,
                        callbackMessagesExact,
                        index,
                    );
                    expect(
                        pubsubMsg.channel in exactChannelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        exactChannelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toBeNull();
                    delete exactChannelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all messages for exact PUBSUB
                expect(Object.keys(exactChannelsAndMessages).length).toEqual(0);

                // Verify messages for pattern PUBSUB
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClientPattern,
                        callbackMessagesPattern,
                        index,
                    );
                    expect(
                        pubsubMsg.channel in patternChannelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        patternChannelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                    delete patternChannelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all messages for pattern PUBSUB
                expect(Object.keys(patternChannelsAndMessages).length).toEqual(
                    0,
                );

                // Verify messages for sharded PUBSUB
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        listeningClientSharded,
                        callbackMessagesSharded,
                        index,
                    );
                    expect(
                        pubsubMsg.channel in shardedChannelsAndMessages,
                    ).toBeTruthy();
                    expect(pubsubMsg.message).toEqual(
                        shardedChannelsAndMessages[pubsubMsg.channel],
                    );
                    expect(pubsubMsg.pattern).toBeNull();
                    delete shardedChannelsAndMessages[pubsubMsg.channel];
                }

                // Check that we received all messages for sharded PUBSUB
                expect(Object.keys(shardedChannelsAndMessages).length).toEqual(
                    0,
                );

                await checkNoMessagesLeft(
                    method,
                    listeningClientExact,
                    callbackMessagesExact,
                    NUM_CHANNELS,
                );
                await checkNoMessagesLeft(
                    method,
                    listeningClientPattern,
                    callbackMessagesPattern,
                    NUM_CHANNELS,
                );
                await checkNoMessagesLeft(
                    method,
                    listeningClientSharded,
                    callbackMessagesSharded,
                    NUM_CHANNELS,
                );
            } finally {
                // Cleanup clients
                if (listeningClientExact) {
                    await clientCleanup(
                        listeningClientExact,
                        clusterMode ? pubSubExact! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }

                if (listeningClientPattern) {
                    await clientCleanup(
                        listeningClientPattern,
                        clusterMode ? pubSubPattern! : undefined,
                    );
                }

                if (listeningClientSharded) {
                    await clientCleanup(
                        listeningClientSharded,
                        clusterMode ? pubSubSharded! : undefined,
                    );
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests combined PUBSUB with different channel modes using the same channel name.
     * One publishing client, three listening clients, one for each mode.
     *
     * This test verifies that separate clients can correctly handle subscriptions for exact, pattern, and sharded channels with the same name.
     * It covers the following scenarios:
     * - Subscribing to an exact channel and verifying message reception.
     * - Subscribing to a pattern channel and verifying message reception.
     * - Subscribing to a sharded channel and verifying message reception.
     * - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
     * - Verifying that no messages are left unread.
     * - Properly unsubscribing from all channels to avoid interference with other tests.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each([
        [true, MethodTesting.Async],
        [true, MethodTesting.Sync],
        [true, MethodTesting.Callback],
    ])(
        "pubsub combined different channels with same name test_%p_%p",
        async (clusterMode, method) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            const CHANNEL_NAME = "same-channel-name";
            const MESSAGE_EXACT = uuidv4();
            const MESSAGE_PATTERN = uuidv4();
            const MESSAGE_SHARDED = uuidv4();

            let listeningClientExact: TGlideClient | null = null;
            let listeningClientPattern: TGlideClient | null = null;
            let listeningClientSharded: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            let pubSubExact:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubSharded:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;

            let context: PubSubMsg[] | null = null;
            let callback;

            const callbackMessagesExact: PubSubMsg[] = [];
            const callbackMessagesPattern: PubSubMsg[] = [];
            const callbackMessagesSharded: PubSubMsg[] = [];

            if (method === MethodTesting.Callback) {
                callback = newMessage;
                context = callbackMessagesExact;
            }

            try {
                // Setup PUBSUB for exact channel
                pubSubExact = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([CHANNEL_NAME]),
                    },
                    {},
                    callback,
                    context,
                );

                [listeningClientExact, publishingClient] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSubExact,
                );

                // Setup PUBSUB for pattern channel
                if (method === MethodTesting.Callback) {
                    context = callbackMessagesPattern;
                }

                pubSubPattern = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([CHANNEL_NAME]),
                    },
                    {},
                    callback,
                    context,
                );

                if (method === MethodTesting.Callback) {
                    context = callbackMessagesSharded;
                }

                pubSubSharded = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Sharded]:
                            new Set([CHANNEL_NAME]),
                    },
                    {},
                    callback,
                    context,
                );

                [listeningClientPattern, listeningClientSharded] =
                    await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSubPattern,
                        pubSubSharded,
                    );

                // Publish messages to each channel
                expect(
                    await publishingClient.publish(MESSAGE_EXACT, CHANNEL_NAME),
                ).toEqual(2);
                expect(
                    await publishingClient.publish(
                        MESSAGE_PATTERN,
                        CHANNEL_NAME,
                    ),
                ).toEqual(2);
                expect(
                    await (publishingClient as GlideClusterClient).publish(
                        MESSAGE_SHARDED,
                        CHANNEL_NAME,
                        true,
                    ),
                ).toEqual(1);

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Verify message for exact and pattern PUBSUB
                for (const [client, callback, pattern] of [
                    [listeningClientExact, callbackMessagesExact, null],
                    [
                        listeningClientPattern,
                        callbackMessagesPattern,
                        CHANNEL_NAME,
                    ],
                ] as [TGlideClient, PubSubMsg[], string | null][]) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        client,
                        callback,
                        0,
                    );
                    const pubsubMsg2 = await getMessageByMethod(
                        method,
                        client,
                        callback,
                        1,
                    );

                    expect(pubsubMsg.message).not.toEqual(pubsubMsg2.message);
                    expect([MESSAGE_PATTERN, MESSAGE_EXACT]).toContain(
                        pubsubMsg.message,
                    );
                    expect([MESSAGE_PATTERN, MESSAGE_EXACT]).toContain(
                        pubsubMsg2.message,
                    );
                    expect(pubsubMsg.channel).toEqual(CHANNEL_NAME);
                    expect(pubsubMsg2.channel).toEqual(CHANNEL_NAME);
                    expect(pubsubMsg.pattern).toEqual(pattern);
                    expect(pubsubMsg2.pattern).toEqual(pattern);
                }

                // Verify message for sharded PUBSUB
                const pubsubMsgSharded = await getMessageByMethod(
                    method,
                    listeningClientSharded,
                    callbackMessagesSharded,
                    0,
                );
                expect(pubsubMsgSharded.message).toEqual(MESSAGE_SHARDED);
                expect(pubsubMsgSharded.channel).toEqual(CHANNEL_NAME);
                expect(pubsubMsgSharded.pattern).toBeNull();

                await checkNoMessagesLeft(
                    method,
                    listeningClientExact,
                    callbackMessagesExact,
                    2,
                );
                await checkNoMessagesLeft(
                    method,
                    listeningClientPattern,
                    callbackMessagesPattern,
                    2,
                );
                await checkNoMessagesLeft(
                    method,
                    listeningClientSharded,
                    callbackMessagesSharded,
                    1,
                );
            } finally {
                // Cleanup clients
                if (listeningClientExact) {
                    await clientCleanup(
                        listeningClientExact,
                        clusterMode ? pubSubExact! : undefined,
                    );
                }

                if (publishingClient) {
                    await clientCleanup(publishingClient);
                }

                if (listeningClientPattern) {
                    await clientCleanup(
                        listeningClientPattern,
                        clusterMode ? pubSubPattern! : undefined,
                    );
                }

                if (listeningClientSharded) {
                    await clientCleanup(
                        listeningClientSharded,
                        clusterMode ? pubSubSharded! : undefined,
                    );
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests PUBSUB with two publishing clients using the same channel name.
     * One client uses pattern subscription, the other uses exact.
     * The clients publish messages to each other and to themselves.
     *
     * This test verifies that two separate clients can correctly publish to and handle subscriptions
     * for exact and pattern channels with the same name. It covers the following scenarios:
     * - Subscribing to an exact channel and verifying message reception.
     * - Subscribing to a pattern channel and verifying message reception.
     * - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
     * - Verifying that no messages are left unread.
     * - Properly unsubscribing from all channels to avoid interference with other tests.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each(testCases)(
        "pubsub two publishing clients same name test_%p_%p",
        async (clusterMode, method) => {
            const CHANNEL_NAME = "channel-name";
            const MESSAGE_EXACT = uuidv4();
            const MESSAGE_PATTERN = uuidv4();

            let clientExact: TGlideClient | null = null;
            let clientPattern: TGlideClient | null = null;

            let pubSubExact:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;

            let contextExact: PubSubMsg[] | null = null;
            let contextPattern: PubSubMsg[] | null = null;
            let callback;

            const callbackMessagesExact: PubSubMsg[] = [];
            const callbackMessagesPattern: PubSubMsg[] = [];

            if (method === MethodTesting.Callback) {
                callback = newMessage;
                contextExact = callbackMessagesExact;
                contextPattern = callbackMessagesPattern;
            }

            try {
                // Setup PUBSUB for exact channel
                pubSubExact = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([CHANNEL_NAME]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([CHANNEL_NAME]),
                    },
                    callback,
                    contextExact,
                );

                // Setup PUBSUB for pattern channels
                pubSubPattern = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([CHANNEL_NAME]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([CHANNEL_NAME]),
                    },
                    callback,
                    contextPattern,
                );

                [clientExact, clientPattern] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSubExact,
                    pubSubPattern,
                );

                // Publish messages to each channel - both clients publishing
                for (const msg of [MESSAGE_EXACT, MESSAGE_PATTERN]) {
                    const result = await clientPattern.publish(
                        msg,
                        CHANNEL_NAME,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(2);
                    }
                }

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Verify message for exact and pattern PUBSUB
                for (const [client, callback, pattern] of [
                    [clientExact, callbackMessagesExact, null],
                    [clientPattern, callbackMessagesPattern, CHANNEL_NAME],
                ] as [TGlideClient, PubSubMsg[], string | null][]) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        client,
                        callback,
                        0,
                    );
                    const pubsubMsg2 = await getMessageByMethod(
                        method,
                        client,
                        callback,
                        1,
                    );

                    expect(pubsubMsg.message).not.toEqual(pubsubMsg2.message);
                    expect([MESSAGE_PATTERN, MESSAGE_EXACT]).toContain(
                        pubsubMsg.message,
                    );
                    expect([MESSAGE_PATTERN, MESSAGE_EXACT]).toContain(
                        pubsubMsg2.message,
                    );
                    expect(pubsubMsg.channel).toEqual(CHANNEL_NAME);
                    expect(pubsubMsg2.channel).toEqual(CHANNEL_NAME);
                    expect(pubsubMsg.pattern).toEqual(pattern);
                    expect(pubsubMsg2.pattern).toEqual(pattern);
                }

                await checkNoMessagesLeft(
                    method,
                    clientPattern,
                    callbackMessagesPattern,
                    2,
                );
                await checkNoMessagesLeft(
                    method,
                    clientExact,
                    callbackMessagesExact,
                    2,
                );
            } finally {
                // Cleanup clients
                if (clientExact) {
                    await clientCleanup(
                        clientExact,
                        clusterMode ? pubSubExact! : undefined,
                    );
                }

                if (clientPattern) {
                    await clientCleanup(
                        clientPattern,
                        clusterMode ? pubSubPattern! : undefined,
                    );
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests PUBSUB with 3 publishing clients using the same channel name.
     * One client uses pattern subscription, one uses exact, and one uses sharded.
     *
     * This test verifies that 3 separate clients can correctly publish to and handle subscriptions
     * for exact, sharded, and pattern channels with the same name. It covers the following scenarios:
     * - Subscribing to an exact channel and verifying message reception.
     * - Subscribing to a pattern channel and verifying message reception.
     * - Subscribing to a sharded channel and verifying message reception.
     * - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
     * - Verifying that no messages are left unread.
     * - Properly unsubscribing from all channels to avoid interference with other tests.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     */
    it.each([
        [true, MethodTesting.Async],
        [true, MethodTesting.Sync],
        [true, MethodTesting.Callback],
    ])(
        "pubsub three publishing clients same name with sharded test_%p_%p",
        async (clusterMode, method) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            const CHANNEL_NAME = "same-channel-name";
            const MESSAGE_EXACT = uuidv4();
            const MESSAGE_PATTERN = uuidv4();
            const MESSAGE_SHARDED = uuidv4();

            let clientExact: TGlideClient | null = null;
            let clientPattern: TGlideClient | null = null;
            let clientSharded: TGlideClient | null = null;
            let clientDontCare: TGlideClient | null = null;

            let pubSubExact:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubSharded:
                | ClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;

            let contextExact: PubSubMsg[] | null = null;
            let contextPattern: PubSubMsg[] | null = null;
            let contextSharded: PubSubMsg[] | null = null;
            let callback;

            const callbackMessagesExact: PubSubMsg[] = [];
            const callbackMessagesPattern: PubSubMsg[] = [];
            const callbackMessagesSharded: PubSubMsg[] = [];

            if (method === MethodTesting.Callback) {
                callback = newMessage;
                contextExact = callbackMessagesExact;
                contextPattern = callbackMessagesPattern;
                contextSharded = callbackMessagesSharded;
            }

            try {
                // Setup PUBSUB for exact channel
                pubSubExact = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([CHANNEL_NAME]),
                    },
                    {},
                    callback,
                    contextExact,
                );

                // Setup PUBSUB for pattern channels
                pubSubPattern = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([CHANNEL_NAME]),
                    },
                    {},
                    callback,
                    contextPattern,
                );

                // Setup PUBSUB for sharded channels
                pubSubSharded = createPubSubSubscription(
                    clusterMode,
                    {
                        [ClusterClientConfiguration.PubSubChannelModes.Sharded]:
                            new Set([CHANNEL_NAME]),
                    },
                    {},
                    callback,
                    contextSharded,
                );

                [clientExact, clientPattern] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSubExact,
                    pubSubPattern,
                );

                [clientSharded, clientDontCare] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSubSharded,
                );

                // Publish messages to each channel - all clients publishing
                const publishResponse = 2;

                expect(
                    await clientPattern.publish(MESSAGE_EXACT, CHANNEL_NAME),
                ).toEqual(publishResponse);

                expect(
                    await clientSharded.publish(MESSAGE_PATTERN, CHANNEL_NAME),
                ).toEqual(publishResponse);

                expect(
                    await (clientExact as GlideClusterClient).publish(
                        MESSAGE_SHARDED,
                        CHANNEL_NAME,
                        true,
                    ),
                ).toEqual(1);

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Verify message for exact and pattern PUBSUB
                for (const [client, callback, pattern] of [
                    [clientExact, callbackMessagesExact, null],
                    [clientPattern, callbackMessagesPattern, CHANNEL_NAME],
                ] as [TGlideClient, PubSubMsg[], string | null][]) {
                    const pubsubMsg = await getMessageByMethod(
                        method,
                        client,
                        callback,
                        0,
                    );
                    const pubsubMsg2 = await getMessageByMethod(
                        method,
                        client,
                        callback,
                        1,
                    );

                    expect(pubsubMsg.message).not.toEqual(pubsubMsg2.message);
                    expect([MESSAGE_PATTERN, MESSAGE_EXACT]).toContain(
                        pubsubMsg.message,
                    );
                    expect([MESSAGE_PATTERN, MESSAGE_EXACT]).toContain(
                        pubsubMsg2.message,
                    );
                    expect(pubsubMsg.channel).toEqual(CHANNEL_NAME);
                    expect(pubsubMsg2.channel).toEqual(CHANNEL_NAME);
                    expect(pubsubMsg.pattern).toEqual(pattern);
                    expect(pubsubMsg2.pattern).toEqual(pattern);
                }

                const shardedMsg = await getMessageByMethod(
                    method,
                    clientSharded,
                    callbackMessagesSharded,
                    0,
                );

                expect(shardedMsg.message).toEqual(MESSAGE_SHARDED);
                expect(shardedMsg.channel).toEqual(CHANNEL_NAME);
                expect(shardedMsg.pattern).toBeNull();

                await checkNoMessagesLeft(
                    method,
                    clientPattern,
                    callbackMessagesPattern,
                    2,
                );
                await checkNoMessagesLeft(
                    method,
                    clientExact,
                    callbackMessagesExact,
                    2,
                );
                await checkNoMessagesLeft(
                    method,
                    clientSharded,
                    callbackMessagesSharded,
                    1,
                );
            } finally {
                // Cleanup clients
                if (clientExact) {
                    await clientCleanup(
                        clientExact,
                        clusterMode ? pubSubExact! : undefined,
                    );
                }

                if (clientPattern) {
                    await clientCleanup(
                        clientPattern,
                        clusterMode ? pubSubPattern! : undefined,
                    );
                }

                if (clientSharded) {
                    await clientCleanup(
                        clientSharded,
                        clusterMode ? pubSubSharded! : undefined,
                    );
                }

                if (clientDontCare) {
                    await clientCleanup(clientDontCare, undefined);
                }
            }
        },
        TIMEOUT,
    );
    describe.skip("pubsub max size message test", () => {
        const generateLargeMessage = (char: string, size: number): string => {
            let message = "";

            for (let i = 0; i < size; i++) {
                message += char;
            }

            return message;
        };

        /**
         * Tests publishing and receiving maximum size messages in PUBSUB.
         *
         * This test verifies that very large messages (512MB - BulkString max size) can be published and received
         * correctly in both cluster and standalone modes. It ensures that the PUBSUB system
         * can handle maximum size messages without errors and that async and sync message
         * retrieval methods can coexist and function correctly.
         *
         * The test covers the following scenarios:
         * - Setting up PUBSUB subscription for a specific channel.
         * - Publishing two maximum size messages to the channel.
         * - Verifying that the messages are received correctly using both async and sync methods.
         * - Ensuring that no additional messages are left after the expected messages are received.
         *
         * @param clusterMode - Indicates if the test should be run in cluster mode.
         */
        it.each([true, false])(
            "test pubsub exact max size message_%p",
            async (clusterMode) => {
                let pubSub:
                    | ClusterClientConfiguration.PubSubSubscriptions
                    | GlideClientConfiguration.PubSubSubscriptions
                    | null = null;

                let listeningClient: TGlideClient | undefined;
                let publishingClient: TGlideClient | undefined;

                const channel = uuidv4();

                const message = generateLargeMessage("1", 512 * 1024 * 1024); // 512MB message
                const message2 = generateLargeMessage("2", 512 * 1024 * 10);

                try {
                    pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [ClusterClientConfiguration.PubSubChannelModes
                                .Exact]: new Set([channel]),
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                new Set([channel]),
                        },
                    );

                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSub,
                    );

                    let result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }

                    result = await publishingClient.publish(message2, channel);

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }

                    // Allow the message to propagate
                    await new Promise((resolve) => setTimeout(resolve, 15000));

                    const asyncMsg = await listeningClient.getPubSubMessage();
                    expect(asyncMsg.message).toEqual(Buffer.from(message));
                    expect(asyncMsg.channel).toEqual(Buffer.from(channel));
                    expect(asyncMsg.pattern).toBeNull();

                    const syncMsg = listeningClient.tryGetPubSubMessage();
                    expect(syncMsg).not.toBeNull();
                    expect(syncMsg!.message).toEqual(Buffer.from(message2));
                    expect(syncMsg!.channel).toEqual(Buffer.from(channel));
                    expect(syncMsg!.pattern).toBeNull();

                    // Assert there are no messages to read
                    await checkNoMessagesLeft(
                        MethodTesting.Async,
                        listeningClient,
                    );
                    expect(listeningClient.tryGetPubSubMessage()).toBeNull();
                } finally {
                    if (listeningClient) {
                        await clientCleanup(
                            listeningClient,
                            clusterMode ? pubSub! : undefined,
                        );
                    }

                    if (publishingClient) {
                        await clientCleanup(publishingClient);
                    }
                }
            },
            TIMEOUT,
        );

        /**
         * Tests publishing and receiving maximum size messages in sharded PUBSUB.
         *
         * This test verifies that very large messages (512MB - BulkString max size) can be published and received
         * correctly. It ensures that the PUBSUB system
         * can handle maximum size messages without errors and that async and sync message
         * retrieval methods can coexist and function correctly.
         *
         * The test covers the following scenarios:
         * - Setting up PUBSUB subscription for a specific sharded channel.
         * - Publishing two maximum size messages to the channel.
         * - Verifying that the messages are received correctly using both async and sync methods.
         * - Ensuring that no additional messages are left after the expected messages are received.
         *
         * @param clusterMode - Indicates if the test should be run in cluster mode.
         */
        it.each([true])(
            "test pubsub sharded max size message_%p",
            async (clusterMode) => {
                if (cmeCluster.checkIfServerVersionLessThan("7.0.0")) return;

                let pubSub:
                    | ClusterClientConfiguration.PubSubSubscriptions
                    | GlideClientConfiguration.PubSubSubscriptions
                    | null = null;

                let listeningClient: TGlideClient | undefined;
                let publishingClient: TGlideClient | undefined;
                const channel = uuidv4();

                const message = generateLargeMessage("1", 512 * 1024 * 1024); // 512MB message
                const message2 = generateLargeMessage("2", 512 * 1024 * 1024); // 512MB message

                try {
                    pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [ClusterClientConfiguration.PubSubChannelModes
                                .Sharded]: new Set([channel]),
                        },
                        {},
                    );

                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSub,
                    );

                    expect(
                        await (publishingClient as GlideClusterClient).publish(
                            message,
                            channel,
                            true,
                        ),
                    ).toEqual(1);

                    expect(
                        await (publishingClient as GlideClusterClient).publish(
                            message2,
                            channel,
                            true,
                        ),
                    ).toEqual(1);

                    // Allow the message to propagate
                    await new Promise((resolve) => setTimeout(resolve, 15000));

                    const asyncMsg = await listeningClient.getPubSubMessage();
                    const syncMsg = listeningClient.tryGetPubSubMessage();
                    expect(syncMsg).not.toBeNull();

                    expect(asyncMsg.message).toEqual(Buffer.from(message));
                    expect(asyncMsg.channel).toEqual(Buffer.from(channel));
                    expect(asyncMsg.pattern).toBeNull();

                    expect(syncMsg!.message).toEqual(Buffer.from(message2));
                    expect(syncMsg!.channel).toEqual(Buffer.from(channel));
                    expect(syncMsg!.pattern).toBeNull();

                    // Assert there are no messages to read
                    await checkNoMessagesLeft(
                        MethodTesting.Async,
                        listeningClient,
                    );
                    expect(listeningClient.tryGetPubSubMessage()).toBeNull();
                } finally {
                    if (listeningClient) {
                        await clientCleanup(
                            listeningClient,
                            clusterMode ? pubSub! : undefined,
                        );
                    }

                    if (publishingClient) {
                        await clientCleanup(publishingClient);
                    }
                }
            },
            TIMEOUT,
        );

        /**
         * Tests publishing and receiving maximum size messages in exact PUBSUB with callback method.
         *
         * This test verifies that very large messages (512MB - BulkString max size) can be published and received
         * correctly in both cluster and standalone modes. It ensures that the PUBSUB system
         * can handle maximum size messages without errors and that the callback message
         * retrieval method works as expected.
         *
         * The test covers the following scenarios:
         * - Setting up PUBSUB subscription for a specific channel with a callback.
         * - Publishing a maximum size message to the channel.
         * - Verifying that the message is received correctly using the callback method.
         *
         * @param clusterMode - Indicates if the test should be run in cluster mode.
         */
        it.each([true, false])(
            "test pubsub exact max size message callback_%p",
            async (clusterMode) => {
                let pubSub:
                    | ClusterClientConfiguration.PubSubSubscriptions
                    | GlideClientConfiguration.PubSubSubscriptions
                    | null = null;

                let listeningClient: TGlideClient | undefined;
                let publishingClient: TGlideClient | undefined;
                const channel = uuidv4();

                const message = generateLargeMessage("0", 12 * 1024 * 1024); // 12MB message

                try {
                    const callbackMessages: PubSubMsg[] = [];
                    const callback = newMessage;

                    pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [ClusterClientConfiguration.PubSubChannelModes
                                .Exact]: new Set([channel]),
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                new Set([channel]),
                        },
                        callback,
                    );

                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSub,
                    );

                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }

                    // Allow the message to propagate
                    await new Promise((resolve) => setTimeout(resolve, 15000));

                    expect(callbackMessages.length).toEqual(1);

                    expect(callbackMessages[0].message).toEqual(
                        Buffer.from(message),
                    );
                    expect(callbackMessages[0].channel).toEqual(
                        Buffer.from(channel),
                    );
                    expect(callbackMessages[0].pattern).toBeNull();
                    // Assert no messages left
                    expect(callbackMessages.length).toEqual(1);
                } finally {
                    if (listeningClient) {
                        await clientCleanup(
                            listeningClient,
                            clusterMode ? pubSub! : undefined,
                        );
                    }

                    if (publishingClient) {
                        await clientCleanup(publishingClient);
                    }
                }
            },
            TIMEOUT,
        );

        /**
         * Tests publishing and receiving maximum size messages in sharded PUBSUB with callback method.
         *
         * This test verifies that very large messages (512MB - BulkString max size) can be published and received
         * correctly. It ensures that the PUBSUB system
         * can handle maximum size messages without errors and that callback
         * retrieval methods can coexist and function correctly.
         *
         * The test covers the following scenarios:
         * - Setting up PUBSUB subscription for a specific sharded channel.
         * - Publishing a maximum size message to the channel.
         * - Verifying that the messages are received correctly using callbacl method.
         *
         * @param clusterMode - Indicates if the test should be run in cluster mode.
         */
        it.each([true])(
            "test pubsub sharded max size message callback_%p",
            async (clusterMode) => {
                if (cmeCluster.checkIfServerVersionLessThan("7.0.0")) return;
                let pubSub:
                    | ClusterClientConfiguration.PubSubSubscriptions
                    | GlideClientConfiguration.PubSubSubscriptions
                    | null = null;

                let listeningClient: TGlideClient | undefined;
                let publishingClient: TGlideClient | undefined;
                const channel = uuidv4();

                const message = generateLargeMessage("0", 512 * 1024 * 1024); // 512MB message

                try {
                    const callbackMessages: PubSubMsg[] = [];
                    const callback = newMessage;

                    pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [ClusterClientConfiguration.PubSubChannelModes
                                .Sharded]: new Set([channel]),
                        },
                        {},
                        callback,
                    );

                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSub,
                    );

                    expect(
                        await (publishingClient as GlideClusterClient).publish(
                            message,
                            channel,
                            true,
                        ),
                    ).toEqual(1);

                    // Allow the message to propagate
                    await new Promise((resolve) => setTimeout(resolve, 15000));

                    expect(callbackMessages.length).toEqual(1);

                    expect(callbackMessages[0].message).toEqual(
                        Buffer.from(message),
                    );
                    expect(callbackMessages[0].channel).toEqual(
                        Buffer.from(channel),
                    );
                    expect(callbackMessages[0].pattern).toBeNull();

                    // Assert no messages left
                    expect(callbackMessages.length).toEqual(1);
                } finally {
                    if (listeningClient) {
                        await clientCleanup(
                            listeningClient,
                            clusterMode ? pubSub! : undefined,
                        );
                    }

                    if (publishingClient) {
                        await clientCleanup(publishingClient);
                    }
                }
            },
            TIMEOUT,
        );
    });

    /**
     * Tests that creating a RESP2 client with PUBSUB raises a ConfigurationError.
     *
     * This test ensures that the system correctly prevents the creation of a PUBSUB client
     * using the RESP2 protocol version, which is not supported.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test pubsub resp2 raise an error_%p",
        async (clusterMode) => {
            const channel = uuidv4();

            const pubSubExact = createPubSubSubscription(
                clusterMode,
                {
                    [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                        new Set([channel]),
                },
                {
                    [GlideClientConfiguration.PubSubChannelModes.Exact]:
                        new Set([channel]),
                },
            );

            await expect(
                createClients(
                    clusterMode,
                    getOptions(clusterMode, ProtocolVersion.RESP2),
                    getOptions(clusterMode, ProtocolVersion.RESP2),
                    pubSubExact,
                ),
            ).rejects.toThrow(ConfigurationError);
        },
    );

    /**
     * Tests that creating a PUBSUB client with context but without a callback raises a ConfigurationError.
     *
     * This test ensures that the system enforces the requirement of providing a callback when
     * context is supplied, preventing misconfigurations.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test pubsub context with no callback raise error_%p",
        async (clusterMode) => {
            const channel = uuidv4();
            const context: PubSubMsg[] = [];

            const pubSubExact = createPubSubSubscription(
                clusterMode,
                {
                    [ClusterClientConfiguration.PubSubChannelModes.Exact]:
                        new Set([channel]),
                },
                {
                    [GlideClientConfiguration.PubSubChannelModes.Exact]:
                        new Set([channel]),
                },
                undefined, // No callback provided
                context,
            );

            // Attempt to create clients, expecting an error
            await expect(
                createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSubExact,
                ),
            ).rejects.toThrow(ConfigurationError);
        },
        TIMEOUT,
    );
});
