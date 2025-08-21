/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import javax.net.ssl.SSLParameters;

/**
 * Options to configure SSL options for the connections kept to Redis servers. This is a
 * compatibility implementation for GLIDE wrapper.
 */
public class SslOptions {

    private final String keyStoreType;
    private final String trustStoreType;
    private final Resource keystoreResource;
    private final char[] keystorePassword;
    private final Resource truststoreResource;
    private final char[] truststorePassword;
    private final SSLParameters sslParameters;
    private final SslVerifyMode sslVerifyMode;
    private final String sslProtocol; // protocol for SSLContext

    private SslOptions(Builder builder) {
        this.keyStoreType = builder.keyStoreType;
        this.trustStoreType = builder.trustStoreType;
        this.keystoreResource = builder.keystoreResource;
        this.keystorePassword = builder.keystorePassword;
        this.truststoreResource = builder.truststoreResource;
        this.truststorePassword = builder.truststorePassword;
        this.sslParameters = builder.sslParameters;
        this.sslVerifyMode = builder.sslVerifyMode;
        this.sslProtocol = builder.sslProtocol;
    }

    /**
     * Returns a new {@link SslOptions.Builder} to construct {@link SslOptions}.
     *
     * @return a new {@link SslOptions.Builder} to construct {@link SslOptions}.
     */
    public static SslOptions.Builder builder() {
        return new SslOptions.Builder();
    }

    // Getters
    public String getKeyStoreType() {
        return keyStoreType;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public Resource getKeystoreResource() {
        return keystoreResource;
    }

    public char[] getKeystorePassword() {
        return keystorePassword;
    }

    public Resource getTruststoreResource() {
        return truststoreResource;
    }

    public char[] getTruststorePassword() {
        return truststorePassword;
    }

    public SSLParameters getSslParameters() {
        return sslParameters;
    }

    public SslVerifyMode getSslVerifyMode() {
        return sslVerifyMode;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    /** Builder for {@link SslOptions}. */
    public static class Builder {

        private String keyStoreType;
        private String trustStoreType;
        private Resource keystoreResource;
        private char[] keystorePassword = null;
        private Resource truststoreResource;
        private char[] truststorePassword = null;
        private SSLParameters sslParameters;
        private SslVerifyMode sslVerifyMode = SslVerifyMode.FULL;
        private String sslProtocol = "TLS"; // protocol for SSLContext

        private Builder() {}

        /**
         * Sets the KeyStore type. Defaults to {@link java.security.KeyStore#getDefaultType()} if not
         * set.
         *
         * @param keyStoreType the keystore type to use, must not be {@code null}.
         * @return {@code this}
         */
        public Builder keyStoreType(String keyStoreType) {
            this.keyStoreType = Objects.requireNonNull(keyStoreType, "KeyStoreType must not be null");
            return this;
        }

        /**
         * Sets the TrustStore type. Defaults to {@link java.security.KeyStore#getDefaultType()} if not
         * set.
         *
         * @param trustStoreType the truststore type to use, must not be {@code null}.
         * @return {@code this}
         */
        public Builder trustStoreType(String trustStoreType) {
            this.trustStoreType =
                    Objects.requireNonNull(trustStoreType, "TrustStoreType must not be null");
            return this;
        }

        /**
         * Sets the Keystore file to load client certificates.
         *
         * @param keystore the keystore file, must not be {@code null}.
         * @return {@code this}
         */
        public Builder keystore(File keystore) {
            return keystore(keystore, null);
        }

        /**
         * Sets the Keystore file to load client certificates.
         *
         * @param keystore the keystore file, must not be {@code null}.
         * @param keystorePassword the keystore password. May be empty to omit password and the keystore
         *     integrity check.
         * @return {@code this}
         */
        public Builder keystore(File keystore, char[] keystorePassword) {
            Objects.requireNonNull(keystore, "Keystore must not be null");
            return keystore(Resource.from(keystore), keystorePassword);
        }

        /**
         * Sets the Keystore resource to load client certificates.
         *
         * @param keystore the keystore URL, must not be {@code null}.
         * @return {@code this}
         */
        public Builder keystore(URL keystore) {
            return keystore(keystore, null);
        }

        /**
         * Sets the Keystore resource to load client certificates.
         *
         * @param keystore the keystore URL, must not be {@code null}.
         * @param keystorePassword the keystore password
         * @return {@code this}
         */
        public Builder keystore(URL keystore, char[] keystorePassword) {
            Objects.requireNonNull(keystore, "Keystore must not be null");
            return keystore(Resource.from(keystore), keystorePassword);
        }

        /**
         * Sets the Java Keystore resource to load client certificates.
         *
         * @param resource the provider that opens a {@link InputStream} to the keystore file, must not
         *     be {@code null}.
         * @param keystorePassword the keystore password. May be empty to omit password and the keystore
         *     integrity check.
         * @return {@code this}
         */
        public Builder keystore(Resource resource, char[] keystorePassword) {
            this.keystoreResource =
                    Objects.requireNonNull(resource, "Keystore InputStreamProvider must not be null");
            this.keystorePassword = keystorePassword != null ? keystorePassword.clone() : null;
            return this;
        }

        /**
         * Sets the Truststore file to load trusted certificates.
         *
         * @param truststore the truststore file, must not be {@code null}.
         * @return {@code this}
         */
        public Builder truststore(File truststore) {
            return truststore(truststore, null);
        }

        /**
         * Sets the Truststore file to load trusted certificates.
         *
         * @param truststore the truststore file, must not be {@code null}.
         * @param truststorePassword the truststore password. May be empty to omit password and the
         *     truststore integrity check.
         * @return {@code this}
         */
        public Builder truststore(File truststore, char[] truststorePassword) {
            Objects.requireNonNull(truststore, "Truststore must not be null");
            return truststore(Resource.from(truststore), truststorePassword);
        }

        /**
         * Sets the Truststore resource to load trusted certificates.
         *
         * @param truststore the truststore URL, must not be {@code null}.
         * @return {@code this}
         */
        public Builder truststore(URL truststore) {
            return truststore(truststore, null);
        }

        /**
         * Sets the Truststore resource to load trusted certificates.
         *
         * @param truststore the truststore URL, must not be {@code null}.
         * @param truststorePassword the truststore password. May be empty to omit password and the
         *     truststore integrity check.
         * @return {@code this}
         */
        public Builder truststore(URL truststore, char[] truststorePassword) {
            Objects.requireNonNull(truststore, "Truststore must not be null");
            return truststore(Resource.from(truststore), truststorePassword);
        }

        /**
         * Sets the Truststore resource to load trusted certificates.
         *
         * @param resource the provider that opens a {@link InputStream} to the keystore file, must not
         *     be {@code null}.
         * @param truststorePassword the truststore password. May be empty to omit password and the
         *     truststore integrity check.
         * @return {@code this}
         */
        public Builder truststore(Resource resource, char[] truststorePassword) {
            this.truststoreResource =
                    Objects.requireNonNull(resource, "Truststore InputStreamProvider must not be null");
            this.truststorePassword = truststorePassword != null ? truststorePassword.clone() : null;
            return this;
        }

        /**
         * Sets SSL parameters.
         *
         * @param sslParameters the SSL parameters
         * @return {@code this}
         */
        public Builder sslParameters(SSLParameters sslParameters) {
            this.sslParameters = sslParameters;
            return this;
        }

        /**
         * Sets SSL verify mode.
         *
         * @param sslVerifyMode the SSL verify mode
         * @return {@code this}
         */
        public Builder sslVerifyMode(SslVerifyMode sslVerifyMode) {
            this.sslVerifyMode = sslVerifyMode;
            return this;
        }

        /**
         * Sets SSL protocol.
         *
         * @param sslProtocol the SSL protocol
         * @return {@code this}
         */
        public Builder sslProtocol(String sslProtocol) {
            this.sslProtocol = sslProtocol;
            return this;
        }

        /**
         * Build the {@link SslOptions}.
         *
         * @return the {@link SslOptions}
         */
        public SslOptions build() {
            return new SslOptions(this);
        }
    }

    /**
     * Supplier for a {@link InputStream} representing a resource. The resulting {@link InputStream}
     * must be closed by the calling code.
     */
    @FunctionalInterface
    public interface Resource {

        /**
         * Create a {@link Resource} that obtains a {@link InputStream} from a {@link URL}.
         *
         * @param url the URL to obtain the {@link InputStream} from.
         * @return a {@link Resource} that opens a connection to the URL and obtains the {@link
         *     InputStream} for it.
         */
        static Resource from(URL url) {
            Objects.requireNonNull(url, "URL must not be null");
            return () -> url.openConnection().getInputStream();
        }

        /**
         * Create a {@link Resource} that obtains a {@link InputStream} from a {@link File}.
         *
         * @param file the File to obtain the {@link InputStream} from.
         * @return a {@link Resource} that obtains the {@link FileInputStream} for the given {@link
         *     File}.
         */
        static Resource from(File file) {
            Objects.requireNonNull(file, "File must not be null");
            return () -> new FileInputStream(file);
        }

        /**
         * Get the {@link InputStream} for the resource.
         *
         * @return the {@link InputStream}
         * @throws IOException if the resource cannot be accessed
         */
        InputStream get() throws IOException;
    }
}
