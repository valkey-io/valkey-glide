/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideClient, GlideClusterClient } from "../build-ts";

/**
 * Probes for the valkey-search 1.3.0 query features by checking that FT.SEARCH accepts INKEYS.
 */
async function probeSearchQueryFeatureSupport(
    client: GlideClient | GlideClusterClient,
): Promise<boolean> {
    const probeIndex = `__glide_search_feature_probe_${Date.now()}_${Math.random()
        .toString(36)
        .slice(2)}`;

    try {
        await client.customCommand([
            "FT.CREATE",
            probeIndex,
            "SCHEMA",
            "title",
            "TEXT",
        ]);
    } catch (e) {
        throw new Error(
            "search 1.3 capability probe could not create a test index " +
                `(is the search module loaded at all?): ${e}`,
            { cause: e },
        );
    }

    try {
        try {
            await client.customCommand([
                "FT.SEARCH",
                probeIndex,
                "*",
                "INKEYS",
                "0",
            ]);
            return true;
        } catch (e) {
            const msg = e instanceof Error ? e.message : String(e);

            if (msg.includes("Unexpected") && msg.includes("INKEYS")) {
                return false;
            }

            throw new Error(
                `search 1.3 capability probe got an unexpected error: ${e}`,
                { cause: e },
            );
        }
    } finally {
        try {
            await client.customCommand(["FT.DROPINDEX", probeIndex]);
        } catch {
            await client.customCommand(["FT.DROPINDEX", probeIndex]);
        }
    }
}

/**
 * Reads one field out of a reply that may be a plain object, a
 * `GlideRecord`-style array of `{ key, value }` entries, or a flat
 * `[key, value, ...]` array. Returns `undefined` if the field is absent or
 * the reply shape isn't recognised.
 */
export function readMapField(reply: unknown, field: string): unknown {
    const decode = (v: unknown): string | undefined =>
        v === undefined || v === null
            ? undefined
            : Buffer.isBuffer(v)
              ? v.toString()
              : String(v);

    if (Array.isArray(reply)) {
        if (
            reply.length > 0 &&
            typeof reply[0] === "object" &&
            reply[0] !== null &&
            "key" in (reply[0] as object)
        ) {
            for (const entry of reply as { key: unknown; value: unknown }[]) {
                if (decode(entry.key) === field) {
                    return entry.value;
                }
            }

            return undefined;
        }

        for (let i = 0; i + 1 < reply.length; i += 2) {
            if (decode(reply[i]) === field) {
                return reply[i + 1];
            }
        }

        return undefined;
    }

    if (reply !== null && typeof reply === "object") {
        for (const [k, v] of Object.entries(reply as Record<string, unknown>)) {
            if (decode(k) === field) {
                return v;
            }
        }
    }

    return undefined;
}

/**
 * `true` if `level` is strictly less than `minLevel`, both dotted X.Y.Z (an
 * optional `-rcN` suffix is stripped).
 */
export function checkVersionLt(level: string, minLevel: string): boolean {
    const strip = (v: string) => v.split("-")[0];
    const a = strip(level).split(".").map(Number);
    const b = strip(minLevel).split(".").map(Number);

    for (let i = 0; i < Math.max(a.length, b.length); i++) {
        const x = a[i] ?? 0;
        const y = b[i] ?? 0;
        if (x !== y) return x < y;
    }

    return false;
}

/**
 * Probes a connected client for search 1.3 capability and, when capable,
 * reads `search.emulate-release`. Throws if the module is absent or if a
 * capable module's level can't be parsed.
 */
export async function probeCapabilityAndLevel(
    client: GlideClient | GlideClusterClient,
): Promise<{ capability: boolean; emulateReleaseLevel: string | undefined }> {
    const capability = await probeSearchQueryFeatureSupport(client);
    let emulateReleaseLevel: string | undefined;

    if (capability) {
        const configGetReply = await client.customCommand([
            "CONFIG",
            "GET",
            "search.emulate-release",
        ]);
        const value = readMapField(configGetReply, "search.emulate-release");
        emulateReleaseLevel =
            value === undefined || value === null ? undefined : String(value);

        if (emulateReleaseLevel === undefined) {
            throw new Error(
                "search.emulate-release could not be parsed from a " +
                    "1.3-capable module's CONFIG GET reply",
            );
        }
    }

    return { capability, emulateReleaseLevel };
}
