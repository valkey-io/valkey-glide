/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for validating TLS certificate formats. Provides comprehensive validation of PEM
 * structure, line endings, and certificate chains.
 */
public class CertificateFormatValidator {

    private static final String PEM_BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_END = "-----END CERTIFICATE-----";

    /** Result of certificate validation containing status and diagnostic information. */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final DiagnosticInfo diagnosticInfo;

        public ValidationResult(
                boolean valid, List<String> errors, List<String> warnings, DiagnosticInfo diagnosticInfo) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
            this.diagnosticInfo = diagnosticInfo;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public DiagnosticInfo getDiagnosticInfo() {
            return diagnosticInfo;
        }

        public void printReport() {
            System.out.println("=== CERTIFICATE VALIDATION REPORT ===");
            System.out.println("Status: " + (valid ? "VALID" : "INVALID"));

            if (!errors.isEmpty()) {
                System.out.println("\nErrors:");
                for (String error : errors) {
                    System.out.println("  - " + error);
                }
            }

            if (!warnings.isEmpty()) {
                System.out.println("\nWarnings:");
                for (String warning : warnings) {
                    System.out.println("  - " + warning);
                }
            }

            if (diagnosticInfo != null) {
                System.out.println("\nDiagnostic Information:");
                System.out.println("  Format: " + diagnosticInfo.format);
                System.out.println("  Line Endings: " + diagnosticInfo.lineEndings);
                System.out.println("  Certificate Count: " + diagnosticInfo.certificateCount);
                System.out.println("  Total Length: " + diagnosticInfo.totalLength + " bytes");
            }

            System.out.println("=====================================");
        }
    }

    /** Diagnostic information about a certificate. */
    public static class DiagnosticInfo {
        private final String format;
        private final String lineEndings;
        private final int certificateCount;
        private final int totalLength;
        private final boolean hasPemMarkers;
        private final boolean hasValidStructure;

        public DiagnosticInfo(
                String format,
                String lineEndings,
                int certificateCount,
                int totalLength,
                boolean hasPemMarkers,
                boolean hasValidStructure) {
            this.format = format;
            this.lineEndings = lineEndings;
            this.certificateCount = certificateCount;
            this.totalLength = totalLength;
            this.hasPemMarkers = hasPemMarkers;
            this.hasValidStructure = hasValidStructure;
        }

        public String getFormat() {
            return format;
        }

        public String getLineEndings() {
            return lineEndings;
        }

        public int getCertificateCount() {
            return certificateCount;
        }

        public int getTotalLength() {
            return totalLength;
        }

        public boolean hasPemMarkers() {
            return hasPemMarkers;
        }

        public boolean hasValidStructure() {
            return hasValidStructure;
        }
    }

    /**
     * Validates the format of a certificate.
     *
     * @param cert The certificate bytes to validate
     * @return ValidationResult containing validation status and diagnostic information
     */
    public static ValidationResult validateFormat(byte[] cert) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (cert == null) {
            errors.add("Certificate is null");
            return new ValidationResult(false, errors, warnings, null);
        }

        if (cert.length == 0) {
            errors.add("Certificate is empty (length = 0)");
            return new ValidationResult(false, errors, warnings, null);
        }

        // Detect format
        String format = detectFormat(cert);
        String lineEndings = detectLineEndings(cert);

        // Validate PEM structure
        String certStr = new String(cert, StandardCharsets.UTF_8);
        int beginCount = countOccurrences(certStr, PEM_BEGIN);
        int endCount = countOccurrences(certStr, PEM_END);

        boolean hasPemMarkers = beginCount > 0 && endCount > 0;
        boolean hasValidStructure = beginCount == endCount && beginCount > 0;

        if (format.equals("PEM")) {
            if (beginCount == 0 || endCount == 0) {
                errors.add("PEM format detected but missing BEGIN/END markers");
            } else if (beginCount != endCount) {
                errors.add("Mismatched PEM markers: " + beginCount + " BEGIN, " + endCount + " END");
            }

            // Check for proper newlines after BEGIN marker
            int beginIdx = certStr.indexOf(PEM_BEGIN);
            if (beginIdx >= 0 && beginIdx + PEM_BEGIN.length() < certStr.length()) {
                char nextChar = certStr.charAt(beginIdx + PEM_BEGIN.length());
                if (nextChar != '\n' && nextChar != '\r') {
                    warnings.add("No newline after BEGIN CERTIFICATE marker");
                }
            }

            // Check for proper newlines before END marker
            int endIdx = certStr.indexOf(PEM_END);
            if (endIdx > 0) {
                char prevChar = certStr.charAt(endIdx - 1);
                if (prevChar != '\n' && prevChar != '\r') {
                    warnings.add("No newline before END CERTIFICATE marker");
                }
            }

            // Warn about line ending inconsistencies
            if (lineEndings.equals("MIXED")) {
                warnings.add("Mixed line endings detected (both LF and CR present)");
            } else if (lineEndings.equals("CRLF")) {
                warnings.add("Windows-style CRLF line endings detected (may cause issues on Unix systems)");
            }
        } else if (format.equals("UNKNOWN")) {
            errors.add("Unknown certificate format");
        }

        DiagnosticInfo diagnosticInfo =
                new DiagnosticInfo(
                        format,
                        lineEndings,
                        Math.max(beginCount, endCount),
                        cert.length,
                        hasPemMarkers,
                        hasValidStructure);

        boolean isValid = errors.isEmpty();
        return new ValidationResult(isValid, errors, warnings, diagnosticInfo);
    }

    /**
     * Validates a certificate chain.
     *
     * @param certs Array of certificate bytes
     * @return ValidationResult for the entire chain
     */
    public static ValidationResult validateChain(byte[][] certs) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (certs == null) {
            errors.add("Certificate chain is null");
            return new ValidationResult(false, errors, warnings, null);
        }

        if (certs.length == 0) {
            errors.add("Certificate chain is empty");
            return new ValidationResult(false, errors, warnings, null);
        }

        int totalLength = 0;
        int validCerts = 0;
        String commonLineEnding = null;

        for (int i = 0; i < certs.length; i++) {
            ValidationResult result = validateFormat(certs[i]);

            if (!result.isValid()) {
                errors.add(
                        "Certificate " + (i + 1) + " is invalid: " + String.join(", ", result.getErrors()));
            } else {
                validCerts++;
            }

            if (result.getDiagnosticInfo() != null) {
                totalLength += result.getDiagnosticInfo().getTotalLength();

                // Check for consistent line endings across chain
                String lineEnding = result.getDiagnosticInfo().getLineEndings();
                if (commonLineEnding == null) {
                    commonLineEnding = lineEnding;
                } else if (!commonLineEnding.equals(lineEnding)) {
                    warnings.add(
                            "Inconsistent line endings in chain: " + commonLineEnding + " vs " + lineEnding);
                }
            }

            warnings.addAll(result.getWarnings());
        }

        if (validCerts < certs.length) {
            errors.add("Only " + validCerts + " of " + certs.length + " certificates are valid");
        }

        DiagnosticInfo diagnosticInfo =
                new DiagnosticInfo(
                        "PEM Chain",
                        commonLineEnding != null ? commonLineEnding : "UNKNOWN",
                        certs.length,
                        totalLength,
                        true,
                        validCerts == certs.length);

        boolean isValid = errors.isEmpty();
        return new ValidationResult(isValid, errors, warnings, diagnosticInfo);
    }

    /**
     * Attempts to parse a certificate and returns diagnostic information.
     *
     * @param cert The certificate bytes to parse
     * @return ValidationResult with parsing information
     */
    public static ValidationResult validateParsing(byte[] cert) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ValidationResult formatResult = validateFormat(cert);
        errors.addAll(formatResult.getErrors());
        warnings.addAll(formatResult.getWarnings());

        // Attempt to parse with Java's CertificateFactory
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream bis = new ByteArrayInputStream(cert);
            Certificate certificate = cf.generateCertificate(bis);

            if (certificate == null) {
                errors.add("CertificateFactory returned null");
            } else {
                warnings.add("Successfully parsed as " + certificate.getType() + " certificate");
            }
        } catch (CertificateException e) {
            errors.add("Failed to parse certificate: " + e.getMessage());
        }

        boolean isValid = errors.isEmpty();
        return new ValidationResult(isValid, errors, warnings, formatResult.getDiagnosticInfo());
    }

    /**
     * Detects the format of a certificate.
     *
     * @param cert The certificate bytes
     * @return A string describing the format
     */
    private static String detectFormat(byte[] cert) {
        if (cert.length < 10) {
            return "UNKNOWN";
        }

        String start = new String(cert, 0, Math.min(30, cert.length), StandardCharsets.UTF_8);

        if (start.contains(PEM_BEGIN)) {
            return "PEM";
        } else if (cert[0] == 0x30 && cert[1] == (byte) 0x82) {
            return "DER";
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Detects the line ending style in a certificate.
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
            return "CRLF";
        } else if (hasLF && !hasCR) {
            return "LF";
        } else if (hasCR && !hasLF) {
            return "CR";
        } else if (hasLF && hasCR) {
            return "MIXED";
        } else {
            return "NONE";
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
     * Generates a comprehensive diagnostic report for a certificate.
     *
     * @param cert The certificate bytes
     * @return A formatted diagnostic report string
     */
    public static String generateDiagnosticReport(byte[] cert) {
        ValidationResult result = validateParsing(cert);

        StringBuilder report = new StringBuilder();
        report.append("=== CERTIFICATE DIAGNOSTIC REPORT ===\n");

        if (result.getDiagnosticInfo() != null) {
            DiagnosticInfo info = result.getDiagnosticInfo();
            report.append("Format: ").append(info.getFormat()).append("\n");
            report.append("Line Endings: ").append(info.getLineEndings()).append("\n");
            report.append("Certificate Count: ").append(info.getCertificateCount()).append("\n");
            report.append("Total Length: ").append(info.getTotalLength()).append(" bytes\n");
            report.append("Has PEM Markers: ").append(info.hasPemMarkers()).append("\n");
            report.append("Valid Structure: ").append(info.hasValidStructure()).append("\n");
        }

        report
                .append("\nValidation Status: ")
                .append(result.isValid() ? "VALID" : "INVALID")
                .append("\n");

        if (!result.getErrors().isEmpty()) {
            report.append("\nErrors:\n");
            for (String error : result.getErrors()) {
                report.append("  - ").append(error).append("\n");
            }
        }

        if (!result.getWarnings().isEmpty()) {
            report.append("\nWarnings:\n");
            for (String warning : result.getWarnings()) {
                report.append("  - ").append(warning).append("\n");
            }
        }

        report.append("=====================================");

        return report.toString();
    }
}
