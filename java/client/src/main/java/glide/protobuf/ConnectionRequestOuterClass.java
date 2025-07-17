/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.protobuf;

/**
 * Compatibility stub for protobuf ConnectionRequestOuterClass.
 * This provides basic compatibility for tests that reference the old protobuf-based implementation.
 */
public class ConnectionRequestOuterClass {
    
    /**
     * Compatibility stub for connection request types.
     */
    public enum ConnectionType {
        Standalone,
        Cluster
    }
    
    /**
     * Compatibility stub for TLS mode.
     */
    public enum TlsMode {
        NoTls,
        SecureTls,
        InsecureTls
    }
    
    /**
     * Compatibility stub for ReadFrom options that map to our actual ReadFrom enum.
     */
    public enum ReadFrom {
        Primary,
        PreferReplica,
        LowestLatency,
        AZAffinity
    }
    
    /**
     * Compatibility stub for connection request.
     */
    public static class ConnectionRequest {
        private ConnectionType connectionType;
        private TlsMode tlsMode;
        private ReadFrom readFrom;
        
        public ConnectionRequest() {
            this.connectionType = ConnectionType.Standalone;
            this.tlsMode = TlsMode.NoTls;
            this.readFrom = ReadFrom.Primary;
        }
        
        public ConnectionType getConnectionType() {
            return connectionType;
        }
        
        public void setConnectionType(ConnectionType connectionType) {
            this.connectionType = connectionType;
        }
        
        public TlsMode getTlsMode() {
            return tlsMode;
        }
        
        public void setTlsMode(TlsMode tlsMode) {
            this.tlsMode = tlsMode;
        }
        
        public ReadFrom getReadFrom() {
            return readFrom;
        }
        
        public void setReadFrom(ReadFrom readFrom) {
            this.readFrom = readFrom;
        }
    }
}