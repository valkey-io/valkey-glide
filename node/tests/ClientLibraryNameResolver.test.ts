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
        ["libName", ""],
        ["libName", "!"],
        ["libName", "~"],
        ["libName", "client!#$%&'*+,-./:;=?@[]^_`{|}~"],
        ["clientInfoTag", ""],
        ["clientInfoTag", "!"],
        ["clientInfoTag", "~"],
        ["clientInfoTag", "tag!#$%&'*+,-./:;=?@[]^_`{|}~"],
    ] as ["libName" | "clientInfoTag", string][])(
        "accepts printable ASCII boundaries and punctuation in %s",
        (fieldName, value) => {
            expect(() =>
                resolveClientLibraryName(
                    fieldName === "libName" ? value : undefined,
                    fieldName === "clientInfoTag" ? value : undefined,
                ),
            ).not.toThrow();
        },
    );

    it.each([
        ["libName", "custom client"],
        ["libName", "custom\u0000client"],
        ["libName", "custom\tclient"],
        ["libName", "custom\nclient"],
        ["libName", "custom\u007fclient"],
        ["libName", "custom\u00a0client"],
        ["libName", "customéclient"],
        ["libName", "custom中client"],
        ["clientInfoTag", "framework tag"],
        ["clientInfoTag", "framework\u0000tag"],
        ["clientInfoTag", "framework\ttag"],
        ["clientInfoTag", "framework\ntag"],
        ["clientInfoTag", "framework\u007ftag"],
        ["clientInfoTag", "framework\u2003tag"],
        ["clientInfoTag", "frameworkétag"],
        ["clientInfoTag", "framework中tag"],
    ] as ["libName" | "clientInfoTag", string][])(
        "rejects characters outside printable ASCII in %s",
        (fieldName, value) => {
            const resolve = () =>
                resolveClientLibraryName(
                    fieldName === "libName" ? value : undefined,
                    fieldName === "clientInfoTag" ? value : undefined,
                );

            expect(resolve).toThrow(ConfigurationError);
            expect(resolve).toThrow(
                `${fieldName} must contain only printable ASCII characters from '!' through '~'`,
            );
        },
    );
});
