/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for debugging TLS certificates in tests. Provides comprehensive logging of
 * certificate content in multiple formats.
 */
public class CertificateDebugger {

    private static final int PREVIEW_BYTES = 100;
    private static final String PEM_BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_END = "-----END CERTIFICATE-----";

    /**
     * Logs comprehensive certificate information for debugging purposes.
     *
     * @param cert The certificate bytes to debug
     * @param context A descriptive context for the log (e.g., "Before client creation")
     */
    public static void logCertificateInfo(byte[] cert, String context) {
        System.out.println("=== CERTIFICATE DEBUG: " + context + " ===");

        if (cert == null) {
            System.out.println("Certificate is NULL");
            System.out.println("=====================================");
            return;
        }

        if (cert.length == 0) {
            System.out.println("Certificate is EMPTY (length = 0)");
            System.out.println("=====================================");
            return;
        }

        // Basic info
        System.out.println("Certificate length: " + cert.length + " bytes");

        // Format detection
        String format = detectFormat(cert);
        System.out.println("Detected format: " + format);

        // Line ending detection
        String lineEndings = detectLineEndings(cert);
        System.out.println("Line endings: " + lineEndings);

        // First and last bytes preview
        int previewLen = Math.min(PREVIEW_BYTES, cert.length);
        byte[] firstBytes = new byte[previewLen];
        System.arraycopy(cert, 0, firstBytes, 0, previewLen);
        System.out.println("First " + previewLen + " bytes (hex): " + bytesToHex(firstBytes));
        System.out.println(
                "First "
                        + previewLen
                        + " bytes (string): "
                        + new String(firstBytes, StandardCharsets.UTF_8)
                                .replace("\n", "\\n")
                                .replace("\r", "\\r"));

        if (cert.length > PREVIEW_BYTES) {
            int lastStart = cert.length - previewLen;
            byte[] lastBytes = new byte[previewLen];
            System.arraycopy(cert, lastStart, lastBytes, 0, previewLen);
            System.out.println("Last " + previewLen + " bytes (hex): " + bytesToHex(lastBytes));
            System.out.println(
                    "Last "
                            + previewLen
                            + " bytes (string): "
                            + new String(lastBytes, StandardCharsets.UTF_8)
                                    .replace("\n", "\\n")
                                    .replace("\r", "\\r"));
        }

        // PEM structure validation
        if (format.contains("PEM")) {
            validatePemStructure(cert);
        }

        // Base64 encoding of full certificate (for comparison)
        System.out.println(
                "Base64 encoded (first 200 chars): "
                        + Base64.getEncoder()
                                .encodeToString(cert)
                                .substring(0, Math.min(200, Base64.getEncoder().encodeToString(cert).length())));

        System.out.println("=====================================");
    }

    /**
     * Logs information about multiple certificates in a chain.
     *
     * @param certs Array of certificate bytes
     * @param context A descriptive context for the log
     */
    public static void logCertificateChain(byte[][] certs, String context) {
        System.out.println("=== CERTIFICATE CHAIN DEBUG: " + context + " ===");

        if (certs == null) {
            System.out.println("Certificate chain is NULL");
            System.out.println("=====================================");
            return;
        }

        System.out.println("Certificate chain length: " + certs.length);

        for (int i = 0; i < certs.length; i++) {
            System.out.println("\n--- Certificate " + (i + 1) + " of " + certs.length + " ---");
            logCertificateInfo(certs[i], "Chain position " + (i + 1));
        }

        System.out.println("=====================================");
    }

    /**
     * Detects the format of the certificate (PEM or DER).
     *
     * @param cert The certificate bytes
     * @return A string describing the detected format
     */
    private static String detectFormat(byte[] cert) {
        if (cert.length < 10) {
            return "UNKNOWN (too short)";
        }

        String start = new String(cert, 0, Math.min(30, cert.length), StandardCharsets.UTF_8);

        if (start.contains(PEM_BEGIN)) {
            return "PEM";
        } else if (cert[0] == 0x30 && cert[1] == (byte) 0x82) {
            return "DER (binary)";
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Detects the line ending style used in the certificate.
     *
     * @param cert The certificate bytes
     * @return A string describing the line ending style
     */
    private static String detectLineEndings(byte[] cert) {
        boolean hasCR = false;
        boolean hasLF = false;
        boolean hasCRLF = false;

        for (int i = 0; i < cert.length; i++) {
            if (cert[i] == '\r') {
                hasCR = true;
                if (i + 1 < cert.length && cert[i + 1] == '\n') {
                    hasCRLF = true;
                }
            } else if (cert[i] == '\n') {
                hasLF = true;
            }
        }

        if (hasCRLF) {
            return "CRLF (Windows-style \\r\\n)";
        } else if (hasLF && !hasCR) {
            return "LF (Unix-style \\n)";
        } else if (hasCR && !hasLF) {
            return "CR (Old Mac-style \\r)";
        } else if (hasLF && hasCR) {
            return "MIXED (both LF and CR, but not CRLF)";
        } else {
            return "NONE (no line endings detected)";
        }
    }

    /**
     * Validates the PEM structure of a certificate.
     *
     * @param cert The certificate bytes
     */
    private static void validatePemStructure(byte[] cert) {
        String certStr = new String(cert, StandardCharsets.UTF_8);

        int beginCount = countOccurrences(certStr, PEM_BEGIN);
        int endCount = countOccurrences(certStr, PEM_END);

        System.out.println("PEM structure validation:");
        System.out.println("  BEGIN CERTIFICATE markers: " + beginCount);
        System.out.println("  END CERTIFICATE markers: " + endCount);

        if (beginCount != endCount) {
            System.out.println("  WARNING: Mismatched BEGIN/END markers!");
        } else if (beginCount == 0) {
            System.out.println("  WARNING: No PEM markers found despite PEM format detection!");
        } else {
            System.out.println("  PEM structure appears valid (" + beginCount + " certificate(s))");
        }

        // Check for proper newline after BEGIN marker
        int beginIdx = certStr.indexOf(PEM_BEGIN);
        if (beginIdx >= 0 && beginIdx + PEM_BEGIN.length() < certStr.length()) {
            char nextChar = certStr.charAt(beginIdx + PEM_BEGIN.length());
            if (nextChar != '\n' && nextChar != '\r') {
                System.out.println("  WARNING: No newline after BEGIN CERTIFICATE marker!");
            }
        }

        // Check for proper newline before END marker
        int endIdx = certStr.indexOf(PEM_END);
        if (endIdx > 0) {
            char prevChar = certStr.charAt(endIdx - 1);
            if (prevChar != '\n' && prevChar != '\r') {
                System.out.println("  WARNING: No newline before END CERTIFICATE marker!");
            }
        }
    }

    /**
     * Counts occurrences of a substring in a string.
     *
     * @param str The string to search
     * @param substr The substring to count
     * @return The number of occurrences
     */
    private static int countOccurrences(String str, String substr) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }

    /**
     * Converts bytes to hexadecimal string representation.
     * Compatible with Java 11+.
     *
     * @param bytes The bytes to convert
     * @return Hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Creates a diagnostic report for certificate parsing attempts.
     *
     * @param cert The certificate bytes
     * @param parseSuccess Whether parsing was successful
     * @param errorMessage Error message if parsing failed
     */
    public static void logParsingAttempt(byte[] cert, boolean parseSuccess, String errorMessage) {
        System.out.println("=== CERTIFICATE PARSING ATTEMPT ===");
        System.out.println("Certificate length: " + (cert != null ? cert.length : "NULL") + " bytes");
        System.out.println("Parsing result: " + (parseSuccess ? "SUCCESS" : "FAILED"));

        if (!parseSuccess && errorMessage != null) {
            System.out.println("Error message: " + errorMessage);
        }

        if (cert != null && !parseSuccess) {
            // Log additional debug info for failed parsing
            System.out.println("Format: " + detectFormat(cert));
            System.out.println("Line endings: " + detectLineEndings(cert));
        }

        System.out.println("===================================");
    }
}
