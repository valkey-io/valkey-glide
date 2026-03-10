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
import ValkeyCluster from "../../utils/TestUtils";
import {
    BaseClientConfiguration,
    ConfigurationError,
    Decoder,
    GlideClient,
    GlideClientConfiguration,
    GlideClusterClient,
    GlideClusterClientConfiguration,
    GlideString,
    ProtocolVersion,
    PubSubMsg,
    TimeoutError,
} from "../build-ts";
import {
    flushAndCloseClient,
    getRandomKey,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";
import {
    Mode,
    createPubsubClient,
    parseActualSubscriptions,
    psubscribeByMethod,
    punsubscribeByMethod,
    ssubscribeByMethod,
    subscribeByMethod,
    sunsubscribeByMethod,
    unsubscribeByMethod,
    waitForSubscriptionState,
    waitForSubscriptionStateIfNeeded,
} from "./PubSubTestUtilities";

type TGlideClient = GlideClient | GlideClusterClient;

function convertGlideRecordToRecord(
    data: { channel: GlideString; numSub: number }[],
): Record<string, number> {
    const res: Record<string, number> = {};

    for (const pair of data) {
        res[pair.channel as string] = pair.numSub;
    }

    return res;
}

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
    let cmeCluster: ValkeyCluster;
    let cmdCluster: ValkeyCluster;
    beforeAll(async () => {
        const standaloneAddresses = global.STAND_ALONE_ENDPOINT;
        const clusterAddresses = global.CLUSTER_ENDPOINTS;
        // Connect to cluster or create a new one based on the parsed addresses
        const [_cmdCluster, _cmeCluster] = await Promise.all([
            standaloneAddresses
                ? ValkeyCluster.initFromExistingCluster(
                      false,
                      parseEndpoints(standaloneAddresses),
                      getServerVersion,
                  )
                : ValkeyCluster.createCluster(false, 1, 1, getServerVersion),
            clusterAddresses
                ? ValkeyCluster.initFromExistingCluster(
                      true,
                      parseEndpoints(clusterAddresses),
                      getServerVersion,
                  )
                : ValkeyCluster.createCluster(true, 3, 1, getServerVersion),
        ]);
        cmdCluster = _cmdCluster;
        cmeCluster = _cmeCluster;
    }, 40000);
    afterEach(async () => {
        if (cmdCluster) {
            await flushAndCloseClient(false, cmdCluster.getAddresses());
        }

        if (cmeCluster) {
            await flushAndCloseClient(true, cmeCluster.getAddresses());
        }
    });
    afterAll(async () => {
        if (cmdCluster) {
            await cmdCluster.close();
        }

        if (cmeCluster) {
            await cmeCluster.close();
        }
    });

    async function createClients(
        clusterMode: boolean,
        options: GlideClusterClientConfiguration | GlideClientConfiguration,
        options2: GlideClusterClientConfiguration | GlideClientConfiguration,
        pubsubSubscriptions:
            | GlideClientConfiguration.PubSubSubscriptions
            | GlideClusterClientConfiguration.PubSubSubscriptions,
        pubsubSubscriptions2?:
            | GlideClientConfiguration.PubSubSubscriptions
            | GlideClusterClientConfiguration.PubSubSubscriptions,
        decoder?: Decoder,
    ): Promise<[TGlideClient, TGlideClient]> {
        let client: TGlideClient | undefined;

        if (clusterMode) {
            try {
                client = await GlideClusterClient.createClient({
                    pubsubSubscriptions: pubsubSubscriptions,
                    defaultDecoder: decoder,
                    ...options,
                });
                const client2 = await GlideClusterClient.createClient({
                    pubsubSubscriptions: pubsubSubscriptions2,
                    defaultDecoder: decoder,
                    ...options2,
                });
                return [client, client2];
            } catch (error) {
                if (client) {
                    client.close();
                }

                throw error;
            }
        } else {
            try {
                client = await GlideClient.createClient({
                    pubsubSubscriptions: pubsubSubscriptions,
                    defaultDecoder: decoder,
                    ...options,
                });
                const client2 = await GlideClient.createClient({
                    pubsubSubscriptions: pubsubSubscriptions2,
                    defaultDecoder: decoder,
                    ...options2,
                });
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

    async function getMessageByMethod(
        method: number,
        client: TGlideClient,
        messages: PubSubMsg[] | null = null,
        index?: number,
    ) {
        if (method === MethodTesting.Async) {
            const pubsubMessage = await client.getPubSubMessage();
            return pubsubMessage;
        } else if (method === MethodTesting.Sync) {
            const pubsubMessage = client.tryGetPubSubMessage();
            return pubsubMessage;
        } else {
            if (messages && index !== null) {
                return messages[index!];
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
            Record<
                GlideClusterClientConfiguration.PubSubChannelModes,
                Set<string>
            >
        >,
        standaloneChannelsAndPatterns: Partial<
            Record<GlideClientConfiguration.PubSubChannelModes, Set<string>>
        >,
        callback?: (msg: PubSubMsg, context: PubSubMsg[]) => void,
        context: PubSubMsg[] | null = null,
    ) {
        if (clusterMode) {
            const mySubscriptions: GlideClusterClientConfiguration.PubSubSubscriptions =
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
        clusterModeSubs?: GlideClusterClientConfiguration.PubSubSubscriptions,
    ): Promise<void> {
        if (client === null) {
            return;
        }

        const clusterMode = clusterModeSubs !== undefined;

        try {
            const result = await client.customCommand(["GET_SUBSCRIPTIONS"]);
            const { exact, pattern, sharded } =
                parseActualSubscriptions(result);

            const hasSubscriptions =
                exact.length > 0 ||
                pattern.length > 0 ||
                (clusterMode && sharded.length > 0);

            if (!hasSubscriptions) {
                return;
            }

            // Send unsubscribe commands
            if (exact.length > 0) {
                await client.customCommand(["UNSUBSCRIBE"]).catch(() => void 0);
            }

            if (pattern.length > 0) {
                await client
                    .customCommand(["PUNSUBSCRIBE"])
                    .catch(() => void 0);
            }

            if (clusterMode && sharded.length > 0) {
                await client
                    .customCommand(["SUNSUBSCRIBE"])
                    .catch(() => void 0);
            }

            // Wait for subscriptions to clear
            const timeoutMs = 3000;
            const startTime = Date.now();

            while (Date.now() - startTime < timeoutMs) {
                const pollResult = await client
                    .customCommand(["GET_SUBSCRIPTIONS"])
                    .catch(() => null);

                if (pollResult === null) break;

                const poll = parseActualSubscriptions(pollResult);
                const isEmpty =
                    poll.exact.length === 0 &&
                    poll.pattern.length === 0 &&
                    (!clusterMode || poll.sharded.length === 0);

                if (isEmpty) break;

                await new Promise((resolve) => setTimeout(resolve, 100));
            }
        } catch {
            void 0;
        } finally {
            client.close();
            await new Promise((resolve) => setTimeout(resolve, 1000));
        }
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

    // Three-dimensional test cases: [cluster_mode, message_read_method, subscription_method]
    const testCasesWithSubscriptionMethod: [
        boolean,
        (typeof MethodTesting)[keyof typeof MethodTesting],
        number,
    ][] = [
        // Cluster mode combinations
        [true, MethodTesting.Async, Mode.Config],
        [true, MethodTesting.Async, Mode.Lazy],
        [true, MethodTesting.Async, Mode.Blocking],
        [true, MethodTesting.Sync, Mode.Config],
        [true, MethodTesting.Sync, Mode.Lazy],
        [true, MethodTesting.Sync, Mode.Blocking],
        [true, MethodTesting.Callback, Mode.Config],
        [true, MethodTesting.Callback, Mode.Lazy],
        [true, MethodTesting.Callback, Mode.Blocking],
        // Standalone mode combinations
        [false, MethodTesting.Async, Mode.Config],
        [false, MethodTesting.Async, Mode.Lazy],
        [false, MethodTesting.Async, Mode.Blocking],
        [false, MethodTesting.Sync, Mode.Config],
        [false, MethodTesting.Sync, Mode.Lazy],
        [false, MethodTesting.Sync, Mode.Blocking],
        [false, MethodTesting.Callback, Mode.Config],
        [false, MethodTesting.Callback, Mode.Lazy],
        [false, MethodTesting.Callback, Mode.Blocking],
    ];

    // Two-dimensional test cases for coexistence tests: [cluster_mode, subscription_method]
    const testCasesCoexistence: [boolean, number][] = [
        [true, Mode.Config],
        [true, Mode.Lazy],
        [true, Mode.Blocking],
        [false, Mode.Config],
        [false, Mode.Lazy],
        [false, Mode.Blocking],
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesWithSubscriptionMethod)(
        `pubsub exact happy path test_%p_%p_%p`,
        async (clusterMode, method, subscriptionMethod) => {
            let listeningClient: TGlideClient;
            let publishingClient: TGlideClient;

            try {
                const channel = getRandomKey() as GlideString;
                const message = getRandomKey() as GlideString;
                const options = getOptions(clusterMode);
                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    const pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: new Set([channel as string]),
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                new Set([channel as string]),
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
                } else {
                    // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        options,
                        getOptions(clusterMode),
                        createPubSubSubscription(
                            clusterMode,
                            {},
                            {},
                            callback,
                            context,
                        ),
                    );

                    // Subscribe dynamically based on subscription method
                    await subscribeByMethod(
                        listeningClient,
                        new Set([channel as string]),
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    new Set([channel as string]),
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

                expect(pubsubMessage!.message).toEqual(message);
                expect(pubsubMessage!.channel).toEqual(channel);
                expect(pubsubMessage!.pattern).toBeNull();

                await checkNoMessagesLeft(method, listeningClient, context, 1);
            } finally {
                await Promise.all([
                    clientCleanup(publishingClient!),
                    clientCleanup(
                        listeningClient!,
                        // eslint-disable-next-line @typescript-eslint/no-explicit-any
                        clusterMode ? ({} as any) : undefined,
                    ),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Tests the basic happy path for exact PUBSUB functionality with binary.
     *
     * This test covers the basic PUBSUB flow using three different methods:
     * Async, Sync, and Callback. It verifies that a message published to a
     * specific channel is correctly received by a subscriber.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesWithSubscriptionMethod)(
        `pubsub exact happy path binary test_%p_%p_%p`,
        async (clusterMode, method, subscriptionMethod) => {
            let listeningClient: TGlideClient;
            let publishingClient: TGlideClient;

            try {
                const channel = getRandomKey() as GlideString;
                const message = getRandomKey() as GlideString;
                const options = getOptions(clusterMode);
                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    const pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: new Set([channel as string]),
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                new Set([channel as string]),
                        },
                        callback,
                        context,
                    );
                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        options,
                        getOptions(clusterMode),
                        pubSub,
                        undefined,
                        Decoder.Bytes,
                    );
                } else {
                    // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        options,
                        getOptions(clusterMode),
                        createPubSubSubscription(
                            clusterMode,
                            {},
                            {},
                            callback,
                            context,
                        ),
                        undefined,
                        Decoder.Bytes,
                    );

                    // Subscribe dynamically based on subscription method
                    await subscribeByMethod(
                        listeningClient,
                        new Set([channel as string]),
                        subscriptionMethod,
                    );
                }

                // For binary tests, skip subscription state verification since Decoder.Bytes
                // affects how channels are returned from GET_SUBSCRIPTIONS.
                // Instead, just wait a bit for subscriptions to be established.
                if (subscriptionMethod === Mode.Lazy) {
                    await new Promise((resolve) => setTimeout(resolve, 1000));
                }

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

                expect(pubsubMessage!.message).toEqual(
                    Buffer.from(String(message)),
                );
                expect(pubsubMessage!.channel).toEqual(
                    Buffer.from(String(channel)),
                );
                expect(pubsubMessage!.pattern).toBeNull();

                await checkNoMessagesLeft(method, listeningClient, context, 1);
            } finally {
                await Promise.all([
                    clientCleanup(publishingClient!),
                    clientCleanup(
                        listeningClient!,
                        // eslint-disable-next-line @typescript-eslint/no-explicit-any
                        clusterMode ? ({} as any) : undefined,
                    ),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesCoexistence)(
        "pubsub exact happy path coexistence test_%p_%p",
        async (clusterMode, subscriptionMethod) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const channel = getRandomKey() as GlideString;
                const message = getRandomKey() as GlideString;
                const message2 = getRandomKey() as GlideString;

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    const pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: new Set([channel as string]),
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                new Set([channel as string]),
                        },
                    );

                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSub,
                    );
                } else {
                    // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        createPubSubSubscription(clusterMode, {}, {}),
                    );

                    // Subscribe dynamically based on subscription method
                    await subscribeByMethod(
                        listeningClient,
                        new Set([channel as string]),
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    new Set([channel as string]),
                );

                for (const msg of [message, message2]) {
                    const result = await publishingClient.publish(msg, channel);

                    if (clusterMode) {
                        expect(result).toEqual(1);
                    }
                }

                // Allow the message to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const asyncMsg = await listeningClient.getPubSubMessage();
                const syncMsg = listeningClient.tryGetPubSubMessage()!;
                expect(syncMsg).toBeTruthy();

                expect([message, message2]).toContain(asyncMsg.message);
                expect(asyncMsg.channel).toEqual(channel);
                expect(asyncMsg.pattern).toBeNull();

                expect([message, message2]).toContain(syncMsg.message);
                expect(syncMsg.channel).toEqual(channel);
                expect(syncMsg.pattern).toBeNull();

                expect(asyncMsg.message).not.toEqual(syncMsg!.message);

                // Assert there are no messages to read
                await checkNoMessagesLeft(MethodTesting.Async, listeningClient);
                expect(listeningClient.tryGetPubSubMessage()).toBeNull();
            } finally {
                await Promise.all([
                    clientCleanup(publishingClient!),
                    clientCleanup(
                        listeningClient!,
                        // eslint-disable-next-line @typescript-eslint/no-explicit-any
                        clusterMode ? ({} as any) : undefined,
                    ),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesWithSubscriptionMethod)(
        "pubsub exact happy path many channels test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";

            try {
                // Create a map of channels to random messages with shard prefix
                const channelsAndMessages: [GlideString, GlideString][] = [];

                for (let i = 0; i < NUM_CHANNELS; i++) {
                    const channel = `${shardPrefix}${getRandomKey()}`;
                    const message = getRandomKey();
                    channelsAndMessages.push([channel, message]);
                }

                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                const channelSet = new Set(
                    channelsAndMessages.map((a) => a[0].toString()),
                );

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    const pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: channelSet,
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                channelSet,
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
                } else {
                    // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        createPubSubSubscription(
                            clusterMode,
                            {},
                            {},
                            callback,
                            context,
                        ),
                    );

                    // Subscribe dynamically based on subscription method
                    await subscribeByMethod(
                        listeningClient,
                        channelSet,
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    channelSet,
                );

                // Publish messages to each channel
                for (const [channel, message] of channelsAndMessages) {
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
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    ))!;
                    expect(
                        channelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toBeNull();
                }

                // Check no messages left
                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS,
                );
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(
                              listeningClient,
                              // eslint-disable-next-line @typescript-eslint/no-explicit-any
                              clusterMode ? ({} as any) : undefined,
                          )
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesCoexistence)(
        "pubsub exact happy path many channels coexistence test_%p_%p",
        async (clusterMode, subscriptionMethod) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";

            try {
                // Create a map of channels to random messages with shard prefix
                const channelsAndMessages: [GlideString, GlideString][] = [];

                for (let i = 0; i < NUM_CHANNELS; i++) {
                    const channel = `${shardPrefix}${getRandomKey()}`;
                    const message = getRandomKey();
                    channelsAndMessages.push([channel, message]);
                }

                const channelSet = new Set(
                    channelsAndMessages.map((a) => a[0].toString()),
                );

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    const pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: channelSet,
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                channelSet,
                        },
                    );

                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSub,
                    );
                } else {
                    // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        createPubSubSubscription(clusterMode, {}, {}),
                    );

                    // Subscribe dynamically based on subscription method
                    await subscribeByMethod(
                        listeningClient,
                        channelSet,
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    channelSet,
                );

                // Publish messages to each channel
                for (const [channel, message] of channelsAndMessages) {
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
                        channelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg?.channel,
                        ),
                    ).toEqual([pubsubMsg?.channel, pubsubMsg?.message]);

                    expect(pubsubMsg?.pattern).toBeNull();
                }

                // Assert there are no messages to read
                await checkNoMessagesLeft(MethodTesting.Async, listeningClient);
                expect(listeningClient.tryGetPubSubMessage()).toBeNull();
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(
                              listeningClient,
                              // eslint-disable-next-line @typescript-eslint/no-explicit-any
                              clusterMode ? ({} as any) : undefined,
                          )
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Test sharded PUBSUB functionality with different message retrieval methods and subscription modes.
     *
     * This test covers the sharded PUBSUB flow using three different methods:
     * Async, Sync, and Callback. It verifies that a message published to a
     * specific sharded channel is correctly received by a subscriber.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     * @param subscriptionMethod - Specifies the subscription mode (Config, Lazy, Blocking).
     */
    it.each([
        [true, MethodTesting.Async, Mode.Config],
        [true, MethodTesting.Async, Mode.Lazy],
        [true, MethodTesting.Async, Mode.Blocking],
        [true, MethodTesting.Sync, Mode.Config],
        [true, MethodTesting.Sync, Mode.Lazy],
        [true, MethodTesting.Sync, Mode.Blocking],
        [true, MethodTesting.Callback, Mode.Config],
        [true, MethodTesting.Callback, Mode.Lazy],
        [true, MethodTesting.Callback, Mode.Blocking],
    ])(
        "sharded pubsub test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const channel = getRandomKey() as GlideString;
            const message = getRandomKey() as GlideString;
            const publishResponse = 1;

            try {
                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                // Create listening client based on subscription method
                if (subscriptionMethod === Mode.Config) {
                    // Config mode: create client with subscriptions configured
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        new Set([channel as string]),
                        callback,
                        context,
                        undefined,
                        undefined,
                        cmeCluster.ports().map((port) => ({
                            host: "localhost",
                            port,
                        })),
                    );
                } else {
                    // Lazy/Blocking mode: create client with callback but no initial subscriptions
                    if (method === MethodTesting.Callback) {
                        // For callback mode, we need to create a client with callback configured
                        // but without initial subscriptions
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            new Set(), // Empty set for exact channels
                            new Set(), // Empty set for patterns
                            new Set(), // Empty set for sharded channels
                            callback,
                            context,
                            undefined,
                            undefined,
                            cmeCluster.ports().map((port) => ({
                                host: "localhost",
                                port,
                            })),
                        );
                    } else {
                        // For async/sync modes, create client without subscriptions or callback
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            cmeCluster.ports().map((port) => ({
                                host: "localhost",
                                port,
                            })),
                        );
                    }

                    // Subscribe dynamically
                    await ssubscribeByMethod(
                        listeningClient as GlideClusterClient,
                        new Set([channel as string]),
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    undefined,
                    undefined,
                    new Set([channel as string]),
                );

                // Create publishing client
                publishingClient = await createPubsubClient(
                    clusterMode,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    cmeCluster.ports().map((port) => ({
                        host: "localhost",
                        port,
                    })),
                );

                const result = await (
                    publishingClient as GlideClusterClient
                ).publish(message, channel, true);

                expect(result).toEqual(publishResponse);

                // Allow the message to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const pubsubMsg = (await getMessageByMethod(
                    method,
                    listeningClient,
                    context,
                    0,
                ))!;

                expect(pubsubMsg.message).toEqual(message);
                expect(pubsubMsg.channel).toEqual(channel);
                expect(pubsubMsg.pattern).toBeNull();

                // Assert there are no messages to read
                await checkNoMessagesLeft(method, listeningClient, context, 1);
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(listeningClient)
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Test sharded PUBSUB with co-existence of multiple messages and subscription modes.
     *
     * This test verifies the behavior of sharded PUBSUB when multiple messages are published
     * to the same sharded channel. It ensures that both async and sync methods of message retrieval
     * function correctly in this scenario.
     *
     * It covers the scenario where messages are published to a sharded channel and received using
     * both async and sync methods. This ensures that the asynchronous and synchronous message
     * retrieval methods can coexist without interfering with each other and operate as expected.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param subscriptionMethod - Specifies the subscription mode (Config, Lazy, Blocking).
     */
    it.each([
        [true, Mode.Config],
        [true, Mode.Lazy],
        [true, Mode.Blocking],
    ])(
        "sharded pubsub co-existence test_%p_%p",
        async (clusterMode, subscriptionMethod) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const channel = getRandomKey() as GlideString;
            const message = getRandomKey() as GlideString;
            const message2 = getRandomKey() as GlideString;

            try {
                // Create listening client based on subscription method
                if (subscriptionMethod === Mode.Config) {
                    // Config mode: create client with subscriptions configured
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        new Set([channel as string]),
                        undefined,
                        undefined,
                        undefined,
                        undefined,
                        cmeCluster.ports().map((port) => ({
                            host: "localhost",
                            port,
                        })),
                    );
                } else {
                    // Lazy/Blocking mode: create client without subscriptions
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        undefined,
                        undefined,
                        undefined,
                        undefined,
                        undefined,
                        cmeCluster.ports().map((port) => ({
                            host: "localhost",
                            port,
                        })),
                    );

                    // Subscribe dynamically
                    await ssubscribeByMethod(
                        listeningClient as GlideClusterClient,
                        new Set([channel as string]),
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    undefined,
                    undefined,
                    new Set([channel as string]),
                );

                // Create publishing client
                publishingClient = await createPubsubClient(
                    clusterMode,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    cmeCluster.ports().map((port) => ({
                        host: "localhost",
                        port,
                    })),
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

                const asyncMsg = await listeningClient!.getPubSubMessage();
                const syncMsg = listeningClient!.tryGetPubSubMessage()!;
                expect(syncMsg).toBeTruthy();

                expect([message, message2]).toContain(asyncMsg.message);
                expect(asyncMsg.channel).toEqual(channel);
                expect(asyncMsg.pattern).toBeNull();

                expect([message, message2]).toContain(syncMsg.message);
                expect(syncMsg.channel).toEqual(channel);
                expect(syncMsg.pattern).toBeNull();

                expect(asyncMsg.message).not.toEqual(syncMsg.message);

                // Assert there are no messages to read
                await checkNoMessagesLeft(MethodTesting.Async, listeningClient);
                expect(listeningClient!.tryGetPubSubMessage()).toBeNull();
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(listeningClient)
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Test sharded PUBSUB with multiple channels, different message retrieval methods, and subscription modes.
     *
     * This test verifies the behavior of sharded PUBSUB when multiple messages are published
     * across multiple sharded channels. It covers three different message retrieval methods:
     * Async, Sync, and Callback.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
     * @param subscriptionMethod - Specifies the subscription mode (Config, Lazy, Blocking).
     */
    it.each([
        [true, MethodTesting.Async, Mode.Config],
        [true, MethodTesting.Async, Mode.Lazy],
        [true, MethodTesting.Async, Mode.Blocking],
        [true, MethodTesting.Sync, Mode.Config],
        [true, MethodTesting.Sync, Mode.Lazy],
        [true, MethodTesting.Sync, Mode.Blocking],
        [true, MethodTesting.Callback, Mode.Config],
        [true, MethodTesting.Callback, Mode.Lazy],
        [true, MethodTesting.Callback, Mode.Blocking],
    ])(
        "sharded pubsub many channels test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";
            const publishResponse = 1;

            // Create a map of channels to random messages with shard prefix
            const channelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const channel = `${shardPrefix}${getRandomKey()}`;
                const message = getRandomKey();
                channelsAndMessages.push([channel, message]);
            }

            try {
                let context: PubSubMsg[] | null = null;
                let callback;

                if (method === MethodTesting.Callback) {
                    context = [];
                    callback = newMessage;
                }

                const shardedChannels = new Set(
                    channelsAndMessages.map((a) => a[0].toString()),
                );

                // Create listening client based on subscription method
                if (subscriptionMethod === Mode.Config) {
                    // Config mode: create client with subscriptions configured
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        shardedChannels,
                        callback,
                        context,
                        undefined,
                        undefined,
                        cmeCluster.ports().map((port) => ({
                            host: "localhost",
                            port,
                        })),
                    );
                } else {
                    // Lazy/Blocking mode: create client with callback but no initial subscriptions
                    if (method === MethodTesting.Callback) {
                        // For callback mode, we need to create a client with callback configured
                        // but without initial subscriptions
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            new Set(), // Empty set for exact channels
                            new Set(), // Empty set for patterns
                            new Set(), // Empty set for sharded channels
                            callback,
                            context,
                            undefined,
                            undefined,
                            cmeCluster.ports().map((port) => ({
                                host: "localhost",
                                port,
                            })),
                        );
                    } else {
                        // For async/sync modes, create client without subscriptions or callback
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            cmeCluster.ports().map((port) => ({
                                host: "localhost",
                                port,
                            })),
                        );
                    }

                    // Subscribe dynamically
                    await ssubscribeByMethod(
                        listeningClient as GlideClusterClient,
                        shardedChannels,
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    undefined,
                    undefined,
                    shardedChannels,
                );

                // Create publishing client
                publishingClient = await createPubsubClient(
                    clusterMode,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    cmeCluster.ports().map((port) => ({
                        host: "localhost",
                        port,
                    })),
                );

                // Publish messages to each channel
                for (const [channel, message] of channelsAndMessages) {
                    const result = await (
                        publishingClient as GlideClusterClient
                    ).publish(message, channel, true);
                    expect(result).toEqual(publishResponse);
                }

                // Allow the messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Check if all messages are received correctly
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    ))!;

                    expect(
                        channelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toBeNull();
                }

                // Assert there are no more messages to read
                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS,
                );
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(listeningClient)
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesWithSubscriptionMethod)(
        "pubsub pattern test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const PATTERN = `{{channel}}:*`;
            const channels: [GlideString, GlideString][] = [
                [`{{channel}}:${getRandomKey()}`, getRandomKey()],
                [`{{channel}}:${getRandomKey()}`, getRandomKey()],
            ];

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            let context: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                context = [];
                callback = newMessage;
            }

            try {
                const options = getOptions(clusterMode);

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined, // no exact channels
                        new Set([PATTERN]), // patterns
                        undefined, // no sharded channels
                        callback,
                        context,
                        options.protocol,
                        undefined,
                        options.addresses as { host: string; port: number }[],
                    );
                } else {
                    // Lazy/Blocking mode: create client with callback but no initial subscriptions
                    if (method === MethodTesting.Callback) {
                        // For callback mode, we need to create a client with callback configured
                        // but without initial subscriptions
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            new Set(), // Empty set for exact channels
                            new Set(), // Empty set for patterns
                            new Set(), // Empty set for sharded channels
                            callback,
                            context,
                            options.protocol,
                            undefined,
                            options.addresses as {
                                host: string;
                                port: number;
                            }[],
                        );
                    } else {
                        // For async/sync modes, create client without subscriptions or callback
                        listeningClient = clusterMode
                            ? await GlideClusterClient.createClient(options)
                            : await GlideClient.createClient(options);
                    }

                    // Subscribe dynamically
                    await psubscribeByMethod(
                        listeningClient,
                        new Set([PATTERN]),
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    undefined, // no exact channels
                    new Set([PATTERN]), // patterns
                    undefined, // no sharded channels
                );

                // Create publishing client
                publishingClient = clusterMode
                    ? await GlideClusterClient.createClient(options)
                    : await GlideClient.createClient(options);

                // Publish messages to each channel
                for (const [channel, message] of channels) {
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
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    ))!;
                    expect(
                        channels.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                }

                // Assert there are no more messages to read
                await checkNoMessagesLeft(method, listeningClient, context, 2);
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(listeningClient)
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesCoexistence)(
        "pubsub pattern coexistence test_%p_%p",
        async (clusterMode, subscriptionMethod) => {
            const PATTERN = `{{channel}}:*`;
            const channels: [GlideString, GlideString][] = [
                [`{{channel}}:${getRandomKey()}`, getRandomKey()],
                [`{{channel}}:${getRandomKey()}`, getRandomKey()],
            ];

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const options = getOptions(clusterMode);

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined, // no exact channels
                        new Set([PATTERN]), // patterns
                        undefined, // no sharded channels
                        undefined, // no callback
                        undefined, // no context
                        options.protocol,
                        undefined,
                        options.addresses as { host: string; port: number }[],
                    );
                } else {
                    // For Lazy/Blocking modes, create client without subscriptions
                    listeningClient = clusterMode
                        ? await GlideClusterClient.createClient(options)
                        : await GlideClient.createClient(options);

                    // Subscribe dynamically
                    await psubscribeByMethod(
                        listeningClient,
                        new Set([PATTERN]),
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    undefined, // no exact channels
                    new Set([PATTERN]), // patterns
                    undefined, // no sharded channels
                );

                // Create publishing client
                publishingClient = clusterMode
                    ? await GlideClusterClient.createClient(options)
                    : await GlideClient.createClient(options);

                // Publish messages to each channel
                for (const [channel, message] of channels) {
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
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClient,
                    ))!;

                    expect(
                        channels.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                }

                // Assert there are no more messages to read
                await checkNoMessagesLeft(MethodTesting.Async, listeningClient);
                expect(listeningClient.tryGetPubSubMessage()).toBeNull();
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(listeningClient)
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesWithSubscriptionMethod)(
        "pubsub pattern many channels test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const NUM_CHANNELS = 256;
            const PATTERN = "{{channel}}:*";
            const channels: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const channel = `{{channel}}:${getRandomKey()}`;
                const message = getRandomKey();
                channels.push([channel, message]);
            }

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            let context: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                context = [];
                callback = newMessage;
            }

            try {
                const options = getOptions(clusterMode);

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined, // no exact channels
                        new Set([PATTERN]), // patterns
                        undefined, // no sharded channels
                        callback,
                        context,
                        options.protocol,
                        undefined,
                        options.addresses as { host: string; port: number }[],
                    );
                } else {
                    // Lazy/Blocking mode: create client with callback but no initial subscriptions
                    if (method === MethodTesting.Callback) {
                        // For callback mode, we need to create a client with callback configured
                        // but without initial subscriptions
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            new Set(), // Empty set for exact channels
                            new Set(), // Empty set for patterns
                            new Set(), // Empty set for sharded channels
                            callback,
                            context,
                            options.protocol,
                            undefined,
                            options.addresses as {
                                host: string;
                                port: number;
                            }[],
                        );
                    } else {
                        // For async/sync modes, create client without subscriptions or callback
                        listeningClient = clusterMode
                            ? await GlideClusterClient.createClient(options)
                            : await GlideClient.createClient(options);
                    }

                    // Subscribe dynamically
                    await psubscribeByMethod(
                        listeningClient,
                        new Set([PATTERN]),
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    undefined, // no exact channels
                    new Set([PATTERN]), // patterns
                    undefined, // no sharded channels
                );

                // Create publishing client
                publishingClient = clusterMode
                    ? await GlideClusterClient.createClient(options)
                    : await GlideClient.createClient(options);

                // Publish messages to each channel
                for (const [channel, message] of channels) {
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
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    ))!;

                    expect(
                        channels.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                }

                // Assert there are no more messages to read
                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS,
                );
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(listeningClient)
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesWithSubscriptionMethod)(
        "pubsub combined exact and pattern test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: [GlideString, GlideString][] = [];
            const patternChannelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${getRandomKey()}`;
                const patternChannel = `{{pattern}}:${getRandomKey()}`;
                const exactMessage = getRandomKey();
                const patternMessage = getRandomKey();

                exactChannelsAndMessages.push([exactChannel, exactMessage]);
                patternChannelsAndMessages.push([
                    patternChannel,
                    patternMessage,
                ]);
            }

            const allChannelsAndMessages: [GlideString, GlideString][] = [
                ...exactChannelsAndMessages,
                ...patternChannelsAndMessages,
            ];

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            let context: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                context = [];
                callback = newMessage;
            }

            try {
                const exactChannelsSet = new Set(
                    exactChannelsAndMessages.map((a) => a[0].toString()),
                );
                const patternSet = new Set([PATTERN]);

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    const pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: exactChannelsSet,
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Pattern]: patternSet,
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                exactChannelsSet,
                            [GlideClientConfiguration.PubSubChannelModes
                                .Pattern]: patternSet,
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
                } else {
                    // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        createPubSubSubscription(
                            clusterMode,
                            {},
                            {},
                            callback,
                            context,
                        ),
                    );

                    // Subscribe dynamically based on subscription method
                    await subscribeByMethod(
                        listeningClient,
                        exactChannelsSet,
                        subscriptionMethod,
                    );
                    await psubscribeByMethod(
                        listeningClient,
                        patternSet,
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    exactChannelsSet,
                    patternSet,
                );

                // Publish messages to all channels
                for (const [channel, message] of allChannelsAndMessages) {
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

                const length = allChannelsAndMessages.length;

                // Check if all messages are received correctly
                for (let index = 0; index < length; index++) {
                    const pubsubMsg: PubSubMsg = (await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    ))!;
                    const pattern = patternChannelsAndMessages.find(
                        ([channel]) => channel === pubsubMsg.channel,
                    )
                        ? PATTERN
                        : null;
                    expect(
                        allChannelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toEqual(pattern);
                }

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS * 2,
                );
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(
                              listeningClient,
                              // eslint-disable-next-line @typescript-eslint/no-explicit-any
                              clusterMode ? ({} as any) : undefined,
                          )
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each(testCasesWithSubscriptionMethod)(
        "pubsub combined exact and pattern multiple clients test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: [GlideString, GlideString][] = [];
            const patternChannelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${getRandomKey()}`;
                const patternChannel = `{{pattern}}:${getRandomKey()}`;
                const exactMessage = getRandomKey();
                const patternMessage = getRandomKey();

                exactChannelsAndMessages.push([exactChannel, exactMessage]);
                patternChannelsAndMessages.push([
                    patternChannel,
                    patternMessage,
                ]);
            }

            const allChannelsAndMessages: [GlideString, GlideString][] = [
                ...exactChannelsAndMessages,
                ...patternChannelsAndMessages,
            ];

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
                const exactChannelsSet = new Set(
                    exactChannelsAndMessages.map((a) => a[0].toString()),
                );
                const patternSet = new Set([PATTERN]);

                // Create listening client for exact channels
                if (subscriptionMethod === Mode.Config) {
                    // Config mode: create client with subscriptions
                    const pubSubExact = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: exactChannelsSet,
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes.Exact]:
                                exactChannelsSet,
                        },
                        callback,
                        contextExact,
                    );

                    [listeningClientExact, publishingClient] =
                        await createClients(
                            clusterMode,
                            getOptions(clusterMode),
                            getOptions(clusterMode),
                            pubSubExact,
                        );
                } else {
                    // Lazy/Blocking mode: create client without subscriptions, then subscribe dynamically
                    [listeningClientExact, publishingClient] =
                        await createClients(
                            clusterMode,
                            getOptions(clusterMode),
                            getOptions(clusterMode),
                            createPubSubSubscription(
                                clusterMode,
                                {},
                                {},
                                callback,
                                contextExact,
                            ),
                        );

                    // Subscribe dynamically based on subscription method
                    await subscribeByMethod(
                        listeningClientExact,
                        exactChannelsSet,
                        subscriptionMethod,
                    );
                }

                // Create listening client for pattern channels
                if (subscriptionMethod === Mode.Config) {
                    // Config mode: create client with subscriptions
                    const pubSubPattern = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Pattern]: patternSet,
                        },
                        {
                            [GlideClientConfiguration.PubSubChannelModes
                                .Pattern]: patternSet,
                        },
                        callback,
                        contextPattern,
                    );

                    [listeningClientPattern, clientDontCare] =
                        await createClients(
                            clusterMode,
                            getOptions(clusterMode),
                            getOptions(clusterMode),
                            pubSubPattern,
                        );
                } else {
                    // Lazy/Blocking mode: create client without subscriptions, then subscribe dynamically
                    [listeningClientPattern, clientDontCare] =
                        await createClients(
                            clusterMode,
                            getOptions(clusterMode),
                            getOptions(clusterMode),
                            createPubSubSubscription(
                                clusterMode,
                                {},
                                {},
                                callback,
                                contextPattern,
                            ),
                        );

                    // Subscribe dynamically based on subscription method
                    await psubscribeByMethod(
                        listeningClientPattern,
                        patternSet,
                        subscriptionMethod,
                    );
                }

                // Wait for subscriptions to be established
                await waitForSubscriptionStateIfNeeded(
                    listeningClientExact,
                    subscriptionMethod,
                    exactChannelsSet,
                );
                await waitForSubscriptionStateIfNeeded(
                    listeningClientPattern,
                    subscriptionMethod,
                    undefined,
                    patternSet,
                );

                // Publish messages to all channels
                for (const [channel, message] of allChannelsAndMessages) {
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
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClientExact,
                        contextExact,
                        index,
                    ))!;
                    expect(
                        exactChannelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toBeNull();
                }

                length = patternChannelsAndMessages.length;

                // Verify messages for pattern PUBSUB
                for (let index = 0; index < length; index++) {
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClientPattern,
                        contextPattern,
                        index,
                    ))!;

                    expect(
                        patternChannelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                }

                // Assert no messages are left unread
                await Promise.all([
                    checkNoMessagesLeft(
                        method,
                        listeningClientExact,
                        contextExact,
                        NUM_CHANNELS,
                    ),
                    checkNoMessagesLeft(
                        method,
                        listeningClientPattern,
                        contextPattern,
                        NUM_CHANNELS,
                    ),
                ]);
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClientExact
                        ? clientCleanup(listeningClientExact)
                        : Promise.resolve(),
                    listeningClientPattern
                        ? clientCleanup(listeningClientPattern)
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                    clientDontCare
                        ? clientCleanup(clientDontCare)
                        : Promise.resolve(),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each([
        [true, MethodTesting.Async, Mode.Config],
        [true, MethodTesting.Async, Mode.Lazy],
        [true, MethodTesting.Async, Mode.Blocking],
        [true, MethodTesting.Sync, Mode.Config],
        [true, MethodTesting.Sync, Mode.Lazy],
        [true, MethodTesting.Sync, Mode.Blocking],
        [true, MethodTesting.Callback, Mode.Config],
        [true, MethodTesting.Callback, Mode.Lazy],
        [true, MethodTesting.Callback, Mode.Blocking],
    ])(
        "pubsub combined exact, pattern, and sharded test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";
            const SHARD_PREFIX = "{same-shard}";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: [GlideString, GlideString][] = [];
            const patternChannelsAndMessages: [GlideString, GlideString][] = [];
            const shardedChannelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${getRandomKey()}`;
                const patternChannel = `{{pattern}}:${getRandomKey()}`;
                const shardedChannel = `${SHARD_PREFIX}:${getRandomKey()}`;
                exactChannelsAndMessages.push([exactChannel, getRandomKey()]);
                patternChannelsAndMessages.push([
                    patternChannel,
                    getRandomKey(),
                ]);
                shardedChannelsAndMessages.push([
                    shardedChannel,
                    getRandomKey(),
                ]);
            }

            const publishResponse = 1;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            let context: PubSubMsg[] | null = null;
            let callback;

            if (method === MethodTesting.Callback) {
                context = [];
                callback = newMessage;
            }

            try {
                const exactChannelsSet = new Set(
                    exactChannelsAndMessages.map((a) => a[0].toString()),
                );
                const patternSet = new Set([PATTERN]);
                const shardedChannelsSet = new Set(
                    shardedChannelsAndMessages.map((a) => a[0].toString()),
                );

                // For Config mode, create client with subscriptions at creation time
                if (subscriptionMethod === Mode.Config) {
                    const pubSub = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: exactChannelsSet,
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Pattern]: patternSet,
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Sharded]: shardedChannelsSet,
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
                } else {
                    // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                    [listeningClient, publishingClient] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        createPubSubSubscription(
                            clusterMode,
                            {},
                            {},
                            callback,
                            context,
                        ),
                    );

                    // Subscribe dynamically based on subscription method
                    await subscribeByMethod(
                        listeningClient,
                        exactChannelsSet,
                        subscriptionMethod,
                    );
                    await psubscribeByMethod(
                        listeningClient,
                        patternSet,
                        subscriptionMethod,
                    );
                    await ssubscribeByMethod(
                        listeningClient as GlideClusterClient,
                        shardedChannelsSet,
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    exactChannelsSet,
                    patternSet,
                    shardedChannelsSet,
                );

                // Publish messages to exact and pattern channels
                for (const [channel, message] of [
                    ...exactChannelsAndMessages,
                    ...patternChannelsAndMessages,
                ]) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );
                    expect(result).toEqual(publishResponse);
                }

                // Publish sharded messages
                for (const [channel, message] of shardedChannelsAndMessages) {
                    const result = await (
                        publishingClient as GlideClusterClient
                    ).publish(message, channel, true);
                    expect(result).toEqual(publishResponse);
                }

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const allChannelsAndMessages: [GlideString, GlideString][] = [
                    ...exactChannelsAndMessages,
                    ...patternChannelsAndMessages,
                    ...shardedChannelsAndMessages,
                ];

                // Check if all messages are received correctly
                for (let index = 0; index < NUM_CHANNELS * 3; index++) {
                    const pubsubMsg: PubSubMsg = (await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        index,
                    ))!;
                    const pattern = patternChannelsAndMessages.find(
                        ([channel]) => channel === pubsubMsg.channel,
                    )
                        ? PATTERN
                        : null;
                    expect(
                        allChannelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toEqual(pattern);
                }

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    context,
                    NUM_CHANNELS * 3,
                );
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClient
                        ? clientCleanup(
                              listeningClient,
                              // eslint-disable-next-line @typescript-eslint/no-explicit-any
                              clusterMode ? ({} as any) : undefined,
                          )
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                ]);
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
     * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
     */
    it.each([
        [true, MethodTesting.Async, Mode.Config],
        [true, MethodTesting.Async, Mode.Lazy],
        [true, MethodTesting.Async, Mode.Blocking],
        [true, MethodTesting.Sync, Mode.Config],
        [true, MethodTesting.Sync, Mode.Lazy],
        [true, MethodTesting.Sync, Mode.Blocking],
        [true, MethodTesting.Callback, Mode.Config],
        [true, MethodTesting.Callback, Mode.Lazy],
        [true, MethodTesting.Callback, Mode.Blocking],
    ])(
        "pubsub combined exact, pattern, and sharded multi-client test_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) return;

            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";
            const SHARD_PREFIX = "{same-shard}";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: [GlideString, GlideString][] = [];
            const patternChannelsAndMessages: [GlideString, GlideString][] = [];
            const shardedChannelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${getRandomKey()}`;
                const patternChannel = `{{pattern}}:${getRandomKey()}`;
                const shardedChannel = `${SHARD_PREFIX}:${getRandomKey()}`;
                exactChannelsAndMessages.push([exactChannel, getRandomKey()]);
                patternChannelsAndMessages.push([
                    patternChannel,
                    getRandomKey(),
                ]);
                shardedChannelsAndMessages.push([
                    shardedChannel,
                    getRandomKey(),
                ]);
            }

            const publishResponse = 1;
            let listeningClientExact: TGlideClient | null = null;
            let listeningClientPattern: TGlideClient | null = null;
            let listeningClientSharded: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            let callback;

            const callbackMessagesExact: PubSubMsg[] = [];
            const callbackMessagesPattern: PubSubMsg[] = [];
            const callbackMessagesSharded: PubSubMsg[] = [];

            if (method === MethodTesting.Callback) {
                callback = newMessage;
            }

            try {
                const exactChannelsSet = new Set(
                    exactChannelsAndMessages.map((a) => a[0].toString()),
                );
                const patternSet = new Set([PATTERN]);
                const shardedChannelsSet = new Set(
                    shardedChannelsAndMessages.map((a) => a[0].toString()),
                );

                // Create listening client for exact channels
                if (subscriptionMethod === Mode.Config) {
                    const pubSubExact = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Exact]: exactChannelsSet,
                        },
                        {},
                        callback,
                        callback ? callbackMessagesExact : undefined,
                    );

                    [listeningClientExact, publishingClient] =
                        await createClients(
                            clusterMode,
                            getOptions(clusterMode),
                            getOptions(clusterMode),
                            pubSubExact,
                        );
                } else {
                    [listeningClientExact, publishingClient] =
                        await createClients(
                            clusterMode,
                            getOptions(clusterMode),
                            getOptions(clusterMode),
                            createPubSubSubscription(
                                clusterMode,
                                {},
                                {},
                                callback,
                                callback ? callbackMessagesExact : undefined,
                            ),
                        );

                    await subscribeByMethod(
                        listeningClientExact,
                        exactChannelsSet,
                        subscriptionMethod,
                    );
                }

                // Create listening client for pattern channels
                if (subscriptionMethod === Mode.Config) {
                    const pubSubPattern = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Pattern]: patternSet,
                        },
                        {},
                        callback,
                        callback ? callbackMessagesPattern : undefined,
                    );

                    [listeningClientPattern, listeningClientSharded] =
                        await createClients(
                            clusterMode,
                            getOptions(clusterMode),
                            getOptions(clusterMode),
                            pubSubPattern,
                        );
                } else {
                    [listeningClientPattern] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        createPubSubSubscription(
                            clusterMode,
                            {},
                            {},
                            callback,
                            callback ? callbackMessagesPattern : undefined,
                        ),
                    );

                    await psubscribeByMethod(
                        listeningClientPattern,
                        patternSet,
                        subscriptionMethod,
                    );
                }

                // Create listening client for sharded channels
                if (subscriptionMethod === Mode.Config) {
                    const pubSubSharded = createPubSubSubscription(
                        clusterMode,
                        {
                            [GlideClusterClientConfiguration.PubSubChannelModes
                                .Sharded]: shardedChannelsSet,
                        },
                        {},
                        callback,
                        callback ? callbackMessagesSharded : undefined,
                    );

                    // Replace listeningClientSharded with a new client
                    if (listeningClientSharded) {
                        listeningClientSharded.close();
                    }

                    [listeningClientSharded] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        pubSubSharded,
                    );
                } else {
                    // Create a separate client for sharded channels with its own callback context
                    [listeningClientSharded] = await createClients(
                        clusterMode,
                        getOptions(clusterMode),
                        getOptions(clusterMode),
                        createPubSubSubscription(
                            clusterMode,
                            {},
                            {},
                            callback,
                            callback ? callbackMessagesSharded : undefined,
                        ),
                    );
                    await ssubscribeByMethod(
                        listeningClientSharded as GlideClusterClient,
                        shardedChannelsSet,
                        subscriptionMethod,
                    );
                }

                // Verify subscriptions are established
                await waitForSubscriptionStateIfNeeded(
                    listeningClientExact,
                    subscriptionMethod,
                    exactChannelsSet,
                );
                await waitForSubscriptionStateIfNeeded(
                    listeningClientPattern,
                    subscriptionMethod,
                    undefined,
                    patternSet,
                );
                await waitForSubscriptionStateIfNeeded(
                    listeningClientSharded,
                    subscriptionMethod,
                    undefined,
                    undefined,
                    shardedChannelsSet,
                );

                // Publish messages to exact and pattern channels
                for (const [channel, message] of [
                    ...exactChannelsAndMessages,
                    ...patternChannelsAndMessages,
                ]) {
                    const result = await publishingClient.publish(
                        message,
                        channel,
                    );
                    expect(result).toEqual(publishResponse);
                }

                // Publish sharded messages to all channels
                for (const [channel, message] of shardedChannelsAndMessages) {
                    const result = await (
                        publishingClient as GlideClusterClient
                    ).publish(message, channel, true);
                    expect(result).toEqual(publishResponse);
                }

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Verify messages for exact PUBSUB
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClientExact,
                        callbackMessagesExact,
                        index,
                    ))!;
                    expect(
                        exactChannelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toBeNull();
                }

                // Verify messages for pattern PUBSUB
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClientPattern,
                        callbackMessagesPattern,
                        index,
                    ))!;

                    expect(
                        patternChannelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toEqual(PATTERN);
                }

                // Verify messages for sharded PUBSUB
                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        listeningClientSharded,
                        callbackMessagesSharded,
                        index,
                    ))!;

                    expect(
                        shardedChannelsAndMessages.find(
                            ([channel]) => channel === pubsubMsg.channel,
                        ),
                    ).toEqual([pubsubMsg.channel, pubsubMsg.message]);

                    expect(pubsubMsg.pattern).toBeNull();
                }

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
                await Promise.all([
                    listeningClientExact
                        ? clientCleanup(listeningClientExact)
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                    listeningClientPattern
                        ? clientCleanup(listeningClientPattern)
                        : Promise.resolve(),
                    listeningClientSharded
                        ? clientCleanup(listeningClientSharded)
                        : Promise.resolve(),
                ]);
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
            const MESSAGE_EXACT = getRandomKey();
            const MESSAGE_PATTERN = getRandomKey();
            const MESSAGE_SHARDED = getRandomKey();

            let listeningClientExact: TGlideClient | null = null;
            let listeningClientPattern: TGlideClient | null = null;
            let listeningClientSharded: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            let pubSubExact:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubSharded:
                | GlideClusterClientConfiguration.PubSubSubscriptions
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([CHANNEL_NAME]),
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([CHANNEL_NAME]),
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([CHANNEL_NAME]),
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
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        client,
                        callback,
                        0,
                    ))!;
                    const pubsubMsg2 = (await getMessageByMethod(
                        method,
                        client,
                        callback,
                        1,
                    ))!;

                    expect(pubsubMsg.message).not.toEqual(pubsubMsg2!.message);
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
                const pubsubMsgSharded = (await getMessageByMethod(
                    method,
                    listeningClientSharded,
                    callbackMessagesSharded,
                    0,
                ))!;
                expect(pubsubMsgSharded.message).toEqual(MESSAGE_SHARDED);
                expect(pubsubMsgSharded.channel).toEqual(CHANNEL_NAME);
                expect(pubsubMsgSharded.pattern).toBeNull();

                await Promise.all([
                    checkNoMessagesLeft(
                        method,
                        listeningClientExact,
                        callbackMessagesExact,
                        2,
                    ),
                    checkNoMessagesLeft(
                        method,
                        listeningClientPattern,
                        callbackMessagesPattern,
                        2,
                    ),
                    checkNoMessagesLeft(
                        method,
                        listeningClientSharded,
                        callbackMessagesSharded,
                        1,
                    ),
                ]);
            } finally {
                // Cleanup clients
                await Promise.all([
                    listeningClientExact
                        ? clientCleanup(
                              listeningClientExact,
                              clusterMode ? pubSubExact! : undefined,
                          )
                        : Promise.resolve(),
                    publishingClient
                        ? clientCleanup(publishingClient)
                        : Promise.resolve(),
                    listeningClientPattern
                        ? clientCleanup(
                              listeningClientPattern,
                              clusterMode ? pubSubPattern! : undefined,
                          )
                        : Promise.resolve(),
                    listeningClientSharded
                        ? clientCleanup(
                              listeningClientSharded,
                              clusterMode ? pubSubSharded! : undefined,
                          )
                        : Promise.resolve(),
                ]);
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
            const MESSAGE_EXACT = getRandomKey();
            const MESSAGE_PATTERN = getRandomKey();

            let clientExact: TGlideClient | null = null;
            let clientPattern: TGlideClient | null = null;

            let pubSubExact:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | GlideClusterClientConfiguration.PubSubSubscriptions
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([CHANNEL_NAME]),
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([CHANNEL_NAME]),
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
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        client,
                        callback,
                        0,
                    ))!;
                    const pubsubMsg2 = (await getMessageByMethod(
                        method,
                        client,
                        callback,
                        1,
                    ))!;

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

                await Promise.all([
                    checkNoMessagesLeft(
                        method,
                        clientPattern,
                        callbackMessagesPattern,
                        2,
                    ),
                    checkNoMessagesLeft(
                        method,
                        clientExact,
                        callbackMessagesExact,
                        2,
                    ),
                ]);
            } finally {
                // Cleanup clients
                await Promise.all([
                    clientExact
                        ? clientCleanup(
                              clientExact,
                              clusterMode ? pubSubExact! : undefined,
                          )
                        : Promise.resolve(),
                    clientPattern
                        ? clientCleanup(
                              clientPattern,
                              clusterMode ? pubSubPattern! : undefined,
                          )
                        : Promise.resolve(),
                ]);
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
            const MESSAGE_EXACT = getRandomKey();
            const MESSAGE_PATTERN = getRandomKey();
            const MESSAGE_SHARDED = getRandomKey();

            let clientExact: TGlideClient | null = null;
            let clientPattern: TGlideClient | null = null;
            let clientSharded: TGlideClient | null = null;
            let clientDontCare: TGlideClient | null = null;

            let pubSubExact:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubSharded:
                | GlideClusterClientConfiguration.PubSubSubscriptions
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([CHANNEL_NAME]),
                    },
                    {},
                    callback,
                    contextExact,
                );

                // Setup PUBSUB for pattern channels
                pubSubPattern = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([CHANNEL_NAME]),
                    },
                    {},
                    callback,
                    contextPattern,
                );

                // Setup PUBSUB for sharded channels
                pubSubSharded = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([CHANNEL_NAME]),
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
                    const pubsubMsg = (await getMessageByMethod(
                        method,
                        client,
                        callback,
                        0,
                    ))!;
                    const pubsubMsg2 = (await getMessageByMethod(
                        method,
                        client,
                        callback,
                        1,
                    ))!;

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

                const shardedMsg = (await getMessageByMethod(
                    method,
                    clientSharded,
                    callbackMessagesSharded,
                    0,
                ))!;

                expect(shardedMsg.message).toEqual(MESSAGE_SHARDED);
                expect(shardedMsg.channel).toEqual(CHANNEL_NAME);
                expect(shardedMsg.pattern).toBeNull();

                await Promise.all([
                    checkNoMessagesLeft(
                        method,
                        clientPattern,
                        callbackMessagesPattern,
                        2,
                    ),
                    checkNoMessagesLeft(
                        method,
                        clientExact,
                        callbackMessagesExact,
                        2,
                    ),
                    checkNoMessagesLeft(
                        method,
                        clientSharded,
                        callbackMessagesSharded,
                        1,
                    ),
                ]);
            } finally {
                // Cleanup clients
                await Promise.all([
                    clientExact
                        ? clientCleanup(
                              clientExact,
                              clusterMode ? pubSubExact! : undefined,
                          )
                        : Promise.resolve(),
                    clientPattern
                        ? clientCleanup(
                              clientPattern,
                              clusterMode ? pubSubPattern! : undefined,
                          )
                        : Promise.resolve(),
                    clientSharded
                        ? clientCleanup(
                              clientSharded,
                              clusterMode ? pubSubSharded! : undefined,
                          )
                        : Promise.resolve(),
                    clientDontCare
                        ? clientCleanup(clientDontCare)
                        : Promise.resolve(),
                ]);
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
         * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
         * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
         */
        it.each(testCasesWithSubscriptionMethod)(
            "test pubsub exact max size message_%p_%p_%p",
            async (clusterMode, method, subscriptionMethod) => {
                let listeningClient: TGlideClient | undefined;
                let publishingClient: TGlideClient | undefined;

                const channel = getRandomKey();

                const message = generateLargeMessage("1", 512 * 1024 * 1024); // 512MB message
                const message2 = generateLargeMessage("2", 512 * 1024 * 10);

                try {
                    let context: PubSubMsg[] | null = null;
                    let callback;

                    if (method === MethodTesting.Callback) {
                        context = [];
                        callback = newMessage;
                    }

                    // For Config mode, create client with subscriptions at creation time
                    if (subscriptionMethod === Mode.Config) {
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            new Set([channel]),
                            undefined,
                            undefined,
                            callback,
                            context,
                        );
                        publishingClient =
                            await createPubsubClient(clusterMode);
                    } else {
                        // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            undefined,
                            undefined,
                            undefined,
                            callback,
                            context,
                        );
                        publishingClient =
                            await createPubsubClient(clusterMode);

                        // Subscribe dynamically based on subscription method
                        await subscribeByMethod(
                            listeningClient,
                            new Set([channel]),
                            subscriptionMethod,
                        );
                    }

                    // Verify subscriptions are established
                    await waitForSubscriptionStateIfNeeded(
                        listeningClient,
                        subscriptionMethod,
                        new Set([channel]),
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

                    const firstMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        0,
                    );
                    expect(firstMsg!.message).toEqual(Buffer.from(message));
                    expect(firstMsg!.channel).toEqual(Buffer.from(channel));
                    expect(firstMsg!.pattern).toBeNull();

                    const secondMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        1,
                    );
                    expect(secondMsg).not.toBeNull();
                    expect(secondMsg!.message).toEqual(Buffer.from(message2));
                    expect(secondMsg!.channel).toEqual(Buffer.from(channel));
                    expect(secondMsg!.pattern).toBeNull();

                    // Assert there are no messages to read
                    await checkNoMessagesLeft(
                        method,
                        listeningClient,
                        context,
                        2,
                    );
                } finally {
                    await Promise.all([
                        clientCleanup(publishingClient!),
                        clientCleanup(
                            listeningClient!,
                            // eslint-disable-next-line @typescript-eslint/no-explicit-any
                            clusterMode ? ({} as any) : undefined,
                        ),
                    ]);
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
         * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
         * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
         */
        it.each(testCasesWithSubscriptionMethod)(
            "test pubsub sharded max size message_%p_%p_%p",
            async (clusterMode, method, subscriptionMethod) => {
                if (!clusterMode) return; // Sharded PubSub is cluster-only
                if (cmeCluster.checkIfServerVersionLessThan("7.0.0")) return;

                let listeningClient: TGlideClient | undefined;
                let publishingClient: TGlideClient | undefined;
                const channel = getRandomKey();

                const message = generateLargeMessage("1", 512 * 1024 * 1024); // 512MB message
                const message2 = generateLargeMessage("2", 512 * 1024 * 1024); // 512MB message

                try {
                    let context: PubSubMsg[] | null = null;
                    let callback;

                    if (method === MethodTesting.Callback) {
                        context = [];
                        callback = newMessage;
                    }

                    // For Config mode, create client with subscriptions at creation time
                    if (subscriptionMethod === Mode.Config) {
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            undefined,
                            undefined,
                            new Set([channel]),
                            callback,
                            context,
                        );
                        publishingClient =
                            await createPubsubClient(clusterMode);
                    } else {
                        // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            undefined,
                            undefined,
                            undefined,
                            callback,
                            context,
                        );
                        publishingClient =
                            await createPubsubClient(clusterMode);

                        // Subscribe dynamically based on subscription method
                        await ssubscribeByMethod(
                            listeningClient as GlideClusterClient,
                            new Set([channel]),
                            subscriptionMethod,
                        );
                    }

                    // Verify subscriptions are established
                    await waitForSubscriptionStateIfNeeded(
                        listeningClient,
                        subscriptionMethod,
                        undefined,
                        undefined,
                        new Set([channel]),
                    );

                    expect(
                        await (publishingClient as GlideClusterClient).publish(
                            Buffer.from(message),
                            channel,
                            true,
                        ),
                    ).toEqual(1);

                    expect(
                        await (publishingClient as GlideClusterClient).publish(
                            message2,
                            Buffer.from(channel),
                            true,
                        ),
                    ).toEqual(1);

                    // Allow the message to propagate
                    await new Promise((resolve) => setTimeout(resolve, 15000));

                    const firstMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        0,
                    );
                    const secondMsg = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        1,
                    );
                    expect(secondMsg).not.toBeNull();

                    expect(firstMsg!.message).toEqual(Buffer.from(message));
                    expect(firstMsg!.channel).toEqual(Buffer.from(channel));
                    expect(firstMsg!.pattern).toBeNull();

                    expect(secondMsg!.message).toEqual(Buffer.from(message2));
                    expect(secondMsg!.channel).toEqual(Buffer.from(channel));
                    expect(secondMsg!.pattern).toBeNull();

                    // Assert there are no messages to read
                    await checkNoMessagesLeft(
                        method,
                        listeningClient,
                        context,
                        2,
                    );
                } finally {
                    await Promise.all([
                        clientCleanup(publishingClient!),
                        clientCleanup(
                            listeningClient!,
                            // eslint-disable-next-line @typescript-eslint/no-explicit-any
                            clusterMode ? ({} as any) : undefined,
                        ),
                    ]);
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
         * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
         * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
         */
        it.each(testCasesWithSubscriptionMethod)(
            "test pubsub exact max size message callback_%p_%p_%p",
            async (clusterMode, method, subscriptionMethod) => {
                let listeningClient: TGlideClient | undefined;
                let publishingClient: TGlideClient | undefined;
                const channel = getRandomKey();

                const message = generateLargeMessage("0", 12 * 1024 * 1024); // 12MB message

                try {
                    let context: PubSubMsg[] | null = null;
                    let callback;

                    if (method === MethodTesting.Callback) {
                        context = [];
                        callback = newMessage;
                    }

                    // For Config mode, create client with subscriptions at creation time
                    if (subscriptionMethod === Mode.Config) {
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            new Set([channel]),
                            undefined,
                            undefined,
                            callback,
                            context,
                        );
                        publishingClient =
                            await createPubsubClient(clusterMode);
                    } else {
                        // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            undefined,
                            undefined,
                            undefined,
                            callback,
                            context,
                        );
                        publishingClient =
                            await createPubsubClient(clusterMode);

                        // Subscribe dynamically based on subscription method
                        await subscribeByMethod(
                            listeningClient,
                            new Set([channel]),
                            subscriptionMethod,
                        );
                    }

                    // Verify subscriptions are established
                    await waitForSubscriptionStateIfNeeded(
                        listeningClient,
                        subscriptionMethod,
                        new Set([channel]),
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

                    const pubsubMessage = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        0,
                    );

                    expect(pubsubMessage!.message).toEqual(
                        Buffer.from(message),
                    );
                    expect(pubsubMessage!.channel).toEqual(
                        Buffer.from(channel),
                    );
                    expect(pubsubMessage!.pattern).toBeNull();

                    // Assert no messages left
                    await checkNoMessagesLeft(
                        method,
                        listeningClient,
                        context,
                        1,
                    );
                } finally {
                    await Promise.all([
                        clientCleanup(publishingClient!),
                        clientCleanup(
                            listeningClient!,
                            // eslint-disable-next-line @typescript-eslint/no-explicit-any
                            clusterMode ? ({} as any) : undefined,
                        ),
                    ]);
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
         * @param method - Specifies the method of PUBSUB subscription (Async, Sync, Callback).
         * @param subscriptionMethod - Specifies the subscription method (Config, Lazy, Blocking).
         */
        it.each(testCasesWithSubscriptionMethod)(
            "test pubsub sharded max size message callback_%p_%p_%p",
            async (clusterMode, method, subscriptionMethod) => {
                if (!clusterMode) return; // Sharded PubSub is cluster-only
                if (cmeCluster.checkIfServerVersionLessThan("7.0.0")) return;

                let listeningClient: TGlideClient | undefined;
                let publishingClient: TGlideClient | undefined;
                const channel = getRandomKey();

                const message = generateLargeMessage("0", 512 * 1024 * 1024); // 512MB message

                try {
                    let context: PubSubMsg[] | null = null;
                    let callback;

                    if (method === MethodTesting.Callback) {
                        context = [];
                        callback = newMessage;
                    }

                    // For Config mode, create client with subscriptions at creation time
                    if (subscriptionMethod === Mode.Config) {
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            undefined,
                            undefined,
                            new Set([channel]),
                            callback,
                            context,
                        );
                        publishingClient =
                            await createPubsubClient(clusterMode);
                    } else {
                        // For Lazy/Blocking modes, create client without subscriptions, then subscribe dynamically
                        listeningClient = await createPubsubClient(
                            clusterMode,
                            undefined,
                            undefined,
                            undefined,
                            callback,
                            context,
                        );
                        publishingClient =
                            await createPubsubClient(clusterMode);

                        // Subscribe dynamically based on subscription method
                        await ssubscribeByMethod(
                            listeningClient as GlideClusterClient,
                            new Set([channel]),
                            subscriptionMethod,
                        );
                    }

                    // Verify subscriptions are established
                    await waitForSubscriptionStateIfNeeded(
                        listeningClient,
                        subscriptionMethod,
                        undefined,
                        undefined,
                        new Set([channel]),
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

                    const pubsubMessage = await getMessageByMethod(
                        method,
                        listeningClient,
                        context,
                        0,
                    );

                    expect(pubsubMessage!.message).toEqual(
                        Buffer.from(message),
                    );
                    expect(pubsubMessage!.channel).toEqual(
                        Buffer.from(channel),
                    );
                    expect(pubsubMessage!.pattern).toBeNull();

                    // Assert no messages left
                    await checkNoMessagesLeft(
                        method,
                        listeningClient,
                        context,
                        1,
                    );
                } finally {
                    await Promise.all([
                        clientCleanup(publishingClient!),
                        clientCleanup(
                            listeningClient!,
                            // eslint-disable-next-line @typescript-eslint/no-explicit-any
                            clusterMode ? ({} as any) : undefined,
                        ),
                    ]);
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
            const channel = getRandomKey();

            const pubSubExact = createPubSubSubscription(
                clusterMode,
                {
                    [GlideClusterClientConfiguration.PubSubChannelModes.Exact]:
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
            const channel = getRandomKey();
            const context: PubSubMsg[] = [];

            const pubSubExact = createPubSubSubscription(
                clusterMode,
                {
                    [GlideClusterClientConfiguration.PubSubChannelModes.Exact]:
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

    /**
     * Tests the pubsubChannels command functionality.
     *
     * This test verifies that the pubsubChannels command correctly returns
     * the active channels matching a specified pattern.
     *
     * It covers the following scenarios:
     * - Checking that no channels exist initially
     * - Subscribing to multiple channels
     * - Retrieving all active channels without a pattern
     * - Retrieving channels matching a specific pattern
     * - Verifying that a non-matching pattern returns no channels
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test pubsub channels_%p",
        async (clusterMode) => {
            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let client1: TGlideClient | null = null;
            let client2: TGlideClient | null = null;
            let client: TGlideClient | null = null;

            try {
                const channel1 = "test_channel1";
                const channel2 = "test_channel2";
                const channel3 = "some_channel3";
                const pattern = "test_*";

                if (clusterMode) {
                    client = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    client = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Assert no channels exists yet
                expect(await client.pubsubChannels()).toEqual([]);

                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([channel1, channel2, channel3]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel1, channel2, channel3]),
                    },
                );

                [client1, client2] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Test pubsubChannels without pattern
                const channels = await client2.pubsubChannels();
                expect(new Set(channels)).toEqual(
                    new Set([channel1, channel2, channel3]),
                );

                // Test pubsubChannels with pattern
                const channelsWithPattern = await client2.pubsubChannels({
                    pattern,
                });
                expect(new Set(channelsWithPattern)).toEqual(
                    new Set([channel1, channel2]),
                );

                // Test with non-matching pattern
                const nonMatchingChannels = await client2.pubsubChannels({
                    pattern: "non_matching_*",
                });
                expect(nonMatchingChannels.length).toBe(0);
            } finally {
                await Promise.all([
                    client1
                        ? clientCleanup(
                              client1,
                              clusterMode ? pubSub! : undefined,
                          )
                        : Promise.resolve(),
                    client2 ? clientCleanup(client2) : Promise.resolve(),
                    client ? clientCleanup(client) : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Tests the pubsubNumPat command functionality.
     *
     * This test verifies that the pubsubNumPat command correctly returns
     * the number of unique patterns that are subscribed to by clients.
     *
     * It covers the following scenarios:
     * - Checking that no patterns exist initially
     * - Subscribing to multiple patterns
     * - Verifying the correct number of unique patterns
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test pubsub numpat_%p",
        async (clusterMode) => {
            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let client1: TGlideClient | null = null;
            let client2: TGlideClient | null = null;
            let client: TGlideClient | null = null;

            try {
                const pattern1 = "test_*";
                const pattern2 = "another_*";

                // Create a client and check initial number of patterns
                if (clusterMode) {
                    client = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    client = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                expect(await client.pubsubNumPat()).toBe(0);

                // Set up subscriptions with patterns
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([pattern1, pattern2]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([pattern1, pattern2]),
                    },
                );

                [client1, client2] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                const numPatterns = await client2.pubsubNumPat();
                expect(numPatterns).toBe(2);
            } finally {
                await Promise.all([
                    client1
                        ? clientCleanup(
                              client1,
                              clusterMode ? pubSub! : undefined,
                          )
                        : Promise.resolve(),
                    client2 ? clientCleanup(client2) : Promise.resolve(),
                    client ? clientCleanup(client) : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Tests the pubsubNumSub command functionality.
     *
     * This test verifies that the pubsubNumSub command correctly returns
     * the number of subscribers for specified channels.
     *
     * It covers the following scenarios:
     * - Checking that no subscribers exist initially
     * - Creating multiple clients with different channel subscriptions
     * - Verifying the correct number of subscribers for each channel
     * - Testing pubsubNumSub with no channels specified
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test pubsub numsub_%p",
        async (clusterMode) => {
            let pubSub1:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSub2:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSub3:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let client1: TGlideClient | null = null;
            let client2: TGlideClient | null = null;
            let client3: TGlideClient | null = null;
            let client4: TGlideClient | null = null;
            let client: TGlideClient | null = null;

            try {
                const channel1 = "test_channel1";
                const channel2 = "test_channel2";
                const channel3 = "test_channel3";
                const channel4 = "test_channel4";

                // Set up subscriptions
                pubSub1 = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([channel1, channel2, channel3]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel1, channel2, channel3]),
                    },
                );
                pubSub2 = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([channel2, channel3]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel2, channel3]),
                    },
                );
                pubSub3 = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([channel3]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel3]),
                    },
                );

                // Create a client and check initial subscribers
                if (clusterMode) {
                    client = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    client = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                let subscribers = await client.pubsubNumSub([
                    channel1,
                    channel2,
                    channel3,
                ]);
                expect(convertGlideRecordToRecord(subscribers)).toEqual({
                    [channel1]: 0,
                    [channel2]: 0,
                    [channel3]: 0,
                });

                [client1, client2] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub1,
                    pubSub2,
                );
                [client3, client4] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub3,
                );

                // Test pubsubNumsub
                subscribers = await client2.pubsubNumSub([
                    channel1,
                    channel2,
                    channel3,
                    channel4,
                ]);
                expect(convertGlideRecordToRecord(subscribers)).toEqual({
                    [channel1]: 1,
                    [channel2]: 2,
                    [channel3]: 3,
                    [channel4]: 0,
                });

                // Test pubsubNumsub with no channels
                const emptySubscribers = await client2.pubsubNumSub([]);
                expect(emptySubscribers).toEqual([]);
            } finally {
                await Promise.all([
                    client1
                        ? clientCleanup(
                              client1,
                              clusterMode ? pubSub1! : undefined,
                          )
                        : Promise.resolve(),
                    client2
                        ? clientCleanup(
                              client2,
                              clusterMode ? pubSub2! : undefined,
                          )
                        : Promise.resolve(),
                    client3
                        ? clientCleanup(
                              client3,
                              clusterMode ? pubSub3! : undefined,
                          )
                        : Promise.resolve(),
                    client4 ? clientCleanup(client4) : Promise.resolve(),
                    client ? clientCleanup(client) : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Tests the pubsubShardchannels command functionality.
     *
     * This test verifies that the pubsubShardchannels command correctly returns
     * the active sharded channels matching a specified pattern.
     *
     * It covers the following scenarios:
     * - Checking that no sharded channels exist initially
     * - Subscribing to multiple sharded channels
     * - Retrieving all active sharded channels without a pattern
     * - Retrieving sharded channels matching a specific pattern
     * - Verifying that a non-matching pattern returns no channels
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true])(
        "test pubsub shardchannels_%p",
        async (clusterMode) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) {
                return; // Skip test if server version is less than required
            }

            let pubSub: GlideClusterClientConfiguration.PubSubSubscriptions | null =
                null;
            let client1: TGlideClient | null = null;
            let client2: TGlideClient | null = null;
            let client: TGlideClient | null = null;

            try {
                const channel1 = "test_shardchannel1";
                const channel2 = "test_shardchannel2";
                const channel3 = "some_shardchannel3";
                const pattern = "test_*";

                client = await GlideClusterClient.createClient(
                    getOptions(clusterMode),
                );

                // Assert no sharded channels exist yet
                expect(await client.pubsubShardChannels()).toEqual([]);

                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([channel1, channel2, channel3]),
                    },
                    {},
                );

                [client1, client2] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Test pubsubShardchannels without pattern
                const channels = await (
                    client2 as GlideClusterClient
                ).pubsubShardChannels();
                expect(new Set(channels)).toEqual(
                    new Set([channel1, channel2, channel3]),
                );

                // Test pubsubShardchannels with pattern
                const channelsWithPattern = await (
                    client2 as GlideClusterClient
                ).pubsubShardChannels({ pattern });
                expect(new Set(channelsWithPattern)).toEqual(
                    new Set([channel1, channel2]),
                );

                // Test with non-matching pattern
                const nonMatchingChannels = await (
                    client2 as GlideClusterClient
                ).pubsubShardChannels({ pattern: "non_matching_*" });
                expect(nonMatchingChannels).toEqual([]);
            } finally {
                await Promise.all([
                    client1
                        ? clientCleanup(client1, pubSub ? pubSub : undefined)
                        : Promise.resolve(),
                    client2 ? clientCleanup(client2) : Promise.resolve(),
                    client ? clientCleanup(client) : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Tests the pubsubShardnumsub command functionality.
     *
     * This test verifies that the pubsubShardnumsub command correctly returns
     * the number of subscribers for specified sharded channels.
     *
     * It covers the following scenarios:
     * - Checking that no subscribers exist initially for sharded channels
     * - Creating multiple clients with different sharded channel subscriptions
     * - Verifying the correct number of subscribers for each sharded channel
     * - Testing pubsubShardnumsub with no channels specified
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true])(
        "test pubsub shardnumsub_%p",
        async (clusterMode) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) {
                return; // Skip test if server version is less than required
            }

            let pubSub1: GlideClusterClientConfiguration.PubSubSubscriptions | null =
                null;
            let pubSub2: GlideClusterClientConfiguration.PubSubSubscriptions | null =
                null;
            let pubSub3: GlideClusterClientConfiguration.PubSubSubscriptions | null =
                null;
            let client1: TGlideClient | null = null;
            let client2: TGlideClient | null = null;
            let client3: TGlideClient | null = null;
            let client4: TGlideClient | null = null;
            let client: TGlideClient | null = null;

            try {
                const channel1 = "test_shardchannel1";
                const channel2 = "test_shardchannel2";
                const channel3 = "test_shardchannel3";
                const channel4 = "test_shardchannel4";

                // Set up subscriptions
                pubSub1 = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([channel1, channel2, channel3]),
                    },
                    {},
                );
                pubSub2 = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([channel2, channel3]),
                    },
                    {},
                );
                pubSub3 = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([channel3]),
                    },
                    {},
                );

                // Create a client and check initial subscribers
                client = await GlideClusterClient.createClient(
                    getOptions(clusterMode),
                );

                let subscribers = await (
                    client as GlideClusterClient
                ).pubsubShardNumSub([channel1, channel2, channel3]);
                expect(convertGlideRecordToRecord(subscribers)).toEqual({
                    [channel1]: 0,
                    [channel2]: 0,
                    [channel3]: 0,
                });

                [client1, client2] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub1,
                    pubSub2,
                );
                [client3, client4] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub3,
                );

                // Test pubsubShardnumsub
                subscribers = await (
                    client4 as GlideClusterClient
                ).pubsubShardNumSub([channel1, channel2, channel3, channel4]);
                expect(convertGlideRecordToRecord(subscribers)).toEqual({
                    [channel1]: 1,
                    [channel2]: 2,
                    [channel3]: 3,
                    [channel4]: 0,
                });

                // Test pubsubShardnumsub with no channels
                const emptySubscribers = await (
                    client4 as GlideClusterClient
                ).pubsubShardNumSub([]);
                expect(emptySubscribers).toEqual([]);
            } finally {
                await Promise.all([
                    client1
                        ? clientCleanup(client1, pubSub1 ? pubSub1 : undefined)
                        : Promise.resolve(),
                    client2
                        ? clientCleanup(client2, pubSub2 ? pubSub2 : undefined)
                        : Promise.resolve(),
                    client3
                        ? clientCleanup(client3, pubSub3 ? pubSub3 : undefined)
                        : Promise.resolve(),
                    client4 ? clientCleanup(client4) : Promise.resolve(),
                    client ? clientCleanup(client) : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Tests that pubsubChannels doesn't return sharded channels and pubsubShardchannels
     * doesn't return regular channels.
     *
     * This test verifies the separation between regular and sharded channels in PUBSUB operations.
     *
     * It covers the following scenarios:
     * - Subscribing to both a regular channel and a sharded channel
     * - Verifying that pubsubChannels only returns the regular channel
     * - Verifying that pubsubShardchannels only returns the sharded channel
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true])(
        "test pubsub channels and shardchannels separation_%p",
        async (clusterMode) => {
            let pubSub: GlideClusterClientConfiguration.PubSubSubscriptions | null =
                null;
            let client1: TGlideClient | null = null;
            let client2: TGlideClient | null = null;

            try {
                const minVersion = "7.0.0";

                if (cmeCluster.checkIfServerVersionLessThan(minVersion)) {
                    return; // Skip test if server version is less than required
                }

                const regularChannel = "regular_channel";
                const shardChannel = "shard_channel";

                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([regularChannel]),
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([shardChannel]),
                    },
                    {},
                );

                [client1, client2] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                );

                // Test pubsubChannels
                const regularChannels = await client2.pubsubChannels();
                expect(regularChannels).toEqual([regularChannel]);

                // Test pubsubShardchannels
                const shardChannels = await (
                    client2 as GlideClusterClient
                ).pubsubShardChannels();
                expect(shardChannels).toEqual([shardChannel]);
            } finally {
                await Promise.all([
                    client1
                        ? clientCleanup(client1, pubSub ? pubSub : undefined)
                        : Promise.resolve(),
                    client2 ? clientCleanup(client2) : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Tests that pubsubNumSub doesn't count sharded channel subscribers and pubsubShardnumsub
     * doesn't count regular channel subscribers.
     *
     * This test verifies the separation between regular and sharded channel subscribers in PUBSUB operations.
     *
     * It covers the following scenarios:
     * - Subscribing to both a regular channel and a sharded channel with two clients
     * - Verifying that pubsubNumSub only counts subscribers for the regular channel
     * - Verifying that pubsubShardnumsub only counts subscribers for the sharded channel
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true])(
        "test pubsub numsub and shardnumsub separation_%p",
        async (clusterMode) => {
            //const clusterMode = false;
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) {
                return; // Skip test if server version is less than required
            }

            let pubSub: GlideClusterClientConfiguration.PubSubSubscriptions | null =
                null;
            let client1: TGlideClient | null = null;
            let client2: TGlideClient | null = null;

            try {
                const regularChannel = "regular_channel";
                const shardChannel = "shard_channel";

                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([regularChannel]),
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([shardChannel]),
                    },
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([regularChannel]),
                    },
                );

                [client1, client2] = await createClients(
                    clusterMode,
                    getOptions(clusterMode),
                    getOptions(clusterMode),
                    pubSub,
                    pubSub,
                );

                // Test pubsubNumsub
                const regularSubscribers = await client2.pubsubNumSub([
                    regularChannel,
                    shardChannel,
                ]);
                expect(convertGlideRecordToRecord(regularSubscribers)).toEqual({
                    [regularChannel]: 2,
                    [shardChannel]: 0,
                });

                // Test pubsubShardnumsub
                const shardSubscribers = await (
                    client2 as GlideClusterClient
                ).pubsubShardNumSub([regularChannel, shardChannel]);
                expect(convertGlideRecordToRecord(shardSubscribers)).toEqual({
                    [regularChannel]: 0,
                    [shardChannel]: 2,
                });
            } finally {
                await Promise.all([
                    client1
                        ? clientCleanup(client1, pubSub!)
                        : Promise.resolve(),
                    client2
                        ? clientCleanup(client2, pubSub!)
                        : Promise.resolve(),
                ]);
            }
        },
        TIMEOUT,
    );

    /**
     * Tests dynamic subscription and unsubscription for exact channels.
     *
     * This test verifies that a client can dynamically subscribe to and unsubscribe from
     * channels at runtime without pre-configuring subscriptions during client creation.
     * Tests both lazy (non-blocking) and blocking (with timeout) modes for both operations.
     * Uses subscribeByMethod and unsubscribeByMethod helpers for consistent subscription handling.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param subscribeMode - Specifies lazy or blocking subscription mode.
     * @param unsubscribeMode - Specifies lazy or blocking unsubscription mode.
     */
    it.each([
        [true, Mode.Lazy, Mode.Lazy],
        [true, Mode.Lazy, Mode.Blocking],
        [true, Mode.Blocking, Mode.Lazy],
        [true, Mode.Blocking, Mode.Blocking],
        [false, Mode.Lazy, Mode.Lazy],
        [false, Mode.Lazy, Mode.Blocking],
        [false, Mode.Blocking, Mode.Lazy],
        [false, Mode.Blocking, Mode.Blocking],
    ])(
        "dynamic_subscribe_unsubscribe_%p_%p_%p",
        async (clusterMode, subscribeMode, unsubscribeMode) => {
            let listener: TGlideClient | null = null;
            let sender: TGlideClient | null = null;

            try {
                const channel = getRandomKey();
                const message1 = getRandomKey();
                const message2 = getRandomKey();

                if (clusterMode) {
                    listener = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                    sender = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    listener = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                    sender = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                const channelsSet = new Set([channel]);

                // Dynamically subscribe to channel using helper
                await subscribeByMethod(listener, channelsSet, subscribeMode);

                // Wait for subscription to be established
                await waitForSubscriptionStateIfNeeded(
                    listener,
                    subscribeMode,
                    channelsSet,
                );

                // Publish first message and verify reception
                expect(await sender.publish(message1, channel)).toBeGreaterThan(
                    0,
                );
                await new Promise((resolve) => setTimeout(resolve, 500));

                const pubsubMsg1 = await listener.getPubSubMessage();
                expect(pubsubMsg1.message).toEqual(message1);
                expect(pubsubMsg1.channel).toEqual(channel);
                expect(pubsubMsg1.pattern).toBeNull();

                // Dynamically unsubscribe from channel using helper
                await unsubscribeByMethod(
                    listener,
                    channelsSet,
                    unsubscribeMode,
                );

                // Wait for unsubscribe to complete
                await waitForSubscriptionState(
                    listener,
                    new Set<string>(),
                    undefined,
                    undefined,
                );

                // Verify no message in queue before publishing
                const msgBefore = listener.tryGetPubSubMessage();
                expect(msgBefore).toBeNull();

                // Publish second message AFTER unsubscribe
                await sender.publish(message2, channel);
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Should not receive message after unsubscribe
                const msg = listener.tryGetPubSubMessage();
                expect(msg).toBeNull();
            } finally {
                if (listener) {
                    listener.close();
                }

                if (sender) {
                    sender.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests dynamic pattern subscription and unsubscription.
     *
     * This test verifies that a client can dynamically subscribe to and unsubscribe from
     * patterns at runtime without pre-configuring subscriptions during client creation.
     * Tests both lazy (non-blocking) and blocking (with timeout) modes for both operations.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param subscribeMode - Specifies lazy or blocking subscription mode.
     * @param unsubscribeMode - Specifies lazy or blocking unsubscription mode.
     */
    it.each([
        [true, Mode.Lazy, Mode.Lazy],
        [true, Mode.Lazy, Mode.Blocking],
        [true, Mode.Blocking, Mode.Lazy],
        [true, Mode.Blocking, Mode.Blocking],
        [false, Mode.Lazy, Mode.Lazy],
        [false, Mode.Lazy, Mode.Blocking],
        [false, Mode.Blocking, Mode.Lazy],
        [false, Mode.Blocking, Mode.Blocking],
    ])(
        "dynamic_psubscribe_punsubscribe_%p_%p_%p",
        async (clusterMode, subscribeMode, unsubscribeMode) => {
            let listener: TGlideClient | null = null;
            let sender: TGlideClient | null = null;

            try {
                const pattern = `${getRandomKey()}*`;
                const channel1 = pattern.replace(/\*/g, getRandomKey());
                const channel2 = pattern.replace(/\*/g, getRandomKey());
                const message1 = getRandomKey();
                const message2 = getRandomKey();

                if (clusterMode) {
                    listener = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                    sender = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    listener = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                    sender = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                const patterns = new Set([pattern]);

                // Dynamically subscribe to pattern
                await psubscribeByMethod(listener, patterns, subscribeMode);

                // Wait for subscription to be established
                await waitForSubscriptionStateIfNeeded(
                    listener,
                    subscribeMode,
                    undefined,
                    patterns,
                    undefined,
                );

                // Publish first message to matching channel and verify reception
                await sender.publish(message1, channel1);
                await new Promise((resolve) => setTimeout(resolve, 500));

                const pubsubMsg1 = await listener.getPubSubMessage();
                expect(pubsubMsg1.message).toEqual(message1);
                expect(pubsubMsg1.channel).toEqual(channel1);
                expect(pubsubMsg1.pattern).toEqual(pattern);

                // Dynamically unsubscribe from pattern using helper
                await punsubscribeByMethod(
                    listener,
                    new Set(patterns),
                    unsubscribeMode,
                    500,
                );

                // Wait for unsubscribe to complete
                await waitForSubscriptionState(
                    listener,
                    undefined,
                    new Set<string>(),
                    undefined,
                );

                // Verify no message in queue before publishing
                const msgBefore = listener.tryGetPubSubMessage();
                expect(msgBefore).toBeNull();

                // Publish second message AFTER unsubscribe
                await sender.publish(message2, channel2);
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Should not receive message after unsubscribe
                const msg = listener.tryGetPubSubMessage();
                expect(msg).toBeNull();
            } finally {
                if (listener) {
                    listener.close();
                }

                if (sender) {
                    sender.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests dynamic sharded subscription and unsubscription (cluster only).
     *
     * This test verifies that a cluster client can dynamically subscribe to and unsubscribe from
     * sharded channels at runtime. Sharded pubsub is tied to specific slots/shards.
     * Tests both lazy (non-blocking) and blocking (with timeout) modes for both operations.
     * Requires Valkey 7.0+.
     *
     * @param clusterMode - Must be true (cluster mode only).
     * @param subscribeMode - Specifies lazy or blocking subscription mode.
     * @param unsubscribeMode - Specifies lazy or blocking unsubscription mode.
     */
    it.each([
        [true, Mode.Lazy, Mode.Lazy],
        [true, Mode.Lazy, Mode.Blocking],
        [true, Mode.Blocking, Mode.Lazy],
        [true, Mode.Blocking, Mode.Blocking],
    ])(
        "dynamic_ssubscribe_sunsubscribe_%p_%p_%p",
        async (clusterMode, subscribeMode, unsubscribeMode) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) {
                return;
            }

            let listener: TGlideClient | null = null;
            let sender: TGlideClient | null = null;

            try {
                const channel = getRandomKey();
                const message1 = getRandomKey();
                const message2 = getRandomKey();

                listener = await GlideClusterClient.createClient(
                    getOptions(clusterMode),
                );
                sender = await GlideClusterClient.createClient(
                    getOptions(clusterMode),
                );

                const channels = new Set([channel]);

                // Dynamically subscribe to sharded channel
                await ssubscribeByMethod(
                    listener as GlideClusterClient,
                    channels,
                    subscribeMode,
                );

                // Wait for subscription to be established
                await waitForSubscriptionStateIfNeeded(
                    listener,
                    subscribeMode,
                    undefined,
                    undefined,
                    channels,
                );

                // Publish first message and verify reception
                expect(
                    await (sender as GlideClusterClient).publish(
                        message1,
                        channel,
                        true,
                    ),
                ).toBeGreaterThan(0);
                await new Promise((resolve) => setTimeout(resolve, 500));

                const pubsubMsg1 = await listener.getPubSubMessage();
                expect(pubsubMsg1.message).toEqual(message1);
                expect(pubsubMsg1.channel).toEqual(channel);
                expect(pubsubMsg1.pattern).toBeNull();

                // Dynamically unsubscribe from sharded channel using helper
                await sunsubscribeByMethod(
                    listener as GlideClusterClient,
                    new Set(channels),
                    unsubscribeMode,
                    500,
                );

                // Wait for unsubscribe to complete
                await waitForSubscriptionState(
                    listener,
                    undefined,
                    undefined,
                    new Set<string>(),
                );

                // Verify no message in queue before publishing
                const msgBefore = listener.tryGetPubSubMessage();
                expect(msgBefore).toBeNull();

                // Publish second message AFTER unsubscribe
                await (sender as GlideClusterClient).publish(
                    message2,
                    channel,
                    true,
                );
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Should not receive message after unsubscribe
                const msg = listener.tryGetPubSubMessage();
                expect(msg).toBeNull();
            } finally {
                if (listener) {
                    listener.close();
                }

                if (sender) {
                    sender.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests unsubscribing from pre-configured subscriptions.
     *
     * This test verifies that a client can dynamically unsubscribe from channels
     * that were pre-configured during client creation (Config mode).
     * Tests both lazy (non-blocking) and blocking (with timeout) modes for unsubscription.
     * Uses unsubscribeByMethod helper for consistent unsubscription handling.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param unsubscribeMode - Specifies lazy or blocking unsubscription mode.
     */
    it.each([
        [true, Mode.Lazy],
        [true, Mode.Blocking],
        [false, Mode.Lazy],
        [false, Mode.Blocking],
    ])(
        "dynamic_unsubscribe_from_preconfigured_%p_%p",
        async (clusterMode, unsubscribeMode) => {
            let listener: TGlideClient | null = null;
            let sender: TGlideClient | null = null;

            try {
                const channel = getRandomKey();
                const message1 = getRandomKey();
                const message2 = getRandomKey();

                const channelsSet = new Set([channel as string]);

                // Create client with pre-configured subscription
                const pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: channelsSet,
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            channelsSet,
                    },
                );

                if (clusterMode) {
                    listener = await GlideClusterClient.createClient({
                        pubsubSubscriptions: pubSub,
                        ...getOptions(clusterMode),
                    });
                    sender = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    listener = await GlideClient.createClient({
                        pubsubSubscriptions: pubSub,
                        ...getOptions(clusterMode),
                    });
                    sender = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Wait for subscription to be established (Config mode)
                await waitForSubscriptionStateIfNeeded(
                    listener,
                    Mode.Config,
                    channelsSet,
                );

                // Publish first message
                expect(await sender.publish(message1, channel)).toBeGreaterThan(
                    0,
                );

                // Allow time for message delivery
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Verify first message received
                const pubsubMsg1 = await listener.getPubSubMessage();
                expect(pubsubMsg1.message).toEqual(message1);
                expect(pubsubMsg1.channel).toEqual(channel);

                // Dynamically unsubscribe from pre-configured channel using helper
                await unsubscribeByMethod(
                    listener,
                    channelsSet,
                    unsubscribeMode,
                );

                // Wait for unsubscribe to complete
                await waitForSubscriptionState(
                    listener,
                    new Set<string>(),
                    undefined,
                    undefined,
                );

                // Drain any pending messages that were published before unsubscribe
                while (listener.tryGetPubSubMessage() !== null) {
                    // Keep draining
                }

                // Publish second message
                await sender.publish(message2, channel);

                // Allow time for potential message delivery
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Verify no message received after unsubscribe
                const pubsubMsg2 = listener.tryGetPubSubMessage();
                expect(pubsubMsg2).toBeNull();
            } finally {
                if (listener) {
                    listener.close();
                }

                if (sender) {
                    sender.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests pattern unsubscribing from pre-configured pattern subscriptions.
     *
     * This test verifies that a client can dynamically unsubscribe from patterns
     * that were pre-configured during client creation.
     * Tests both lazy (non-blocking) and blocking (with timeout) modes.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param unsubscribeMode - Specifies lazy or blocking unsubscription mode.
     */
    it.each([
        [true, Mode.Lazy],
        [true, Mode.Blocking],
        [false, Mode.Lazy],
        [false, Mode.Blocking],
    ])(
        "dynamic_punsubscribe_from_preconfigured_%p_%p",
        async (clusterMode, unsubscribeMode) => {
            let listener: TGlideClient | null = null;
            let sender: TGlideClient | null = null;

            try {
                const pattern = `${getRandomKey()}*`;
                const channel = pattern.replace(/\*/g, getRandomKey());
                const message1 = getRandomKey();
                const message2 = getRandomKey();

                // Create client with pre-configured pattern subscription
                const pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([pattern as string]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([pattern as string]),
                    },
                );

                if (clusterMode) {
                    listener = await GlideClusterClient.createClient({
                        pubsubSubscriptions: pubSub,
                        ...getOptions(clusterMode),
                    });
                    sender = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    listener = await GlideClient.createClient({
                        pubsubSubscriptions: pubSub,
                        ...getOptions(clusterMode),
                    });
                    sender = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Allow time for subscription to propagate
                await waitForSubscriptionState(
                    listener,
                    undefined,
                    new Set([pattern as string]),
                    undefined,
                );

                // Publish first message to matching channel
                await sender.publish(message1, channel);

                // Allow time for message delivery
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Verify first message received
                const pubsubMsg1 = await listener.getPubSubMessage();
                expect(pubsubMsg1.message).toEqual(message1);
                expect(pubsubMsg1.channel).toEqual(channel);
                expect(pubsubMsg1.pattern).toEqual(pattern);

                // Dynamically unsubscribe from pre-configured pattern using helper
                await punsubscribeByMethod(
                    listener,
                    new Set([pattern]),
                    unsubscribeMode,
                    500,
                );

                // Wait for unsubscribe to complete
                await waitForSubscriptionState(
                    listener,
                    undefined,
                    new Set<string>(),
                    undefined,
                );

                // Drain any pending messages that were published before unsubscribe
                while (listener.tryGetPubSubMessage() !== null) {
                    // Keep draining
                }

                // Publish second message
                await sender.publish(message2, channel);

                // Allow time for potential message delivery
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Verify no message received after unsubscribe
                const pubsubMsg2 = listener.tryGetPubSubMessage();
                expect(pubsubMsg2).toBeNull();
            } finally {
                if (listener) {
                    listener.close();
                }

                if (sender) {
                    sender.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests sharded unsubscribing from pre-configured sharded subscriptions (cluster only).
     *
     * This test verifies that a cluster client can dynamically unsubscribe from sharded channels
     * that were pre-configured during client creation.
     * Tests both lazy (non-blocking) and blocking (with timeout) modes.
     * Requires Valkey 7.0+.
     *
     * @param clusterMode - Must be true (cluster mode only).
     * @param unsubscribeMode - Specifies lazy or blocking unsubscription mode.
     */
    it.each([
        [true, Mode.Lazy],
        [true, Mode.Blocking],
    ])(
        "dynamic_sunsubscribe_from_preconfigured_%p_%p",
        async (clusterMode, unsubscribeMode) => {
            const minVersion = "7.0.0";

            if (cmeCluster.checkIfServerVersionLessThan(minVersion)) {
                return;
            }

            let listener: TGlideClient | null = null;
            let sender: TGlideClient | null = null;

            try {
                const channel = getRandomKey();
                const message1 = getRandomKey();
                const message2 = getRandomKey();

                // Create client with pre-configured sharded subscription
                const pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([channel as string]),
                    },
                    {},
                );

                listener = await GlideClusterClient.createClient({
                    pubsubSubscriptions: pubSub,
                    ...getOptions(clusterMode),
                });
                sender = await GlideClusterClient.createClient(
                    getOptions(clusterMode),
                );

                // Wait for subscription to be established
                await waitForSubscriptionState(
                    listener,
                    undefined,
                    undefined,
                    new Set([channel as string]),
                );

                // Publish first message to sharded channel
                expect(
                    await (sender as GlideClusterClient).publish(
                        message1,
                        channel,
                        true,
                    ),
                ).toBeGreaterThan(0);

                // Allow time for message delivery
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Verify first message received
                const pubsubMsg1 = await listener.getPubSubMessage();
                expect(pubsubMsg1.message).toEqual(message1);
                expect(pubsubMsg1.channel).toEqual(channel);
                expect(pubsubMsg1.pattern).toBeNull();

                // Dynamically unsubscribe from pre-configured sharded channel using helper
                await sunsubscribeByMethod(
                    listener as GlideClusterClient,
                    new Set([channel]),
                    unsubscribeMode,
                    500,
                );

                // Wait for unsubscribe to complete
                await waitForSubscriptionState(
                    listener,
                    undefined,
                    undefined,
                    new Set<string>(),
                );

                // Drain any pending messages that were published before unsubscribe
                while (listener.tryGetPubSubMessage() !== null) {
                    // Keep draining
                }

                // Publish second message
                await (sender as GlideClusterClient).publish(
                    message2,
                    channel,
                    true,
                );

                // Allow time for potential message delivery
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Verify no message received after unsubscribe
                const pubsubMsg2 = listener.tryGetPubSubMessage();
                expect(pubsubMsg2).toBeNull();
            } finally {
                if (listener) {
                    listener.close();
                }

                if (sender) {
                    sender.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests dynamic subscription with pre-configured callback but no channels.
     *
     * This test verifies that a client can be created with only a callback and context
     * (no pre-configured channels), and then dynamically subscribe to channels where
     * the callback will catch messages from those channels.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param subscribeMode - Specifies lazy or blocking subscription mode.
     */
    it.each([
        [true, Mode.Lazy],
        [true, Mode.Blocking],
        [false, Mode.Lazy],
        [false, Mode.Blocking],
    ])(
        "dynamic_subscribe_with_preconfigured_callback_no_channels_%p_%p",
        async (clusterMode, subscribeMode) => {
            let listener: TGlideClient | null = null;
            let sender: TGlideClient | null = null;

            try {
                const channel = getRandomKey();
                const message = getRandomKey();

                // Create callback context to collect messages
                const callbackMessages: PubSubMsg[] = [];
                const callback = newMessage;

                // Create client with pre-configured callback but NO channels
                const pubSub = createPubSubSubscription(
                    clusterMode,
                    {}, // No channels configured
                    {}, // No channels configured
                    callback,
                    callbackMessages,
                );

                if (clusterMode) {
                    listener = await GlideClusterClient.createClient({
                        pubsubSubscriptions: pubSub,
                        ...getOptions(clusterMode),
                    });
                    sender = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    listener = await GlideClient.createClient({
                        pubsubSubscriptions: pubSub,
                        ...getOptions(clusterMode),
                    });
                    sender = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Verify no messages in callback initially
                expect(callbackMessages.length).toBe(0);

                // Dynamically subscribe to channel
                if (subscribeMode === Mode.Lazy) {
                    await listener.subscribeLazy([channel]);
                } else {
                    await listener.subscribe([channel], 500);
                }

                // Wait for subscription to be established
                await waitForSubscriptionStateIfNeeded(
                    listener,
                    subscribeMode,
                    new Set([channel as string]),
                    undefined,
                    undefined,
                );

                // Publish message
                expect(await sender.publish(message, channel)).toBeGreaterThan(
                    0,
                );

                // Allow time for message delivery
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Verify callback received the message
                expect(callbackMessages.length).toBe(1);
                expect(callbackMessages[0].message).toEqual(message);
                expect(callbackMessages[0].channel).toEqual(channel);
                expect(callbackMessages[0].pattern).toBeNull();
            } finally {
                if (listener) {
                    listener.close();
                }

                if (sender) {
                    sender.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests that subscription metrics exist in statistics.

     *
     * This test verifies that the getStatistics method returns subscription-related
     * metrics including out-of-sync count and last sync timestamp.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_subscription_metrics_in_statistics_%p",
        async (clusterMode) => {
            let client: TGlideClient | null = null;

            try {
                const channel = getRandomKey();

                // Create client WITHOUT any subscription configuration
                if (clusterMode) {
                    client = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    client = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Dynamically subscribe to channel (non-blocking)
                await client.subscribeLazy([channel]);

                // Wait for subscription to be established
                await waitForSubscriptionState(
                    client,
                    new Set([channel as string]),
                    undefined,
                    undefined,
                );

                // Get statistics
                const stats = (await client.getStatistics()) as Record<
                    string,
                    string
                >;

                // Verify subscription metrics exist
                expect(stats).toHaveProperty("subscription_out_of_sync_count");
                expect(stats).toHaveProperty(
                    "subscription_last_sync_timestamp",
                );

                // Verify metrics are valid (non-negative numbers)
                const outOfSyncCount = parseInt(
                    stats["subscription_out_of_sync_count"],
                );
                const lastSyncTimestamp = parseInt(
                    stats["subscription_last_sync_timestamp"],
                );

                expect(outOfSyncCount).toBeGreaterThanOrEqual(0);
                expect(lastSyncTimestamp).toBeGreaterThanOrEqual(0);
            } finally {
                if (client) {
                    client.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Tests that subscription timestamp updates after subscribe.
     *
     * This test verifies that the subscription_last_sync_timestamp metric
     * is updated after a subscription operation.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_subscription_timestamp_updates_after_subscribe_%p",
        async (clusterMode) => {
            let client: TGlideClient | null = null;

            try {
                const channel = getRandomKey();

                // Create client WITHOUT any subscription configuration
                if (clusterMode) {
                    client = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    client = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Get initial statistics
                const statsBefore = (await client.getStatistics()) as Record<
                    string,
                    string
                >;
                const timestampBefore = parseInt(
                    statsBefore["subscription_last_sync_timestamp"],
                );

                // Dynamically subscribe to channel (non-blocking)
                await client.subscribeLazy([channel]);

                // Allow time for subscription to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Get updated statistics
                const statsAfter = (await client.getStatistics()) as Record<
                    string,
                    string
                >;
                const timestampAfter = parseInt(
                    statsAfter["subscription_last_sync_timestamp"],
                );

                // Verify timestamp was updated
                expect(timestampAfter).toBeGreaterThan(timestampBefore);
            } finally {
                if (client) {
                    client.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Helper function to kill connections to the server.
     * This triggers reconnection and resubscription behavior.
     *
     * @param client - The client to use for sending CLIENT KILL command
     * @param killType - Type of connections to kill ("normal" or null for all)
     * @param skipMe - Whether to skip the calling client ("yes" or "no")
     */
    async function killConnections(
        client: TGlideClient,
        killType: string | null = "normal",
        skipMe = "yes",
    ): Promise<void> {
        const cmd: (string | Buffer)[] = ["CLIENT", "KILL"];

        if (killType !== null) {
            cmd.push("TYPE", killType);
        }

        cmd.push("SKIPME", skipMe);

        if (client instanceof GlideClusterClient) {
            await client.customCommand(cmd, { route: "allNodes" });
        } else {
            await client.customCommand(cmd);
        }
    }

    /**
     * Tests the basic happy path for exact PUBSUB functionality using custom commands.
     *
     * This test mirrors test_pubsub_exact_happy_path but uses customCommand to send
     * SUBSCRIBE (lazy) or SUBSCRIBE_BLOCKING (blocking) commands directly.
     * Config mode is not tested as it doesn't use custom commands.
     */
    it.each(
        testCasesWithSubscriptionMethod.filter(
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            ([_, __, subMethod]) => subMethod !== Mode.Config,
        ),
    )(
        "test_pubsub_exact_happy_path_custom_command_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const channel = "test_exact_channel_custom";
                const message = "test_exact_message_custom";

                const callbackMessages: PubSubMsg[] = [];
                const callback =
                    method === MethodTesting.Callback ? newMessage : undefined;
                const context =
                    method === MethodTesting.Callback
                        ? callbackMessages
                        : undefined;

                // Create client with callback only (no config-based subscriptions)
                listeningClient = await createPubsubClient(
                    clusterMode,
                    undefined,
                    undefined,
                    undefined,
                    callback,
                    context,
                    undefined,
                    undefined,
                    clusterMode
                        ? cmeCluster.ports().map((port) => ({
                              host: "localhost",
                              port,
                          }))
                        : cmdCluster.ports().map((port) => ({
                              host: "localhost",
                              port,
                          })),
                );

                if (clusterMode) {
                    publishingClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    publishingClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Subscribe using customCommand
                let cmd: (string | Buffer)[];

                if (subscriptionMethod === Mode.Lazy) {
                    // SUBSCRIBE is the lazy (non-blocking) command
                    cmd = ["SUBSCRIBE", channel];
                } else {
                    // SUBSCRIBE_BLOCKING takes channels followed by timeout_ms
                    const timeoutMs = 500;
                    cmd = ["SUBSCRIBE_BLOCKING", channel, timeoutMs.toString()];
                }

                const subscribeResult =
                    await listeningClient.customCommand(cmd);
                expect(subscribeResult).toBeNull();

                // Verify subscription is established
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    new Set([channel]),
                );

                const publishResult = await publishingClient.publish(
                    message,
                    channel,
                );

                if (clusterMode) {
                    expect(publishResult).toBe(1);
                }

                // Allow the message to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const pubsubMsg = await getMessageByMethod(
                    method,
                    listeningClient,
                    callbackMessages,
                    0,
                );

                expect(pubsubMsg).not.toBeNull();
                expect(pubsubMsg!.message).toBe(message);
                expect(pubsubMsg!.channel).toBe(channel);
                expect(pubsubMsg!.pattern).toBeNull();

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    callbackMessages,
                    1,
                );

                // Unsubscribe using customCommand
                let unsubCmd: (string | Buffer)[];

                if (subscriptionMethod === Mode.Lazy) {
                    // UNSUBSCRIBE is the lazy (non-blocking) command
                    unsubCmd = ["UNSUBSCRIBE", channel];
                } else {
                    // UNSUBSCRIBE_BLOCKING takes channels followed by timeout_ms
                    const timeoutMs = 5000;
                    unsubCmd = [
                        "UNSUBSCRIBE_BLOCKING",
                        channel,
                        timeoutMs.toString(),
                    ];
                }

                const unsubscribeResult =
                    await listeningClient.customCommand(unsubCmd);
                expect(unsubscribeResult).toBeNull();

                // Verify unsubscription
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    new Set(),
                );
            } finally {
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        // eslint-disable-next-line @typescript-eslint/no-explicit-any
                        clusterMode ? ({} as any) : undefined,
                    );
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that exact channel subscriptions are automatically restored after connection kill.
     */
    it.each(testCasesWithSubscriptionMethod)(
        "test_resubscribe_after_connection_kill_exact_channels_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const channel = "reconnect_exact_channel_test";
                const messageBefore = "message_before_kill";
                const messageAfter = "message_after_kill";

                const callbackMessages: PubSubMsg[] = [];
                const callback =
                    method === MethodTesting.Callback ? newMessage : undefined;
                const context =
                    method === MethodTesting.Callback
                        ? callbackMessages
                        : undefined;

                if (subscriptionMethod === Mode.Config) {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        new Set([channel]),
                        undefined,
                        undefined,
                        callback,
                        context,
                        undefined,
                        undefined,
                        clusterMode
                            ? cmeCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              }))
                            : cmdCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              })),
                    );
                } else {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        undefined,
                        callback,
                        context,
                        undefined,
                        undefined,
                        clusterMode
                            ? cmeCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              }))
                            : cmdCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              })),
                    );
                    await subscribeByMethod(
                        listeningClient,
                        new Set([channel]),
                        subscriptionMethod,
                    );
                }

                if (clusterMode) {
                    publishingClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    publishingClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    new Set([channel]),
                );

                // Verify subscription works before kill
                await publishingClient.publish(messageBefore, channel);
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const msgBefore = await getMessageByMethod(
                    method,
                    listeningClient,
                    callbackMessages,
                    0,
                );
                expect(msgBefore).not.toBeNull();
                expect(msgBefore!.message).toBe(messageBefore);
                expect(msgBefore!.channel).toBe(channel);

                // Kill connections - this should trigger reconnection
                await killConnections(publishingClient, null);

                // Give some time for connection to reconnect
                await new Promise((resolve) => setTimeout(resolve, 2000));

                // Wait for subscriptions to be re-established
                await waitForSubscriptionState(
                    listeningClient,
                    new Set([channel]),
                );

                // Verify subscription still works after reconnection
                await publishingClient.publish(messageAfter, channel);
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const msgAfter = await getMessageByMethod(
                    method,
                    listeningClient,
                    callbackMessages,
                    1,
                );
                expect(msgAfter).not.toBeNull();
                expect(msgAfter!.message).toBe(messageAfter);
                expect(msgAfter!.channel).toBe(channel);

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    callbackMessages,
                    2,
                );
            } finally {
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        // eslint-disable-next-line @typescript-eslint/no-explicit-any
                        clusterMode ? ({} as any) : undefined,
                    );
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that pattern subscriptions are automatically restored after connection kill.
     */
    it.each(testCasesWithSubscriptionMethod)(
        "test_resubscribe_after_connection_kill_patterns_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const pattern = "news_reconnect_pattern.*";
                const channel = "news_reconnect_pattern.sports";
                const messageBefore = "message_before_kill";
                const messageAfter = "message_after_kill";

                const callbackMessages: PubSubMsg[] = [];
                const callback =
                    method === MethodTesting.Callback ? newMessage : undefined;
                const context =
                    method === MethodTesting.Callback
                        ? callbackMessages
                        : undefined;

                if (subscriptionMethod === Mode.Config) {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined,
                        new Set([pattern]),
                        undefined,
                        callback,
                        context,
                        undefined,
                        undefined,
                        clusterMode
                            ? cmeCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              }))
                            : cmdCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              })),
                    );
                } else {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        undefined,
                        callback,
                        context,
                        undefined,
                        undefined,
                        clusterMode
                            ? cmeCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              }))
                            : cmdCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              })),
                    );
                    await psubscribeByMethod(
                        listeningClient,
                        new Set([pattern]),
                        subscriptionMethod,
                    );
                }

                if (clusterMode) {
                    publishingClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    publishingClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    undefined,
                    new Set([pattern]),
                );

                // Verify subscription works before kill
                await publishingClient.publish(messageBefore, channel);
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const msgBefore = await getMessageByMethod(
                    method,
                    listeningClient,
                    callbackMessages,
                    0,
                );
                expect(msgBefore).not.toBeNull();
                expect(msgBefore!.message).toBe(messageBefore);
                expect(msgBefore!.channel).toBe(channel);
                expect(msgBefore!.pattern).toBe(pattern);

                // Kill connections
                await killConnections(publishingClient, null);

                // Give some time for connection to reconnect
                await new Promise((resolve) => setTimeout(resolve, 2000));

                await waitForSubscriptionState(
                    listeningClient,
                    undefined,
                    new Set([pattern]),
                );

                // Verify subscription still works after reconnection
                await publishingClient.publish(messageAfter, channel);
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const msgAfter = await getMessageByMethod(
                    method,
                    listeningClient,
                    callbackMessages,
                    1,
                );
                expect(msgAfter).not.toBeNull();
                expect(msgAfter!.message).toBe(messageAfter);
                expect(msgAfter!.channel).toBe(channel);
                expect(msgAfter!.pattern).toBe(pattern);

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    callbackMessages,
                    2,
                );
            } finally {
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        // eslint-disable-next-line @typescript-eslint/no-explicit-any
                        clusterMode ? ({} as any) : undefined,
                    );
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that sharded subscriptions are automatically restored after connection kill.
     * Only runs in cluster mode (Valkey 7.0+).
     */
    it.each(
        testCasesWithSubscriptionMethod.filter(([clusterMode]) => clusterMode),
    )(
        "test_resubscribe_after_connection_kill_sharded_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            const version = await getServerVersion([
                cmeCluster.getAddresses()[0],
            ]);

            if (version < "7.0.0") {
                return;
            }

            let listeningClient: GlideClusterClient | null = null;
            let publishingClient: GlideClusterClient | null = null;

            try {
                const channel = "sharded_reconnect_test_channel";
                const messageBefore = "message_before_kill";
                const messageAfter = "message_after_kill";

                const callbackMessages: PubSubMsg[] = [];
                const callback =
                    method === MethodTesting.Callback ? newMessage : undefined;
                const context =
                    method === MethodTesting.Callback
                        ? callbackMessages
                        : undefined;

                if (subscriptionMethod === Mode.Config) {
                    listeningClient = (await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        new Set([channel]),
                        callback,
                        context,
                        undefined,
                        undefined,
                        cmeCluster.ports().map((port) => ({
                            host: "localhost",
                            port,
                        })),
                    )) as GlideClusterClient;
                } else {
                    listeningClient = (await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        undefined,
                        callback,
                        context,
                        undefined,
                        undefined,
                        cmeCluster.ports().map((port) => ({
                            host: "localhost",
                            port,
                        })),
                    )) as GlideClusterClient;
                    await ssubscribeByMethod(
                        listeningClient,
                        new Set([channel]),
                        subscriptionMethod,
                    );
                }

                publishingClient = await GlideClusterClient.createClient(
                    getOptions(clusterMode),
                );

                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    undefined,
                    undefined,
                    new Set([channel]),
                );

                // Verify subscription works before kill
                await publishingClient.publish(messageBefore, channel, true);
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const msgBefore = await getMessageByMethod(
                    method,
                    listeningClient,
                    callbackMessages,
                    0,
                );
                expect(msgBefore).not.toBeNull();
                expect(msgBefore!.message).toBe(messageBefore);
                expect(msgBefore!.channel).toBe(channel);

                // Kill connections
                await killConnections(publishingClient, null);

                // Give some time for connection to reconnect
                await new Promise((resolve) => setTimeout(resolve, 2000));

                await waitForSubscriptionState(
                    listeningClient,
                    undefined,
                    undefined,
                    new Set([channel]),
                );

                // Verify subscription still works after reconnection
                await publishingClient.publish(messageAfter, channel, true);
                await new Promise((resolve) => setTimeout(resolve, 1000));

                const msgAfter = await getMessageByMethod(
                    method,
                    listeningClient,
                    callbackMessages,
                    1,
                );
                expect(msgAfter).not.toBeNull();
                expect(msgAfter!.message).toBe(messageAfter);
                expect(msgAfter!.channel).toBe(channel);

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    callbackMessages,
                    2,
                );
            } finally {
                if (listeningClient) {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    await clientCleanup(listeningClient, {} as any);
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that 256 exact channel subscriptions are automatically restored after connection kill.
     */
    it.each(testCasesWithSubscriptionMethod)(
        "test_resubscribe_after_connection_kill_many_exact_channels_%p_%p_%p",
        async (clusterMode, method, subscriptionMethod) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const NUM_CHANNELS = 256;
                const channels = new Set(
                    Array.from(
                        { length: NUM_CHANNELS },
                        (_, i) => `{reconnect_exact_${i}}channel`,
                    ),
                );
                const messageAfter = "message_after_kill";

                const callbackMessages: PubSubMsg[] = [];
                const callback =
                    method === MethodTesting.Callback ? newMessage : undefined;
                const context =
                    method === MethodTesting.Callback
                        ? callbackMessages
                        : undefined;

                if (subscriptionMethod === Mode.Config) {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        channels,
                        undefined,
                        undefined,
                        callback,
                        context,
                        undefined,
                        undefined,
                        clusterMode
                            ? cmeCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              }))
                            : cmdCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              })),
                    );
                } else {
                    listeningClient = await createPubsubClient(
                        clusterMode,
                        undefined,
                        undefined,
                        undefined,
                        callback,
                        context,
                        undefined,
                        undefined,
                        clusterMode
                            ? cmeCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              }))
                            : cmdCluster.ports().map((port) => ({
                                  host: "localhost",
                                  port,
                              })),
                    );
                    await subscribeByMethod(
                        listeningClient,
                        channels,
                        subscriptionMethod,
                    );
                }

                if (clusterMode) {
                    publishingClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    publishingClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    channels,
                );

                // Kill connections
                await killConnections(publishingClient, null);

                // Give time for reconnect
                await new Promise((resolve) => setTimeout(resolve, 2000));

                // Wait for resubscription
                await waitForSubscriptionState(
                    listeningClient,
                    channels,
                    undefined,
                    undefined,
                    5000,
                );

                // Publish to all channels after reconnection
                for (const channel of channels) {
                    await publishingClient.publish(messageAfter, channel);
                }

                await new Promise((resolve) => setTimeout(resolve, 2000));

                // Verify all messages received
                const receivedChannels = new Set<string>();

                for (let index = 0; index < NUM_CHANNELS; index++) {
                    const msg = await getMessageByMethod(
                        method,
                        listeningClient,
                        callbackMessages,
                        index,
                    );
                    expect(msg).not.toBeNull();
                    expect(msg!.message).toBe(messageAfter);
                    expect(msg!.pattern).toBeNull();
                    receivedChannels.add(msg!.channel as string);
                }

                expect(receivedChannels.size).toBe(channels.size);

                // Verify all channels received messages
                for (const channel of channels) {
                    expect(receivedChannels.has(channel)).toBe(true);
                }

                await checkNoMessagesLeft(
                    method,
                    listeningClient,
                    callbackMessages,
                    NUM_CHANNELS,
                );
            } finally {
                if (listeningClient) {
                    await clientCleanup(
                        listeningClient,
                        // eslint-disable-next-line @typescript-eslint/no-explicit-any
                        clusterMode ? ({} as any) : undefined,
                    );
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that out-of-sync metric is recorded when subscription fails due to ACL.
     *
     * This test verifies that the subscription_out_of_sync_count metric increases
     * when a subscription fails due to ACL restrictions. After granting permissions,
     * the subscription should reconcile and the sync timestamp should update.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_subscription_metrics_on_acl_failure_%p",
        async (clusterMode) => {
            let listeningClient: TGlideClient | null = null;
            let adminClient: TGlideClient | null = null;

            const channel = `channel_acl_metrics_test_${Date.now()}`;
            const username = `mock_test_user_acl_metrics_${Date.now()}`;
            const password = "password_acl_metrics";

            try {
                // Create admin client
                if (clusterMode) {
                    adminClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    adminClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Create user without pubsub permissions
                const aclCommand = [
                    "ACL",
                    "SETUSER",
                    username,
                    "ON",
                    `>${password}`,
                    "~*",
                    "resetchannels",
                    "+@all",
                    "-@pubsub",
                ];

                if (clusterMode) {
                    await (adminClient as GlideClusterClient).customCommand(
                        aclCommand,
                        { route: "allNodes" },
                    );
                } else {
                    await adminClient.customCommand(aclCommand);
                }

                // Create listening client and authenticate with restricted user
                if (clusterMode) {
                    listeningClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    listeningClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Authenticate with restricted user
                if (clusterMode) {
                    await (listeningClient as GlideClusterClient).customCommand(
                        ["AUTH", username, password],
                        {
                            route: "allNodes",
                        },
                    );
                } else {
                    await listeningClient.customCommand([
                        "AUTH",
                        username,
                        password,
                    ]);
                }

                // Get initial metrics
                const initialStats =
                    (await listeningClient.getStatistics()) as Record<
                        string,
                        string
                    >;
                const initialOutOfSync = parseInt(
                    initialStats["subscription_out_of_sync_count"] || "0",
                );
                const initialSyncTimestamp = parseInt(
                    initialStats["subscription_last_sync_timestamp"] || "0",
                );

                // Subscribe using Lazy method (will fail due to ACL)
                await subscribeByMethod(
                    listeningClient,
                    new Set([channel]),
                    Mode.Lazy,
                );

                // Wait for reconciliation attempts
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Check that out-of-sync metric increased (reconciliation failed)
                const stats = (await listeningClient.getStatistics()) as Record<
                    string,
                    string
                >;
                const outOfSyncCount = parseInt(
                    stats["subscription_out_of_sync_count"] || "0",
                );

                expect(outOfSyncCount).toBeGreaterThan(initialOutOfSync);

                // Now grant pubsub permissions
                const aclGrantCommand = [
                    "ACL",
                    "SETUSER",
                    username,
                    "+@pubsub",
                    "allchannels",
                ];

                if (clusterMode) {
                    await (adminClient as GlideClusterClient).customCommand(
                        aclGrantCommand,
                        { route: "allNodes" },
                    );
                } else {
                    await adminClient.customCommand(aclGrantCommand);
                }

                // Wait for reconciliation to succeed (reconciliation happens every 5 secs)
                await waitForSubscriptionState(
                    listeningClient,
                    new Set([channel]),
                    undefined,
                    undefined,
                    6000,
                );

                // Verify sync timestamp was updated
                const finalStats =
                    (await listeningClient.getStatistics()) as Record<
                        string,
                        string
                    >;
                const finalSyncTimestamp = parseInt(
                    finalStats["subscription_last_sync_timestamp"] || "0",
                );

                expect(finalSyncTimestamp).toBeGreaterThan(
                    initialSyncTimestamp,
                );
            } finally {
                // Cleanup ACL user
                if (adminClient) {
                    const aclDeleteCommand = ["ACL", "DELUSER", username];

                    try {
                        if (clusterMode) {
                            await (
                                adminClient as GlideClusterClient
                            ).customCommand(aclDeleteCommand, {
                                route: "allNodes",
                            });
                        } else {
                            await adminClient.customCommand(aclDeleteCommand);
                        }
                    } catch {
                        // Ignore cleanup errors
                    }
                }

                if (listeningClient) {
                    listeningClient.close();
                }

                if (adminClient) {
                    adminClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that out-of-sync metric increments on repeated reconciliation failures.
     *
     * This test verifies that the subscription_out_of_sync_count metric increases
     * when reconciliation repeatedly fails due to ACL restrictions. Each failed
     * subscription attempt should increment the out-of-sync counter.
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_subscription_metrics_repeated_reconciliation_failures_%p",
        async (clusterMode) => {
            let listeningClient: TGlideClient | null = null;
            let adminClient: TGlideClient | null = null;

            const channel1 = `channel1_repeated_failures_${Date.now()}`;
            const channel2 = `channel2_repeated_failures_${Date.now()}`;
            const username = `mock_test_user_repeated_${Date.now()}`;
            const password = "password_repeated";

            try {
                // Create admin client
                if (clusterMode) {
                    adminClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    adminClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Create user WITHOUT pubsub permissions
                const aclCommand = [
                    "ACL",
                    "SETUSER",
                    username,
                    "ON",
                    `>${password}`,
                    "~*",
                    "resetchannels",
                    "+@all",
                    "-@pubsub",
                ];

                if (clusterMode) {
                    await (adminClient as GlideClusterClient).customCommand(
                        aclCommand,
                        { route: "allNodes" },
                    );
                } else {
                    await adminClient.customCommand(aclCommand);
                }

                // Create listening client
                if (clusterMode) {
                    listeningClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    listeningClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Authenticate with restricted user
                if (clusterMode) {
                    await (listeningClient as GlideClusterClient).customCommand(
                        ["AUTH", username, password],
                        {
                            route: "allNodes",
                        },
                    );
                } else {
                    await listeningClient.customCommand([
                        "AUTH",
                        username,
                        password,
                    ]);
                }

                // Get initial metrics
                const initialStats =
                    (await listeningClient.getStatistics()) as Record<
                        string,
                        string
                    >;
                const initialOutOfSync = parseInt(
                    initialStats["subscription_out_of_sync_count"] || "0",
                );

                // Subscribe to multiple channels (will fail due to ACL)
                const channels = [channel1, channel2];

                for (const channel of channels) {
                    try {
                        await subscribeByMethod(
                            listeningClient,
                            new Set([channel]),
                            Mode.Lazy,
                        );
                    } catch {
                        // Expected for blocking method - ignore
                    }
                }

                // Give time for async reconciliation to run and increase the metric
                await new Promise((resolve) => setTimeout(resolve, 500));

                // Check that out-of-sync increased multiple times
                const stats = (await listeningClient.getStatistics()) as Record<
                    string,
                    string
                >;
                const outOfSyncCount = parseInt(
                    stats["subscription_out_of_sync_count"] || "0",
                );

                // Should have at least 2 out-of-sync events (one per failed reconciliation)
                expect(outOfSyncCount).toBeGreaterThanOrEqual(
                    initialOutOfSync + 2,
                );
            } finally {
                // Cleanup ACL user
                if (adminClient) {
                    const aclDeleteCommand = ["ACL", "DELUSER", username];

                    try {
                        if (clusterMode) {
                            await (
                                adminClient as GlideClusterClient
                            ).customCommand(aclDeleteCommand, {
                                route: "allNodes",
                            });
                        } else {
                            await adminClient.customCommand(aclDeleteCommand);
                        }
                    } catch {
                        // Ignore cleanup errors
                    }
                }

                if (listeningClient) {
                    listeningClient.close();
                }

                if (adminClient) {
                    adminClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that sync timestamp updates on successful subscription.
     *
     * This test verifies that the subscription_last_sync_timestamp metric updates
     * after a successful subscription. It subscribes to two channels to ensure
     * at least one full reconciliation cycle and one successful timestamp update.
     *
     * Validates: Requirements 10.8
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param subscriptionMethod - The subscription method (Lazy or Blocking).
     */
    it.each([
        [true, Mode.Lazy],
        [true, Mode.Blocking],
        [false, Mode.Lazy],
        [false, Mode.Blocking],
    ])(
        "test_subscription_sync_timestamp_metric_on_success_%p_%p",
        async (clusterMode, subscriptionMethod) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            const channel1 = `channel1_sync_timestamp_${Date.now()}`;
            const channel2 = `channel2_sync_timestamp_${Date.now()}`;
            const message = "message_1";

            try {
                // Create listening client without subscriptions
                if (clusterMode) {
                    listeningClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    listeningClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Create publishing client
                if (clusterMode) {
                    publishingClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    publishingClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Get initial statistics
                const initialStats =
                    (await listeningClient.getStatistics()) as Record<
                        string,
                        string
                    >;
                const initialTimestamp = parseInt(
                    initialStats["subscription_last_sync_timestamp"] || "0",
                );

                // Record time before first subscription
                const timeBeforeFirstSub = Date.now();

                // Subscribe to first channel
                await subscribeByMethod(
                    listeningClient,
                    new Set([channel1]),
                    subscriptionMethod,
                );

                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    new Set([channel1]),
                );

                // Subscribe to another channel - this ensures we will have at least 1 full reconciliation cycle
                // and 1 successful timestamp update before checking it
                await subscribeByMethod(
                    listeningClient,
                    new Set([channel2]),
                    subscriptionMethod,
                );

                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    subscriptionMethod,
                    new Set([channel1, channel2]),
                );

                // Check that timestamp was updated
                const statsAfterFirst =
                    (await listeningClient.getStatistics()) as Record<
                        string,
                        string
                    >;
                const timestampAfterFirst = parseInt(
                    statsAfterFirst["subscription_last_sync_timestamp"] || "0",
                );

                // Timestamp should be non-zero after successful subscription
                expect(timestampAfterFirst).toBeGreaterThan(0);

                // The timestamp should be at least as recent as when we started the subscription.
                // This verifies that the sync timestamp was updated during our subscription process.
                // Note: We use >= because the timestamp might have been set during the first subscribe
                // call, which happened after timeBeforeFirstSub was recorded.
                expect(timestampAfterFirst).toBeGreaterThanOrEqual(
                    timeBeforeFirstSub,
                );

                // If the initial timestamp was 0 (no previous sync), verify it increased
                // If the initial timestamp was non-zero (from previous test), the >= check above
                // is sufficient to verify the timestamp is current
                if (initialTimestamp === 0) {
                    expect(timestampAfterFirst).toBeGreaterThan(
                        initialTimestamp,
                    );
                }

                // Verify subscription works by publishing and receiving a message
                await publishingClient.publish(message, channel1);
                await new Promise((resolve) => setTimeout(resolve, 1000));
                const msg = await listeningClient.getPubSubMessage();
                expect(msg?.message?.toString()).toBe(message);
            } finally {
                if (listeningClient) {
                    listeningClient.close();
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that blocking subscribe times out when reconciliation can't complete.
     *
     * This test verifies the different timeout behaviors between Lazy and Blocking
     * subscription modes. When a user lacks pubsub permissions:
     * - Lazy subscribe should succeed (desired state updated)
     * - Blocking subscribe should timeout (reconciliation can't complete)
     *
     * Validates: Requirements 10.9
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_lazy_vs_blocking_timeout_%p",
        async (clusterMode) => {
            let client: TGlideClient | null = null;
            let adminClient: TGlideClient | null = null;

            const username = `mock_test_user_timeout_${Date.now()}`;
            const password = "password_timeout";
            const channel = `channel_timeout_test_${Date.now()}`;

            try {
                // Create admin client
                if (clusterMode) {
                    adminClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    adminClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Create user without pubsub permissions
                const aclCommand = [
                    "ACL",
                    "SETUSER",
                    username,
                    "ON",
                    `>${password}`,
                    "~*",
                    "resetchannels",
                    "+@all",
                    "-@pubsub",
                ];

                if (clusterMode) {
                    await (adminClient as GlideClusterClient).customCommand(
                        aclCommand,
                        { route: "allNodes" },
                    );
                } else {
                    await adminClient.customCommand(aclCommand);
                }

                // Create client
                if (clusterMode) {
                    client = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    client = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Authenticate with restricted user
                if (clusterMode) {
                    await (client as GlideClusterClient).customCommand(
                        ["AUTH", username, password],
                        { route: "allNodes" },
                    );
                } else {
                    await client.customCommand(["AUTH", username, password]);
                }

                // Lazy subscribe should succeed (desired state updated)
                const lazyResult = await client.subscribeLazy(
                    new Set([channel]),
                );
                expect(lazyResult).toBeNull();

                // Blocking subscribe should timeout
                await expect(
                    client.subscribe(new Set([channel]), 1000),
                ).rejects.toThrow(TimeoutError);
            } finally {
                // Cleanup ACL user
                if (adminClient) {
                    const aclDeleteCommand = ["ACL", "DELUSER", username];

                    try {
                        if (clusterMode) {
                            await (
                                adminClient as GlideClusterClient
                            ).customCommand(aclDeleteCommand, {
                                route: "allNodes",
                            });
                        } else {
                            await adminClient.customCommand(aclDeleteCommand);
                        }
                    } catch {
                        // Ignore cleanup errors
                    }
                }

                if (client) {
                    client.close();
                }

                if (adminClient) {
                    adminClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test mixing Config, Lazy, and Blocking subscriptions across all subscription types
     * (Exact, Pattern, and Sharded for cluster mode).
     *
     * This comprehensive test verifies that all subscription methods work together
     * for all subscription types. A client is created with Config subscriptions,
     * then Lazy and Blocking subscriptions are added dynamically.
     *
     * Validates: Requirements 10.11
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_mixed_subscription_methods_all_types_%p",
        async (clusterMode) => {
            // Sharded channels require Valkey 7.0+
            if (clusterMode && cmeCluster.checkIfServerVersionLessThan("7.0.0"))
                return;

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                // Create unique names for each combination
                const prefix = "mixed_sub_types";

                // Exact channels
                const exactConfig = `exact_config_${prefix}`;
                const exactLazy = `exact_lazy_${prefix}`;
                const exactBlocking = `exact_blocking_${prefix}`;

                // Pattern subscriptions
                const patternConfig = `pattern_config_${prefix}_*`;
                const patternLazy = `pattern_lazy_${prefix}_*`;
                const patternBlocking = `pattern_blocking_${prefix}_*`;

                // Channels that match the patterns
                const patternConfigChannel = `pattern_config_${prefix}_match`;
                const patternLazyChannel = `pattern_lazy_${prefix}_match`;
                const patternBlockingChannel = `pattern_blocking_${prefix}_match`;

                // Sharded channels (cluster mode only)
                const shardedConfig = clusterMode
                    ? `sharded_config_${prefix}`
                    : null;
                const shardedLazy = clusterMode
                    ? `sharded_lazy_${prefix}`
                    : null;
                const shardedBlocking = clusterMode
                    ? `sharded_blocking_${prefix}`
                    : null;

                const addresses = clusterMode
                    ? cmeCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }))
                    : cmdCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }));

                // Create client with Config subscriptions
                listeningClient = await createPubsubClient(
                    clusterMode,
                    new Set([exactConfig]),
                    new Set([patternConfig]),
                    clusterMode && shardedConfig
                        ? new Set([shardedConfig])
                        : undefined,
                    undefined, // no callback
                    undefined, // no context
                    undefined, // default protocol
                    undefined, // default timeout
                    addresses,
                );

                // Create publishing client
                if (clusterMode) {
                    publishingClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    publishingClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Wait for config subscriptions
                await waitForSubscriptionState(
                    listeningClient,
                    new Set([exactConfig]),
                    new Set([patternConfig]),
                    clusterMode && shardedConfig
                        ? new Set([shardedConfig])
                        : undefined,
                );

                // Add Lazy subscriptions
                await subscribeByMethod(
                    listeningClient,
                    new Set([exactLazy]),
                    Mode.Lazy,
                );
                await psubscribeByMethod(
                    listeningClient,
                    new Set([patternLazy]),
                    Mode.Lazy,
                );

                if (clusterMode && shardedLazy) {
                    await ssubscribeByMethod(
                        listeningClient as GlideClusterClient,
                        new Set([shardedLazy]),
                        Mode.Lazy,
                    );
                }

                // Add Blocking subscriptions
                await subscribeByMethod(
                    listeningClient,
                    new Set([exactBlocking]),
                    Mode.Blocking,
                );
                await psubscribeByMethod(
                    listeningClient,
                    new Set([patternBlocking]),
                    Mode.Blocking,
                );

                if (clusterMode && shardedBlocking) {
                    await ssubscribeByMethod(
                        listeningClient as GlideClusterClient,
                        new Set([shardedBlocking]),
                        Mode.Blocking,
                    );
                }

                // Wait for all subscriptions
                const allExact = new Set([
                    exactConfig,
                    exactLazy,
                    exactBlocking,
                ]);
                const allPatterns = new Set([
                    patternConfig,
                    patternLazy,
                    patternBlocking,
                ]);
                const allSharded =
                    clusterMode &&
                    shardedConfig &&
                    shardedLazy &&
                    shardedBlocking
                        ? new Set([shardedConfig, shardedLazy, shardedBlocking])
                        : undefined;

                await waitForSubscriptionState(
                    listeningClient,
                    allExact,
                    allPatterns,
                    allSharded,
                );

                // Publish messages
                type MessageToPublish = [string, string, boolean];
                const messagesToPublish: MessageToPublish[] = [
                    // [channel, message, is_sharded]
                    [exactConfig, "msg_exact_config", false],
                    [exactLazy, "msg_exact_lazy", false],
                    [exactBlocking, "msg_exact_blocking", false],
                    [patternConfigChannel, "msg_pattern_config", false],
                    [patternLazyChannel, "msg_pattern_lazy", false],
                    [patternBlockingChannel, "msg_pattern_blocking", false],
                ];

                if (
                    clusterMode &&
                    shardedConfig &&
                    shardedLazy &&
                    shardedBlocking
                ) {
                    messagesToPublish.push(
                        [shardedConfig, "msg_sharded_config", true],
                        [shardedLazy, "msg_sharded_lazy", true],
                        [shardedBlocking, "msg_sharded_blocking", true],
                    );
                }

                for (const [channel, message, isSharded] of messagesToPublish) {
                    if (isSharded) {
                        await (publishingClient as GlideClusterClient).publish(
                            message,
                            channel,
                            true,
                        );
                    } else {
                        await publishingClient.publish(message, channel);
                    }
                }

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Collect all messages
                const expectedCount = clusterMode ? 9 : 6;
                const receivedMessages: Record<string, string> = {};

                for (let i = 0; i < expectedCount; i++) {
                    const msg = await listeningClient.getPubSubMessage();
                    const channel = (msg.channel as Buffer).toString();
                    const message = (msg.message as Buffer).toString();
                    receivedMessages[channel] = message;
                }

                // Verify exact channel messages
                expect(receivedMessages[exactConfig]).toBe("msg_exact_config");
                expect(receivedMessages[exactLazy]).toBe("msg_exact_lazy");
                expect(receivedMessages[exactBlocking]).toBe(
                    "msg_exact_blocking",
                );

                // Verify pattern channel messages
                expect(receivedMessages[patternConfigChannel]).toBe(
                    "msg_pattern_config",
                );
                expect(receivedMessages[patternLazyChannel]).toBe(
                    "msg_pattern_lazy",
                );
                expect(receivedMessages[patternBlockingChannel]).toBe(
                    "msg_pattern_blocking",
                );

                // Verify sharded channel messages (cluster mode only)
                if (
                    clusterMode &&
                    shardedConfig &&
                    shardedLazy &&
                    shardedBlocking
                ) {
                    expect(receivedMessages[shardedConfig]).toBe(
                        "msg_sharded_config",
                    );
                    expect(receivedMessages[shardedLazy]).toBe(
                        "msg_sharded_lazy",
                    );
                    expect(receivedMessages[shardedBlocking]).toBe(
                        "msg_sharded_blocking",
                    );
                }

                // Verify no extra messages
                const extraMessage = listeningClient.tryGetPubSubMessage();
                expect(extraMessage).toBeNull();
            } finally {
                if (listeningClient) {
                    listeningClient.close();
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that Config subscription method with empty sets is a silent no-op.
     *
     * Unlike Lazy and Blocking methods which may raise errors for empty sets,
     * the Config method silently ignores empty subscription sets. This happens
     * because empty sets are filtered out before reaching the Rust core.
     *
     * Validates: Requirements 10.10
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_config_subscription_with_empty_set_is_allowed_%p",
        async (clusterMode) => {
            let client: TGlideClient | null = null;

            try {
                // Create client with empty subscription sets via Config
                // This should NOT raise an error - empty sets are filtered out
                client = await createPubsubClient(
                    clusterMode,
                    new Set<string>(), // empty channels
                    new Set<string>(), // empty patterns
                    clusterMode ? new Set<string>() : undefined, // empty sharded (cluster only)
                    undefined, // no callback
                    undefined, // no context
                    undefined, // default protocol
                    undefined, // default timeout
                    clusterMode
                        ? cmeCluster.ports().map((port) => ({
                              host: "localhost",
                              port,
                          }))
                        : cmdCluster.ports().map((port) => ({
                              host: "localhost",
                              port,
                          })),
                );

                // Verify client was created successfully and state is empty
                await waitForSubscriptionState(
                    client,
                    new Set<string>(), // expected empty channels
                    new Set<string>(), // expected empty patterns
                    clusterMode ? new Set<string>() : undefined, // expected empty sharded (cluster only)
                );

                // Additional verification: client should be functional
                // Try a simple command to ensure the client works
                const pingResult = await client.ping();
                expect(pingResult).toBe("PONG");
            } finally {
                if (client) {
                    client.close();
                }
            }
        },
        TIMEOUT,
    );

    // Two-dimensional test cases for unsubscribe all tests: [cluster_mode, subscription_method]
    // Only Lazy and Blocking can dynamically unsubscribe (Config cannot)
    const testCasesUnsubscribeAll: [boolean, number][] = [
        [true, Mode.Lazy],
        [true, Mode.Blocking],
        [false, Mode.Lazy],
        [false, Mode.Blocking],
    ];

    /**
     * Test unsubscribing from all channels/patterns/sharded using null parameter.
     *
     * This test verifies that passing null to unsubscribe methods removes all
     * subscriptions of that type. Tests all three subscription types in a single test.
     *
     * Validates: Python test_unsubscribe_all_subscription_types
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param subscriptionMethod - Specifies the subscription method (Lazy, Blocking).
     */
    it.each(testCasesUnsubscribeAll)(
        "test_unsubscribe_all_subscription_types_%p_%p",
        async (clusterMode, subscriptionMethod) => {
            // Sharded channels require Valkey 7.0+
            if (clusterMode && cmeCluster.checkIfServerVersionLessThan("7.0.0"))
                return;

            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const exactChannels = new Set([
                    "exact_unsub_all_0",
                    "exact_unsub_all_1",
                    "exact_unsub_all_2",
                ]);
                const patterns = new Set([
                    "pattern_unsub_all_0.*",
                    "pattern_unsub_all_1.*",
                    "pattern_unsub_all_2.*",
                ]);
                const shardedChannels = clusterMode
                    ? new Set([
                          "sharded_unsub_all_0",
                          "sharded_unsub_all_1",
                          "sharded_unsub_all_2",
                      ])
                    : undefined;
                const message = "test_message";

                const addresses = clusterMode
                    ? cmeCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }))
                    : cmdCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }));

                // Create client with Config subscriptions (so we have something to unsubscribe from)
                listeningClient = await createPubsubClient(
                    clusterMode,
                    exactChannels,
                    patterns,
                    shardedChannels,
                    undefined, // no callback
                    undefined, // no context
                    undefined, // default protocol
                    undefined, // default timeout
                    addresses,
                );

                // Create publishing client
                if (clusterMode) {
                    publishingClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    publishingClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Verify all subscriptions are active
                await waitForSubscriptionStateIfNeeded(
                    listeningClient,
                    Mode.Config,
                    exactChannels,
                    patterns,
                    shardedChannels,
                );

                // Unsubscribe from all (pass null to unsubscribe from all of each type)
                await unsubscribeByMethod(
                    listeningClient,
                    null,
                    subscriptionMethod,
                );
                await punsubscribeByMethod(
                    listeningClient,
                    null,
                    subscriptionMethod,
                );

                if (clusterMode) {
                    await sunsubscribeByMethod(
                        listeningClient as GlideClusterClient,
                        null,
                        subscriptionMethod,
                    );
                }

                // Wait for subscriptions to be cleared
                await waitForSubscriptionState(
                    listeningClient,
                    new Set<string>(),
                    new Set<string>(),
                    clusterMode ? new Set<string>() : undefined,
                );

                // Publish to all types - none should be received
                for (const channel of exactChannels) {
                    await publishingClient.publish(message, channel);
                }

                for (const pattern of patterns) {
                    const matchingChannel = pattern.replace(/\*/g, "test");
                    await publishingClient.publish(message, matchingChannel);
                }

                if (clusterMode && shardedChannels) {
                    for (const channel of shardedChannels) {
                        await (publishingClient as GlideClusterClient).publish(
                            message,
                            channel,
                            true,
                        );
                    }
                }

                // Allow messages to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Verify no messages received
                await checkNoMessagesLeft(
                    MethodTesting.Async,
                    listeningClient,
                    null,
                    0,
                );
            } finally {
                if (listeningClient) {
                    listeningClient.close();
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    // Two-dimensional test cases for empty set error tests: [cluster_mode, subscription_method]
    const testCasesEmptySet: [boolean, number][] = [
        [true, Mode.Lazy],
        [true, Mode.Blocking],
        [false, Mode.Lazy],
        [false, Mode.Blocking],
    ];

    /**
     * Test that subscribing with an empty set raises an error for dynamic subscription methods.
     *
     * This test verifies that Lazy and Blocking subscription methods raise RequestError
     * when called with an empty set of channels/patterns.
     *
     * Validates: Python test_subscribe_empty_set_raises_error
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     * @param subscriptionMethod - Specifies the subscription method (Lazy, Blocking).
     */
    it.each(testCasesEmptySet)(
        "test_subscribe_empty_set_raises_error_%p_%p",
        async (clusterMode, subscriptionMethod) => {
            // Sharded channels require Valkey 7.0+
            if (clusterMode && cmeCluster.checkIfServerVersionLessThan("7.0.0"))
                return;

            let client: TGlideClient | null = null;

            try {
                const addresses = clusterMode
                    ? cmeCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }))
                    : cmdCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }));

                // Create client without subscriptions
                client = await createPubsubClient(
                    clusterMode,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    addresses,
                );

                // Verify initial state is empty
                await waitForSubscriptionState(
                    client,
                    new Set<string>(),
                    new Set<string>(),
                    clusterMode ? new Set<string>() : undefined,
                );

                // Test subscribe with empty set - should throw error
                if (subscriptionMethod === Mode.Lazy) {
                    await expect(
                        client.subscribeLazy(new Set<string>()),
                    ).rejects.toThrow();
                } else {
                    await expect(
                        client.subscribe(new Set<string>(), 5000),
                    ).rejects.toThrow();
                }

                // Test psubscribe with empty set - should throw error
                if (subscriptionMethod === Mode.Lazy) {
                    await expect(
                        client.psubscribeLazy(new Set<string>()),
                    ).rejects.toThrow();
                } else {
                    await expect(
                        client.psubscribe(new Set<string>(), 5000),
                    ).rejects.toThrow();
                }

                // Test ssubscribe with empty set (cluster only) - should throw error
                if (clusterMode) {
                    if (subscriptionMethod === Mode.Lazy) {
                        await expect(
                            (client as GlideClusterClient).ssubscribeLazy(
                                new Set<string>(),
                            ),
                        ).rejects.toThrow();
                    } else {
                        await expect(
                            (client as GlideClusterClient).ssubscribe(
                                new Set<string>(),
                                5000,
                            ),
                        ).rejects.toThrow();
                    }
                }

                // Verify state is still empty after failed subscription attempts
                await waitForSubscriptionState(
                    client,
                    new Set<string>(),
                    new Set<string>(),
                    clusterMode ? new Set<string>() : undefined,
                );
            } finally {
                if (client) {
                    client.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that pubsubReconciliationIntervalMs config bounds reconciliation staleness.
     *
     * Configures a 1 second interval, then samples multiple intervals between
     * consecutive reconciliation timestamp updates. Validates that observed intervals
     * are frequently below the upper tolerance bound.
     *
     * Validates: Python test_pubsub_reconciliation_interval_config
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_pubsub_reconciliation_interval_config_%p",
        async (clusterMode) => {
            let listeningClient: TGlideClient | null = null;

            try {
                const intervalMs = 1000; // 1 second interval
                const pollIntervalMs = 100; // 100ms polling

                const addresses = clusterMode
                    ? cmeCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }))
                    : cmdCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }));

                // Create client with configured reconciliation interval
                if (clusterMode) {
                    listeningClient = await GlideClusterClient.createClient({
                        addresses,
                        protocol: ProtocolVersion.RESP3,
                        advancedConfiguration: {
                            pubsubReconciliationIntervalMs: intervalMs,
                        },
                    });
                } else {
                    listeningClient = await GlideClient.createClient({
                        addresses,
                        protocol: ProtocolVersion.RESP3,
                        advancedConfiguration: {
                            pubsubReconciliationIntervalMs: intervalMs,
                        },
                    });
                }

                // Helper to poll for timestamp change
                const pollForTimestampChange = async (
                    previousTs: number,
                    timeoutS = 5.0,
                ): Promise<number> => {
                    const startTime = Date.now();

                    while ((Date.now() - startTime) / 1000 < timeoutS) {
                        const stats =
                            (await listeningClient!.getStatistics()) as Record<
                                string,
                                string
                            >;
                        const currentTs = parseInt(
                            stats["subscription_last_sync_timestamp"] || "0",
                            10,
                        );

                        if (currentTs !== previousTs) {
                            return currentTs;
                        }

                        await new Promise((resolve) =>
                            setTimeout(resolve, pollIntervalMs),
                        );
                    }

                    throw new Error(
                        `Sync timestamp did not change within ${timeoutS}s. Previous: ${previousTs}`,
                    );
                };

                // Get initial timestamp
                const initialStats =
                    (await listeningClient.getStatistics()) as Record<
                        string,
                        string
                    >;
                const initialTs = parseInt(
                    initialStats["subscription_last_sync_timestamp"] || "0",
                    10,
                );

                // Wait for first sync event after client creation
                const firstSyncTs = await pollForTimestampChange(initialTs);

                const upperBoundMs = intervalMs * 1.5;
                const maxSamples = 8;
                const requiredWithinUpperBoundSamples = 4;

                // Collect several consecutive intervals
                const sampledIntervalsMs: number[] = [];
                let withinUpperBoundCount = 0;
                let previousSyncTs = firstSyncTs;

                for (let i = 0; i < maxSamples; i++) {
                    const currentSyncTs = await pollForTimestampChange(
                        previousSyncTs,
                        3.0,
                    );
                    const sampledIntervalMs = currentSyncTs - previousSyncTs;
                    sampledIntervalsMs.push(sampledIntervalMs);
                    previousSyncTs = currentSyncTs;

                    if (sampledIntervalMs <= upperBoundMs) {
                        withinUpperBoundCount++;

                        if (
                            withinUpperBoundCount >=
                            requiredWithinUpperBoundSamples
                        ) {
                            break;
                        }
                    }
                }

                expect(withinUpperBoundCount).toBeGreaterThanOrEqual(
                    requiredWithinUpperBoundSamples,
                );
            } finally {
                if (listeningClient) {
                    listeningClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that lazyConnect with preconfigured pubsub subscriptions subscribes on first command.
     *
     * This test verifies that when a client is created with lazyConnect: true and
     * pubsubSubscriptions configured, the subscriptions are NOT established until
     * the first command is executed.
     *
     * Validates: Reviewer feedback - lazy client with preconfigured subscriptions
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_lazy_connect_with_preconfigured_subscriptions_%p",
        async (clusterMode) => {
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const channel = `lazy_preconfigured_channel_${Date.now()}`;
                const message = "lazy_preconfigured_message";

                const addresses = clusterMode
                    ? cmeCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }))
                    : cmdCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }));

                // Create pubsub subscription config
                const pubsubSubscriptions = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([channel]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel]),
                    },
                );

                // Create client with lazyConnect and pubsub subscriptions
                if (clusterMode) {
                    listeningClient = await GlideClusterClient.createClient({
                        addresses,
                        protocol: ProtocolVersion.RESP3,
                        lazyConnect: true,
                        pubsubSubscriptions,
                    });
                } else {
                    listeningClient = await GlideClient.createClient({
                        addresses,
                        protocol: ProtocolVersion.RESP3,
                        lazyConnect: true,
                        pubsubSubscriptions,
                    });
                }

                // At this point, connection should NOT be established yet
                // Execute first command to trigger connection
                await listeningClient.ping();

                // Now verify subscriptions are established
                await waitForSubscriptionState(
                    listeningClient,
                    new Set([channel]),
                    undefined,
                    undefined,
                );

                // Create publishing client
                if (clusterMode) {
                    publishingClient = await GlideClusterClient.createClient(
                        getOptions(clusterMode),
                    );
                } else {
                    publishingClient = await GlideClient.createClient(
                        getOptions(clusterMode),
                    );
                }

                // Publish message
                await publishingClient.publish(message, channel);

                // Allow message to propagate
                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Verify message received
                const pubsubMessage = await listeningClient.getPubSubMessage();
                expect(pubsubMessage.message).toEqual(message);
                expect(pubsubMessage.channel).toEqual(channel);
            } finally {
                if (listeningClient) {
                    listeningClient.close();
                }

                if (publishingClient) {
                    publishingClient.close();
                }
            }
        },
        TIMEOUT,
    );

    /**
     * Test that getSubscriptions() method correctly parses the GlideRecord response format.
     *
     * This test verifies that the parseGetSubscriptionsResponse function correctly handles
     * the GlideRecord format returned by the Rust core (array of {key, value} objects with
     * string keys like "Exact", "Pattern", "Sharded").
     *
     * @param clusterMode - Indicates if the test should be run in cluster mode.
     */
    it.each([true, false])(
        "test_getSubscriptions_parses_response_correctly_%p",
        async (clusterMode) => {
            let client: TGlideClient | undefined;

            try {
                const addresses = clusterMode
                    ? cmeCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }))
                    : cmdCluster.ports().map((port) => ({
                          host: "localhost",
                          port,
                      }));

                const channel = getRandomKey();
                const pattern = `${getRandomKey()}*`;

                // Create pubsub subscription config with exact and pattern subscriptions
                const pubsubSubscriptions = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set([channel]),
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([pattern]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set([channel]),
                        [GlideClientConfiguration.PubSubChannelModes.Pattern]:
                            new Set([pattern]),
                    },
                );

                // Create client with subscriptions
                if (clusterMode) {
                    client = await GlideClusterClient.createClient({
                        addresses,
                        protocol: ProtocolVersion.RESP3,
                        pubsubSubscriptions,
                    });
                } else {
                    client = await GlideClient.createClient({
                        addresses,
                        protocol: ProtocolVersion.RESP3,
                        pubsubSubscriptions,
                    });
                }

                // Wait for subscriptions to be established
                await waitForSubscriptionState(
                    client,
                    new Set([channel]),
                    new Set([pattern]),
                    undefined,
                );

                // Call getSubscriptions() method - this tests the parseGetSubscriptionsResponse fix
                const state = await client.getSubscriptions();

                // Verify the response structure is correct
                expect(state).toHaveProperty("desiredSubscriptions");
                expect(state).toHaveProperty("actualSubscriptions");

                // Verify exact channel subscriptions
                if (clusterMode) {
                    const exactMode =
                        GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact;
                    const patternMode =
                        GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern;

                    // Check desired subscriptions
                    expect(state.desiredSubscriptions[exactMode]).toBeDefined();
                    expect(
                        state.desiredSubscriptions[exactMode]?.has(channel),
                    ).toBe(true);
                    expect(
                        state.desiredSubscriptions[patternMode],
                    ).toBeDefined();
                    expect(
                        state.desiredSubscriptions[patternMode]?.has(pattern),
                    ).toBe(true);

                    // Check actual subscriptions
                    expect(state.actualSubscriptions[exactMode]).toBeDefined();
                    expect(
                        state.actualSubscriptions[exactMode]?.has(channel),
                    ).toBe(true);
                    expect(
                        state.actualSubscriptions[patternMode],
                    ).toBeDefined();
                    expect(
                        state.actualSubscriptions[patternMode]?.has(pattern),
                    ).toBe(true);
                } else {
                    const exactMode =
                        GlideClientConfiguration.PubSubChannelModes.Exact;
                    const patternMode =
                        GlideClientConfiguration.PubSubChannelModes.Pattern;

                    // Check desired subscriptions
                    expect(state.desiredSubscriptions[exactMode]).toBeDefined();
                    expect(
                        state.desiredSubscriptions[exactMode]?.has(channel),
                    ).toBe(true);
                    expect(
                        state.desiredSubscriptions[patternMode],
                    ).toBeDefined();
                    expect(
                        state.desiredSubscriptions[patternMode]?.has(pattern),
                    ).toBe(true);

                    // Check actual subscriptions
                    expect(state.actualSubscriptions[exactMode]).toBeDefined();
                    expect(
                        state.actualSubscriptions[exactMode]?.has(channel),
                    ).toBe(true);
                    expect(
                        state.actualSubscriptions[patternMode],
                    ).toBeDefined();
                    expect(
                        state.actualSubscriptions[patternMode]?.has(pattern),
                    ).toBe(true);
                }
            } finally {
                if (client) {
                    client.close();
                }
            }
        },
        TIMEOUT,
    );
});
