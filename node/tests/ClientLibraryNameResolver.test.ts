// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import { describe, expect, it } from "@jest/globals";
import { ConfigurationError } from "../build-ts";
import { resolveClientLibraryName } from "../build-ts/ClientLibraryNameResolver";

describe("resolveClientLibraryName", () => {
    it.each([
        [undefined, undefined, "GlideJS"],
        ["custom-client", undefined, "custom-client"],
        [undefined, "framework:1.2", "GlideJS(framework:1.2)"],
        ["custom-client", "framework:1.2", "custom-client(framework:1.2)"],
        ["", undefined, "GlideJS"],
        [undefined, "", "GlideJS"],
        ["", "", "GlideJS"],
        [
            "custom/client+v2",
            "framework:@1.2!",
            "custom/client+v2(framework:@1.2!)",
        ],
    ])(
        "resolves libName=%p and clientInfoTag=%p to %p",
        (libName, clientInfoTag, expected) => {
            expect(resolveClientLibraryName(libName, clientInfoTag)).toBe(
                expected,
            );
        },
    );

    it.each([
        ["libName", "custom client"],
        ["libName", "custom\tclient"],
        ["libName", "custom\u00a0client"],
        ["libName", "custom\u0085client"],
        ["clientInfoTag", "framework tag"],
        ["clientInfoTag", "framework\ntag"],
        ["clientInfoTag", "framework\u2003tag"],
        ["clientInfoTag", "framework\u0085tag"],
    ] as ["libName" | "clientInfoTag", string][])(
        "rejects whitespace in %s",
        (fieldName, value) => {
            const resolve = () =>
                resolveClientLibraryName(
                    fieldName === "libName" ? value : undefined,
                    fieldName === "clientInfoTag" ? value : undefined,
                );

            expect(resolve).toThrow(ConfigurationError);
            expect(resolve).toThrow(
                `${fieldName} must not contain whitespace characters`,
            );
        },
    );
});
