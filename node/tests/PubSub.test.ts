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
} from "..";
import ValkeyCluster from "../../utils/TestUtils";
import {
    flushAndCloseClient,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

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
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact.toString()
                ) {
                    cmd = "UNSUBSCRIBE";
                } else if (
                    channelType ===
                    GlideClusterClientConfiguration.PubSubChannelModes.Pattern.toString()
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
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient;
            let publishingClient: TGlideClient;

            try {
                const channel = uuidv4() as GlideString;
                const message = uuidv4() as GlideString;
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
                        clusterMode ? pubSub! : undefined,
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
     */
    it.each(testCases)(
        `pubsub exact happy path binary test_%p%p`,
        async (clusterMode, method) => {
            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient;
            let publishingClient: TGlideClient;

            try {
                const channel = uuidv4() as GlideString;
                const message = uuidv4() as GlideString;
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

                expect(pubsubMessage!.message).toEqual(Buffer.from(message));
                expect(pubsubMessage!.channel).toEqual(Buffer.from(channel));
                expect(pubsubMessage!.pattern).toBeNull();

                await checkNoMessagesLeft(method, listeningClient, context, 1);
            } finally {
                await Promise.all([
                    clientCleanup(publishingClient!),
                    clientCleanup(
                        listeningClient!,
                        clusterMode ? pubSub! : undefined,
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
     */
    it.each([true, false])(
        "pubsub exact happy path coexistence test_%p",
        async (clusterMode) => {
            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                const channel = uuidv4() as GlideString;
                const message = uuidv4() as GlideString;
                const message2 = uuidv4() as GlideString;

                pubSub = createPubSubSubscription(
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
                        clusterMode ? pubSub! : undefined,
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
     */
    it.each(testCases)(
        "pubsub exact happy path many channels test_%p_%p",
        async (clusterMode, method) => {
            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";

            try {
                // Create a map of channels to random messages with shard prefix
                const channelsAndMessages: [GlideString, GlideString][] = [];

                for (let i = 0; i < NUM_CHANNELS; i++) {
                    const channel = `${shardPrefix}${uuidv4()}`;
                    const message = uuidv4();
                    channelsAndMessages.push([channel, message]);
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set(
                            channelsAndMessages.map((a) => a[0].toString()),
                        ),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(
                                channelsAndMessages.map((a) => a[0].toString()),
                            ),
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
                              clusterMode ? pubSub! : undefined,
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
     */
    it.each([true, false])(
        "pubsub exact happy path many channels coexistence test_%p",
        async (clusterMode) => {
            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";

            try {
                // Create a map of channels to random messages with shard prefix
                const channelsAndMessages: [GlideString, GlideString][] = [];

                for (let i = 0; i < NUM_CHANNELS; i++) {
                    const channel = `${shardPrefix}${uuidv4()}`;
                    const message = uuidv4();
                    channelsAndMessages.push([channel, message]);
                }

                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set(
                            channelsAndMessages.map((a) => a[0].toString()),
                        ),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(
                                channelsAndMessages.map((a) => a[0].toString()),
                            ),
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
                              clusterMode ? pubSub! : undefined,
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
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const channel = uuidv4() as GlideString;
            const message = uuidv4() as GlideString;
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([channel as string]),
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
                        ? clientCleanup(
                              listeningClient,
                              clusterMode ? pubSub! : undefined,
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
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const channel = uuidv4() as GlideString;
            const message = uuidv4() as GlideString;
            const message2 = uuidv4() as GlideString;

            try {
                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    true,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set([channel as string]),
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
                        ? clientCleanup(listeningClient, pubSub!)
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
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;
            const NUM_CHANNELS = 256;
            const shardPrefix = "{same-shard}";
            const publishResponse = 1;

            // Create a map of channels to random messages with shard prefix
            const channelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const channel = `${shardPrefix}${uuidv4()}`;
                const message = uuidv4();
                channelsAndMessages.push([channel, message]);
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set(
                            channelsAndMessages.map((a) => a[0].toString()),
                        ),
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
                        ? clientCleanup(
                              listeningClient,
                              clusterMode ? pubSub! : undefined,
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
            const channels: [GlideString, GlideString][] = [
                [`{{channel}}:${uuidv4()}`, uuidv4()],
                [`{{channel}}:${uuidv4()}`, uuidv4()],
            ];

            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([PATTERN]),
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
                        ? clientCleanup(
                              listeningClient,
                              clusterMode ? pubSub! : undefined,
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
            const channels: [GlideString, GlideString][] = [
                [`{{channel}}:${uuidv4()}`, uuidv4()],
                [`{{channel}}:${uuidv4()}`, uuidv4()],
            ];

            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let listeningClient: TGlideClient | null = null;
            let publishingClient: TGlideClient | null = null;

            try {
                // Create PUBSUB subscription for the test
                pubSub = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([PATTERN]),
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
                        ? clientCleanup(
                              listeningClient,
                              clusterMode ? pubSub! : undefined,
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
            const channels: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const channel = `{{channel}}:${uuidv4()}`;
                const message = uuidv4();
                channels.push([channel, message]);
            }

            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([PATTERN]),
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
                        ? clientCleanup(
                              listeningClient,
                              clusterMode ? pubSub! : undefined,
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
            const exactChannelsAndMessages: [GlideString, GlideString][] = [];
            const patternChannelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${uuidv4()}`;
                const patternChannel = `{{pattern}}:${uuidv4()}`;
                const exactMessage = uuidv4();
                const patternMessage = uuidv4();

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

            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set(
                            exactChannelsAndMessages.map((a) =>
                                a[0].toString(),
                            ),
                        ),
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([PATTERN]),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(
                                exactChannelsAndMessages.map((a) =>
                                    a[0].toString(),
                                ),
                            ),
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
                              clusterMode ? pubSub! : undefined,
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
     */
    it.each(testCases)(
        "pubsub combined exact and pattern multiple clients test_%p_%p",
        async (clusterMode, method) => {
            const NUM_CHANNELS = 256;
            const PATTERN = "{{pattern}}:*";

            // Create dictionaries of channels and their corresponding messages
            const exactChannelsAndMessages: [GlideString, GlideString][] = [];
            const patternChannelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${uuidv4()}`;
                const patternChannel = `{{pattern}}:${uuidv4()}`;
                const exactMessage = uuidv4();
                const patternMessage = uuidv4();

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

            let pubSubExact:
                | GlideClusterClientConfiguration.PubSubSubscriptions
                | GlideClientConfiguration.PubSubSubscriptions
                | null = null;
            let pubSubPattern:
                | GlideClusterClientConfiguration.PubSubSubscriptions
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set(
                            exactChannelsAndMessages.map((a) =>
                                a[0].toString(),
                            ),
                        ),
                    },
                    {
                        [GlideClientConfiguration.PubSubChannelModes.Exact]:
                            new Set(
                                exactChannelsAndMessages.map((a) =>
                                    a[0].toString(),
                                ),
                            ),
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([PATTERN]),
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
                        ? clientCleanup(
                              listeningClientExact,
                              clusterMode ? pubSubExact! : undefined,
                          )
                        : Promise.resolve(),
                    listeningClientPattern
                        ? clientCleanup(
                              listeningClientPattern,
                              clusterMode ? pubSubPattern! : undefined,
                          )
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
            const exactChannelsAndMessages: [GlideString, GlideString][] = [];
            const patternChannelsAndMessages: [GlideString, GlideString][] = [];
            const shardedChannelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${uuidv4()}`;
                const patternChannel = `{{pattern}}:${uuidv4()}`;
                const shardedChannel = `${SHARD_PREFIX}:${uuidv4()}`;
                exactChannelsAndMessages.push([exactChannel, uuidv4()]);
                patternChannelsAndMessages.push([patternChannel, uuidv4()]);
                shardedChannelsAndMessages.push([shardedChannel, uuidv4()]);
            }

            const publishResponse = 1;
            let pubSub:
                | GlideClusterClientConfiguration.PubSubSubscriptions
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set(
                            exactChannelsAndMessages.map((a) =>
                                a[0].toString(),
                            ),
                        ),
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([PATTERN]),
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Sharded]: new Set(
                            shardedChannelsAndMessages.map((a) =>
                                a[0].toString(),
                            ),
                        ),
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
                              clusterMode ? pubSub! : undefined,
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
            const exactChannelsAndMessages: [GlideString, GlideString][] = [];
            const patternChannelsAndMessages: [GlideString, GlideString][] = [];
            const shardedChannelsAndMessages: [GlideString, GlideString][] = [];

            for (let i = 0; i < NUM_CHANNELS; i++) {
                const exactChannel = `{{channel}}:${uuidv4()}`;
                const patternChannel = `{{pattern}}:${uuidv4()}`;
                const shardedChannel = `${SHARD_PREFIX}:${uuidv4()}`;
                exactChannelsAndMessages.push([exactChannel, uuidv4()]);
                patternChannelsAndMessages.push([patternChannel, uuidv4()]);
                shardedChannelsAndMessages.push([shardedChannel, uuidv4()]);
            }

            const publishResponse = 1;
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
                // Setup PUBSUB for exact channels
                pubSubExact = createPubSubSubscription(
                    clusterMode,
                    {
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Exact]: new Set(
                            exactChannelsAndMessages.map((a) =>
                                a[0].toString(),
                            ),
                        ),
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
                        [GlideClusterClientConfiguration.PubSubChannelModes
                            .Pattern]: new Set([PATTERN]),
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
                            .Sharded]: new Set(
                            shardedChannelsAndMessages.map((a) =>
                                a[0].toString(),
                            ),
                        ),
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
            const MESSAGE_EXACT = uuidv4();
            const MESSAGE_PATTERN = uuidv4();

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
            const MESSAGE_EXACT = uuidv4();
            const MESSAGE_PATTERN = uuidv4();
            const MESSAGE_SHARDED = uuidv4();

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
         */
        it.each([true, false])(
            "test pubsub exact max size message_%p",
            async (clusterMode) => {
                let pubSub:
                    | GlideClusterClientConfiguration.PubSubSubscriptions
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
                            [GlideClusterClientConfiguration.PubSubChannelModes
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
                    await Promise.all([
                        listeningClient
                            ? clientCleanup(
                                  listeningClient,
                                  clusterMode ? pubSub! : undefined,
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
                    | GlideClusterClientConfiguration.PubSubSubscriptions
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
                            [GlideClusterClientConfiguration.PubSubChannelModes
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
                    await Promise.all([
                        listeningClient
                            ? clientCleanup(
                                  listeningClient,
                                  clusterMode ? pubSub! : undefined,
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
                    | GlideClusterClientConfiguration.PubSubSubscriptions
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
                            [GlideClusterClientConfiguration.PubSubChannelModes
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
                    await Promise.all([
                        listeningClient
                            ? clientCleanup(
                                  listeningClient,
                                  clusterMode ? pubSub! : undefined,
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
                    | GlideClusterClientConfiguration.PubSubSubscriptions
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
                            [GlideClusterClientConfiguration.PubSubChannelModes
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
                    await Promise.all([
                        listeningClient
                            ? clientCleanup(
                                  listeningClient,
                                  clusterMode ? pubSub! : undefined,
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
            const channel = uuidv4();
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
});
