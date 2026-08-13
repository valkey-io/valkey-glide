// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import { ConfigurationError } from "./Errors.js";

const DEFAULT_LIBRARY_NAME = "GlideJS";
const WHITESPACE_PATTERN = /\p{White_Space}/u;

function validateIdentificationValue(
    fieldName: "libName" | "clientInfoTag",
    value: string | undefined,
): void {
    if (value && WHITESPACE_PATTERN.test(value)) {
        throw new ConfigurationError(
            `${fieldName} must not contain whitespace characters`,
        );
    }
}

/**
 * Resolves the library name sent to the server from the raw client-identification
 * configuration values.
 *
 * @internal
 */
export function resolveClientLibraryName(
    libName: string | undefined,
    clientInfoTag: string | undefined,
): string {
    validateIdentificationValue("libName", libName);
    validateIdentificationValue("clientInfoTag", clientInfoTag);

    const effectiveLibName = libName || DEFAULT_LIBRARY_NAME;
    return clientInfoTag
        ? `${effectiveLibName}(${clientInfoTag})`
        : effectiveLibName;
}
