/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { expect } from "@jest/globals";
import {
    GlideClusterClient,
    GlideClient,
    ProtocolVersion,
    PubSubMsg,
    TimeoutError,
} from "../build-ts";

type TGlideClient = GlideClient | GlideClusterClient;

/**
 * Enumeration for specifying the subscription mode.
 *
 * @property {number} Lazy - Non-blocking subscription that returns immediately without waiting for confirmation.
 *                           Subscriptions are made dynamically after client creation using subscribeLazy/psubscribeLazy/ssubscribeLazy.
 *                           Requires polling to verify subscriptions are established before publishing messages.
 *
 * @property {number} Blocking - Blocking subscription that waits for confirmation with a timeout.
 *                               Subscriptions are made dynamically after client creation using subscribe/psubscribe/ssubscribe.
 *                               Subscriptions are immediately verified after the blocking call returns.
 *
 * @property {number} Config - Subscriptions configured at client creation time via pubsubSubscriptions parameter.
 *                             Client is created with subscriptions already established.
 *                             Requires immediate verification that subscriptions are active before publishing messages.
 *                             Cannot dynamically subscribe or unsubscribe after client creation.
 */
export const Mode = {
    Lazy: 0,
    Blocking: 1,
    Config: 2,
};

interface SubscriptionEntry {
    key: string;
    value: Buffer[];
}

/**
 * Parse the GET_SUBSCRIPTIONS command response into structured subscription data.
 *
 * @param result - Raw response from GET_SUBSCRIPTIONS command
 * @returns Object containing exact, pattern, and sharded subscription arrays as Buffers
 */
export function parseActualSubscriptions(result: unknown): {
    exact: Buffer[];
    pattern: Buffer[];
    sharded: Buffer[];
} {
    // Result format: ["desired", [{key, value}, ...], "actual", [{key, value}, ...]]
    const actualArray = (result as unknown[])?.[3] as
        | SubscriptionEntry[]
        | undefined;

    if (!Array.isArray(actualArray)) {
        return { exact: [], pattern: [], sharded: [] };
    }

    const findChannels = (key: string): Buffer[] => {
        const entry = actualArray.find((e) => e.key === key);
        return Array.isArray(entry?.value) ? entry.value : [];
    };

    return {
        exact: findChannels("Exact"),
        pattern: findChannels("Pattern"),
        sharded: findChannels("Sharded"),
    };
}

/**
 * Wait for subscription state to match expected values by polling GET_SUBSCRIPTIONS.
 *
 * @param client - The Glide client
 * @param expectedChannels - Expected exact channel subscriptions (undefined = don't check)
 * @param expectedPatterns - Expected pattern subscriptions (undefined = don't check)
 * @param expectedSharded - Expected sharded channel subscriptions (undefined = don't check)
 * @param timeoutMs - Timeout in milliseconds (default: 5000ms)
 * @param pollInterval - How often to poll state in milliseconds (default: 100ms)
 * @returns Object with current actual subscription state (channels, patterns, sharded as Sets of strings)
 * @throws TimeoutError if expected state not reached within timeout
 */
export async function waitForSubscriptionState(
    client: TGlideClient,
    expectedChannels?: Set<string>,
    expectedPatterns?: Set<string>,
    expectedSharded?: Set<string>,
    timeoutMs = 5000,
    pollInterval = 100,
): Promise<{
    channels: Set<string>;
    patterns: Set<string>;
    sharded: Set<string>;
}> {
    const startTime = Date.now();
    let lastActualState: {
        channels: Set<string>;
        patterns: Set<string>;
        sharded: Set<string>;
    } | null = null;

    while (true) {
        const elapsed = Date.now() - startTime;

        if (elapsed > timeoutMs) {
            let errorMsg = `Subscription state not reached within ${timeoutMs}ms.\n`;
            errorMsg += `Expected - channels: ${expectedChannels ? Array.from(expectedChannels).join(", ") : "N/A"}, `;
            errorMsg += `patterns: ${expectedPatterns ? Array.from(expectedPatterns).join(", ") : "N/A"}, `;
            errorMsg += `sharded: ${expectedSharded ? Array.from(expectedSharded).join(", ") : "N/A"}\n`;

            if (lastActualState) {
                errorMsg += `Actual - channels: ${Array.from(lastActualState.channels).join(", ")}, `;
                errorMsg += `patterns: ${Array.from(lastActualState.patterns).join(", ")}, `;
                errorMsg += `sharded: ${Array.from(lastActualState.sharded).join(", ")}\n`;
            }

            throw new TimeoutError(errorMsg);
        }

        try {
            const result = await client.customCommand(["GET_SUBSCRIPTIONS"]);
            const { exact, pattern, sharded } =
                parseActualSubscriptions(result);

            // Convert Buffer arrays to string Sets
            const channelsActual = new Set(exact.map((buf) => buf.toString()));
            const patternsActual = new Set(
                pattern.map((buf) => buf.toString()),
            );
            const shardedActual = new Set(sharded.map((buf) => buf.toString()));

            lastActualState = {
                channels: channelsActual,
                patterns: patternsActual,
                sharded: shardedActual,
            };

            // Check if all expected states match
            const channelsMatch =
                expectedChannels === undefined ||
                (channelsActual.size === expectedChannels.size &&
                    Array.from(expectedChannels).every((ch) =>
                        channelsActual.has(ch),
                    ));

            const patternsMatch =
                expectedPatterns === undefined ||
                (patternsActual.size === expectedPatterns.size &&
                    Array.from(expectedPatterns).every((p) =>
                        patternsActual.has(p),
                    ));

            const shardedMatch =
                expectedSharded === undefined ||
                (shardedActual.size === expectedSharded.size &&
                    Array.from(expectedSharded).every((s) =>
                        shardedActual.has(s),
                    ));

            if (channelsMatch && patternsMatch && shardedMatch) {
                return lastActualState;
            }
        } catch (error) {
            // Rethrow TimeoutError (indicates polling timeout), continue on other errors
            if (error instanceof TimeoutError) {
                throw error;
            }
            // Continue polling on connection/other transient errors
        }

        await new Promise((resolve) => setTimeout(resolve, pollInterval));
    }
}

/**
 * Verify subscriptions are established based on the subscription method.
 * - Lazy: wait/poll until state matches (with timeout)
 * - Blocking and Config: verify immediately using GET_SUBSCRIPTIONS
 *
 * @param client - The Glide client
 * @param subscriptionMethod - The subscription method (Mode.Lazy, Mode.Blocking, or Mode.Config)
 * @param expectedChannels - Expected exact channel subscriptions (undefined = don't check)
 * @param expectedPatterns - Expected pattern subscriptions (undefined = don't check)
 * @param expectedSharded - Expected sharded channel subscriptions (undefined = don't check)
 * @param timeoutMs - Timeout in milliseconds (default: 5000ms)
 * @throws Error if expected state is not reached
 */
export async function waitForSubscriptionStateIfNeeded(
    client: TGlideClient,
    subscriptionMethod: number,
    expectedChannels?: Set<string>,
    expectedPatterns?: Set<string>,
    expectedSharded?: Set<string>,
    timeoutMs = 5000,
): Promise<void> {
    // Lazy subscriptions may need time to reconcile
    if (subscriptionMethod === Mode.Lazy) {
        await waitForSubscriptionState(
            client,
            expectedChannels,
            expectedPatterns,
            expectedSharded,
            timeoutMs,
        );
        return;
    }

    // Blocking and Config should already be established - verify immediately
    const result = await client.customCommand(["GET_SUBSCRIPTIONS"]);
    const { exact, pattern, sharded } = parseActualSubscriptions(result);

    // Convert Buffer arrays to string Sets
    const channelsActual = new Set(exact.map((buf) => buf.toString()));
    const patternsActual = new Set(pattern.map((buf) => buf.toString()));
    const shardedActual = new Set(sharded.map((buf) => buf.toString()));

    // Verify expected channels
    if (expectedChannels !== undefined) {
        const channelsMatch =
            channelsActual.size === expectedChannels.size &&
            Array.from(expectedChannels).every((ch) => channelsActual.has(ch));

        if (!channelsMatch) {
            throw new Error(
                `Expected channels ${Array.from(expectedChannels).join(", ")}, ` +
                    `got ${Array.from(channelsActual).join(", ")}`,
            );
        }
    }

    // Verify expected patterns
    if (expectedPatterns !== undefined) {
        const patternsMatch =
            patternsActual.size === expectedPatterns.size &&
            Array.from(expectedPatterns).every((p) => patternsActual.has(p));

        if (!patternsMatch) {
            throw new Error(
                `Expected patterns ${Array.from(expectedPatterns).join(", ")}, ` +
                    `got ${Array.from(patternsActual).join(", ")}`,
            );
        }
    }

    // Verify expected sharded channels
    if (expectedSharded !== undefined) {
        const shardedMatch =
            shardedActual.size === expectedSharded.size &&
            Array.from(expectedSharded).every((s) => shardedActual.has(s));

        if (!shardedMatch) {
            throw new Error(
                `Expected sharded ${Array.from(expectedSharded).join(", ")}, ` +
                    `got ${Array.from(shardedActual).join(", ")}`,
            );
        }
    }
}

/**
 * Dynamically subscribe to exact channels based on subscription method.
 * Does NOT wait for subscription to be established - use waitForSubscriptionStateIfNeeded after calling.
 *
 * @param client - The Glide client
 * @param channels - Set of exact channel names to subscribe to
 * @param subscriptionMethod - The subscription method (Mode.Lazy, Mode.Blocking, or Mode.Config)
 * @param timeoutMs - Timeout in milliseconds for blocking subscriptions (default: 5000ms)
 * @throws Error if subscription call fails or returns unexpected value
 */
export async function subscribeByMethod(
    client: TGlideClient,
    channels: Set<string>,
    subscriptionMethod: number,
    timeoutMs = 5000,
): Promise<void> {
    // Config mode: subscriptions already configured at creation, no-op
    if (subscriptionMethod === Mode.Config) {
        return;
    }

    let result;

    if (subscriptionMethod === Mode.Lazy) {
        // Lazy mode: non-blocking subscription
        result = await client.subscribeLazy(channels);
    } else if (subscriptionMethod === Mode.Blocking) {
        // Blocking mode: wait for confirmation with timeout
        result = await client.subscribe(channels, timeoutMs);
    } else {
        throw new Error(`Unknown subscription method: ${subscriptionMethod}`);
    }

    // Assert subscription call returns null or undefined
    expect(result).toBeNull();
}

/**
 * Dynamically subscribe to patterns based on subscription method.
 * Does NOT wait for subscription to be established - use waitForSubscriptionStateIfNeeded after calling.
 *
 * @param client - The Glide client
 * @param patterns - Set of pattern names to subscribe to
 * @param subscriptionMethod - The subscription method (Mode.Lazy, Mode.Blocking, or Mode.Config)
 * @param timeoutMs - Timeout in milliseconds for blocking subscriptions (default: 5000ms)
 * @throws Error if subscription call fails or returns unexpected value
 */
export async function psubscribeByMethod(
    client: TGlideClient,
    patterns: Set<string>,
    subscriptionMethod: number,
    timeoutMs = 5000,
): Promise<void> {
    // Config mode: subscriptions already configured at creation, no-op
    if (subscriptionMethod === Mode.Config) {
        return;
    }

    let result;

    if (subscriptionMethod === Mode.Lazy) {
        // Lazy mode: non-blocking pattern subscription
        result = await client.psubscribeLazy(patterns);
    } else if (subscriptionMethod === Mode.Blocking) {
        // Blocking mode: wait for confirmation with timeout
        result = await client.psubscribe(patterns, timeoutMs);
    } else {
        throw new Error(`Unknown subscription method: ${subscriptionMethod}`);
    }

    // Assert subscription call returns null or undefined
    expect(result).toBeNull();
}

/**
 * Dynamically subscribe to sharded channels based on subscription method (cluster mode only).
 * Does NOT wait for subscription to be established - use waitForSubscriptionStateIfNeeded after calling.
 *
 * @param client - The Glide cluster client
 * @param channels - Set of sharded channel names to subscribe to
 * @param subscriptionMethod - The subscription method (Mode.Lazy, Mode.Blocking, or Mode.Config)
 * @param timeoutMs - Timeout in milliseconds for blocking subscriptions (default: 5000ms)
 * @throws Error if subscription call fails or returns unexpected value
 */
export async function ssubscribeByMethod(
    client: GlideClusterClient,
    channels: Set<string>,
    subscriptionMethod: number,
    timeoutMs = 5000,
): Promise<void> {
    // Config mode: subscriptions already configured at creation, no-op
    if (subscriptionMethod === Mode.Config) {
        return;
    }

    let result;

    if (subscriptionMethod === Mode.Lazy) {
        // Lazy mode: non-blocking sharded channel subscription
        result = await client.ssubscribeLazy(channels);
    } else if (subscriptionMethod === Mode.Blocking) {
        // Blocking mode: wait for confirmation with timeout
        result = await client.ssubscribe(channels, timeoutMs);
    } else {
        throw new Error(`Unknown subscription method: ${subscriptionMethod}`);
    }

    // Assert subscription call returns null or undefined
    expect(result).toBeNull();
}

/**
 * Dynamically unsubscribe from exact channels based on subscription method.
 * Does NOT wait for unsubscription to complete - use waitForSubscriptionStateIfNeeded after calling.
 *
 * @param client - The Glide client
 * @param channels - Set of exact channel names to unsubscribe from (null = unsubscribe from all)
 * @param subscriptionMethod - The subscription method (Mode.Lazy, Mode.Blocking, or Mode.Config)
 * @param timeoutMs - Timeout in milliseconds for blocking unsubscriptions (default: 5000ms)
 * @throws Error if unsubscription call fails or returns unexpected value
 */
export async function unsubscribeByMethod(
    client: TGlideClient,
    channels: Set<string> | null,
    subscriptionMethod: number,
    timeoutMs = 5000,
): Promise<void> {
    // Config mode: cannot dynamically unsubscribe, no-op
    if (subscriptionMethod === Mode.Config) {
        return;
    }

    let result;

    if (subscriptionMethod === Mode.Lazy) {
        // Lazy mode: non-blocking unsubscription
        result = await client.unsubscribeLazy(channels);
    } else if (subscriptionMethod === Mode.Blocking) {
        // Blocking mode: wait for confirmation with timeout
        result = await client.unsubscribe(channels, timeoutMs);
    } else {
        throw new Error(`Unknown subscription method: ${subscriptionMethod}`);
    }

    // Assert unsubscription call returns null or undefined
    expect(result).toBeNull();
}

/**
 * Dynamically unsubscribe from patterns based on subscription method.
 * Does NOT wait for unsubscription to complete - use waitForSubscriptionStateIfNeeded after calling.
 *
 * @param client - The Glide client
 * @param patterns - Set of pattern names to unsubscribe from (null = unsubscribe from all)
 * @param subscriptionMethod - The subscription method (Mode.Lazy, Mode.Blocking, or Mode.Config)
 * @param timeoutMs - Timeout in milliseconds for blocking unsubscriptions (default: 5000ms)
 * @throws Error if unsubscription call fails or returns unexpected value
 */
export async function punsubscribeByMethod(
    client: TGlideClient,
    patterns: Set<string> | null,
    subscriptionMethod: number,
    timeoutMs = 5000,
): Promise<void> {
    // Config mode: cannot dynamically unsubscribe, no-op
    if (subscriptionMethod === Mode.Config) {
        return;
    }

    let result;

    if (subscriptionMethod === Mode.Lazy) {
        // Lazy mode: non-blocking pattern unsubscription
        result = await client.punsubscribeLazy(patterns);
    } else if (subscriptionMethod === Mode.Blocking) {
        // Blocking mode: wait for confirmation with timeout
        result = await client.punsubscribe(patterns, timeoutMs);
    } else {
        throw new Error(`Unknown subscription method: ${subscriptionMethod}`);
    }

    // Assert unsubscription call returns null or undefined
    expect(result).toBeNull();
}

/**
 * Dynamically unsubscribe from sharded channels based on subscription method (cluster mode only).
 * Does NOT wait for unsubscription to complete - use waitForSubscriptionStateIfNeeded after calling.
 *
 * @param client - The Glide cluster client
 * @param channels - Set of sharded channel names to unsubscribe from (null = unsubscribe from all)
 * @param subscriptionMethod - The subscription method (Mode.Lazy, Mode.Blocking, or Mode.Config)
 * @param timeoutMs - Timeout in milliseconds for blocking unsubscriptions (default: 5000ms)
 * @throws Error if unsubscription call fails or returns unexpected value
 */
export async function sunsubscribeByMethod(
    client: GlideClusterClient,
    channels: Set<string> | null,
    subscriptionMethod: number,
    timeoutMs = 5000,
): Promise<void> {
    // Config mode: cannot dynamically unsubscribe, no-op
    if (subscriptionMethod === Mode.Config) {
        return;
    }

    let result;

    if (subscriptionMethod === Mode.Lazy) {
        // Lazy mode: non-blocking sharded channel unsubscription
        result = await client.sunsubscribeLazy(channels);
    } else if (subscriptionMethod === Mode.Blocking) {
        // Blocking mode: wait for confirmation with timeout
        result = await client.sunsubscribe(channels, timeoutMs);
    } else {
        throw new Error(`Unknown subscription method: ${subscriptionMethod}`);
    }

    // Assert unsubscription call returns null or undefined
    expect(result).toBeNull();
}

/**
 * Create a PubSub client with optional subscriptions.
 * If channels, patterns, or shardedChannels are provided, creates client with PubSub subscriptions.
 * Otherwise creates a regular client without subscriptions.
 *
 * @param clusterMode - Whether to create a cluster client or standalone client
 * @param channels - Optional set of exact channel names to subscribe to
 * @param patterns - Optional set of pattern names to subscribe to
 * @param shardedChannels - Optional set of sharded channel names to subscribe to (cluster mode only)
 * @param callback - Optional callback function for PubSub messages
 * @param context - Optional context object passed to callback
 * @param protocol - Optional protocol version (default: RESP3)
 * @param timeout - Optional timeout in milliseconds
 * @param addresses - REQUIRED array of server addresses
 * @returns Created Glide client
 */
export async function createPubsubClient(
    clusterMode: boolean,
    channels?: Set<string>,
    patterns?: Set<string>,
    shardedChannels?: Set<string>,
    callback?: (msg: PubSubMsg, context: PubSubMsg[]) => void,
    context?: PubSubMsg[] | null,
    protocol?: ProtocolVersion,
    timeout?: number,
    addresses?: { host: string; port: number }[],
): Promise<TGlideClient> {
    // Import dynamically to avoid circular dependencies
    const {
        GlideClient,
        GlideClusterClient,
        GlideClientConfiguration,
        GlideClusterClientConfiguration,
        ProtocolVersion: PV,
    } = await import("../build-ts");

    // Build base configuration
    /* eslint-disable @typescript-eslint/no-explicit-any */
    const baseConfig: any = {
        protocol: protocol || PV.RESP3,
        addresses: addresses || [],
    };
    /* eslint-enable @typescript-eslint/no-explicit-any */

    if (timeout !== undefined) {
        baseConfig.requestTimeout = timeout;
    }

    // Check if we need to create PubSub subscriptions
    const hasSubscriptions = channels || patterns || shardedChannels;

    if (hasSubscriptions || callback) {
        // Build channelsAndPatterns object
        /* eslint-disable @typescript-eslint/no-explicit-any */
        const channelsAndPatterns: any = {};
        /* eslint-enable @typescript-eslint/no-explicit-any */

        if (clusterMode) {
            // Use cluster mode enums
            if (channels && channels.size > 0) {
                channelsAndPatterns[
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact
                ] = channels;
            }

            if (patterns && patterns.size > 0) {
                channelsAndPatterns[
                    GlideClusterClientConfiguration.PubSubChannelModes.Pattern
                ] = patterns;
            }

            if (shardedChannels && shardedChannels.size > 0) {
                channelsAndPatterns[
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded
                ] = shardedChannels;
            }
        } else {
            // Use standalone mode enums
            if (channels && channels.size > 0) {
                channelsAndPatterns[
                    GlideClientConfiguration.PubSubChannelModes.Exact
                ] = channels;
            }

            if (patterns && patterns.size > 0) {
                channelsAndPatterns[
                    GlideClientConfiguration.PubSubChannelModes.Pattern
                ] = patterns;
            }
        }

        // Create PubSubSubscriptions object - include callback even if no initial subscriptions
        // This is needed for custom command tests where we subscribe dynamically
        baseConfig.pubsubSubscriptions = {
            channelsAndPatterns,
            callback,
            context,
        };
    }

    // Create and return the client
    if (clusterMode) {
        return await GlideClusterClient.createClient(baseConfig);
    } else {
        return await GlideClient.createClient(baseConfig);
    }
}

/**
 * Get the appropriate PubSubChannelModes enum for the client type.
 *
 * @param client - The Glide client (cluster or standalone)
 * @returns PubSubChannelModes enum for the client type
 */
/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/no-require-imports */
export function getPubsubModes(client: TGlideClient): any {
    // Import dynamically to avoid circular dependencies
    const {
        GlideClusterClient,
        GlideClientConfiguration,
        GlideClusterClientConfiguration,
    } = require("../build-ts");

    if (client instanceof GlideClusterClient) {
        return GlideClusterClientConfiguration.PubSubChannelModes;
    } else {
        return GlideClientConfiguration.PubSubChannelModes;
    }
}
/* eslint-enable @typescript-eslint/no-explicit-any, @typescript-eslint/no-require-imports */
