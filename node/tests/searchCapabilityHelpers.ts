/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Shared valkey-search 1.3 capability/emulate-release helpers, factored out
 * of ServerModulesVs13.test.ts so ServerModules.test.ts's existing
 * (pre-1.3) tests can also become level-aware where the server's reply
 * shape changes under `search.emulate-release >= 1.3.0` (D22) -- even for
 * assertions that predate the 1.3 port and were never gated before,
 * because this port is the first time those tests run against a module at
 * that level.
 */

import { GlideClient, GlideClusterClient } from "../build-ts";

/**
 * Pure classification of the INKEYS probe's error text (equivalent to
 * Python's `classify_inkeys_probe_error`). Unit-testable with no server.
 */
export function classifyInkeysProbeError(msg: string): boolean {
    return msg.includes("Unexpected") && msg.includes("INKEYS");
}

/**
 * Probes a connected client for valkey-search 1.3 capability using a real
 * command plus a 1.3-only argument (INKEYS, added in rc1 / PR #967),
 * classified by error text rather than a version string (a version string
 * can be present without the specific capability a test needs being live).
 * Raises -- never returns a false "incapable" -- if the search module
 * appears entirely absent, since a missing module must never become a
 * silent skip.
 */
export async function probeSearch13Capability(
    client: GlideClient | GlideClusterClient,
): Promise<boolean> {
    const probeIndex = `__glide_search_1_3_probe_${Date.now()}_${Math.random()
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

            if (classifyInkeysProbeError(msg)) {
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
            // Retry once -- a transient failure here would otherwise leave
            // probeIndex on the server for the rest of the session.
            // FT.CREATE and FT.DROPINDEX both use probeIndex as their own
            // first argument, so they always route to the same primary; a
            // cluster-routing mismatch is not a possible explanation for a
            // retry also failing.
            await client.customCommand(["FT.DROPINDEX", probeIndex]);
        }
    }
}

/**
 * Reads one field out of a reply that may be a plain object, a
 * `GlideRecord`-style array of `{ key, value }` entries, or a flat
 * `[key, value, ...]` array -- the shape a nested map comes back as depends
 * on the protocol (RESP3 clients get `GlideRecord`/object shapes, RESP2
 * clients get a flat array for the same logical map). Returns `undefined`
 * if the field is absent or the reply shape isn't recognised.
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
 * Parses `CONFIG GET search.emulate-release`'s reply into a string, or
 * `undefined` if the key is absent or the shape is unrecognised.
 */
export function parseEmulateReleaseLevel(
    configGetReply: unknown,
): string | undefined {
    const value = readMapField(configGetReply, "search.emulate-release");
    return value === undefined || value === null
        ? undefined
        : Buffer.isBuffer(value)
          ? value.toString()
          : String(value);
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
 * Session-once probe + emulate-release level read, run against a client in
 * the given cluster. Throws (never returns a silently-wrong result) if the
 * module is absent, or if a capable module's level can't be parsed.
 */
export async function probeCapabilityAndLevel(
    client: GlideClient | GlideClusterClient,
): Promise<{ capability: boolean; emulateReleaseLevel: string | undefined }> {
    const capability = await probeSearch13Capability(client);
    let emulateReleaseLevel: string | undefined;

    if (capability) {
        const configGetReply = await client.customCommand([
            "CONFIG",
            "GET",
            "search.emulate-release",
        ]);
        emulateReleaseLevel = parseEmulateReleaseLevel(configGetReply);

        if (emulateReleaseLevel === undefined) {
            throw new Error(
                "search.emulate-release could not be parsed from a " +
                    "1.3-capable module's CONFIG GET reply",
            );
        }
    }

    return { capability, emulateReleaseLevel };
}
