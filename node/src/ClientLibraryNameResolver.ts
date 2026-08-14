// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import { ConfigurationError } from "./Errors.js";

const DEFAULT_LIBRARY_NAME = "GlideJS";
const PRINTABLE_ASCII_PATTERN = /^[!-~]+$/;

function validateIdentificationValue(
    fieldName: "libName" | "clientInfoTag",
    value: string | undefined,
): void {
    if (value && !PRINTABLE_ASCII_PATTERN.test(value)) {
        throw new ConfigurationError(
            `${fieldName} must contain only printable ASCII characters from '!' through '~'`,
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
