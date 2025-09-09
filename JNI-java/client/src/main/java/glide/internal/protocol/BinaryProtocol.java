package glide.internal.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Unified binary protocol handler for Java-Rust communication.
 * Similar to the Rust BinaryProtocol struct, provides reader/writer modes
 * with deterministic, safe binary data handling.
 * 
 * PROTOCOL SPECIFICATION:
 * - All multi-byte integers use BIG-ENDIAN byte order
 * - Strings are UTF-8 encoded with 4-byte length prefix
 * - Optional fields use 1-byte presence flags (0 = absent, 1 = present)
 * - Arrays use 4-byte count prefix
 * 
 * UNIFIED DESIGN:
 * - Single entry point for all binary protocol operations
 * - Reader/writer modes like Rust implementation
 * - Deterministic error handling and validation
 * - Clean API without scattered builders
 */
public class BinaryProtocol {
    
    private ByteBuffer buffer;
    private final String context;
    private final Mode mode;
    
    /**
     * Protocol operation mode.
     */
    public enum Mode {
        READER,
        WRITER
    }
    
    /**
     * Create a new binary protocol reader from bytes.
     */
    public static BinaryProtocol newReader(byte[] bytes, String context) {
        return new BinaryProtocol(bytes, context, Mode.READER);
    }
    
    /**
     * Create a new binary protocol writer with initial capacity.
     */
    public static BinaryProtocol newWriter(String context) {
        return new BinaryProtocol(null, context, Mode.WRITER);
    }
    
    /**
     * Private constructor for reader/writer creation.
     */
    private BinaryProtocol(byte[] bytes, String context, Mode mode) {
        this.context = context != null ? context : "unknown";
        this.mode = mode;
        
        if (mode == Mode.READER) {
            if (bytes == null) {
                throw new IllegalArgumentException("Reader mode requires input bytes");
            }
            this.buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asReadOnlyBuffer();
        } else {
            // Writer mode starts with 1KB initial capacity
            this.buffer = ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN);
        }
    }
    
    // ==================== READER METHODS ====================
    
    /**
     * Read presence flag (1 byte boolean).
     */
    public boolean readPresenceFlag(String fieldName) {
        ensureReaderMode();
        ensureRemaining(1, "presence flag for " + fieldName);
        return buffer.get() != 0;
    }
    
    /**
     * Read boolean value (1 byte).
     */
    public boolean readBoolean(String fieldName) {
        ensureReaderMode();
        ensureRemaining(1, "boolean " + fieldName);
        return buffer.get() != 0;
    }
    
    /**
     * Read 32-bit integer (4 bytes, big-endian).
     */
    public int readInt32(String fieldName) {
        ensureReaderMode();
        ensureRemaining(4, "int32 " + fieldName);
        return buffer.getInt();
    }
    
    /**
     * Read 64-bit long (8 bytes, big-endian).
     */
    public long readInt64(String fieldName) {
        ensureReaderMode();
        ensureRemaining(8, "int64 " + fieldName);
        return buffer.getLong();
    }
    
    /**
     * Read UTF-8 string with 4-byte length prefix.
     */
    public String readString(String fieldName) {
        ensureReaderMode();
        ensureRemaining(4, "string length prefix for " + fieldName);
        
        int length = buffer.getInt();
        if (length < 0) {
            throw new IllegalStateException("Invalid string length: " + length + " for field " + fieldName + " in " + context);
        }
        
        ensureRemaining(length, "string content for " + fieldName);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    /**
     * Read byte array with 4-byte length prefix.
     */
    public byte[] readBytes(String fieldName) {
        ensureReaderMode();
        ensureRemaining(4, "bytes length prefix for " + fieldName);
        
        int length = buffer.getInt();
        if (length < 0) {
            throw new IllegalStateException("Invalid bytes length: " + length + " for field " + fieldName + " in " + context);
        }
        if (length == 0) {
            return new byte[0];
        }
        
        ensureRemaining(length, "bytes content for " + fieldName);
        byte[] result = new byte[length];
        buffer.get(result);
        return result;
    }
    
    // ==================== WRITER METHODS ====================
    
    /**
     * Write presence flag (1 byte boolean).
     */
    public BinaryProtocol writePresenceFlag(boolean present, String fieldName) {
        ensureWriterMode();
        ensureCapacity(1);
        buffer.put((byte) (present ? 1 : 0));
        return this;
    }
    
    /**
     * Write boolean value (1 byte).
     */
    public BinaryProtocol writeBoolean(boolean value, String fieldName) {
        ensureWriterMode();
        ensureCapacity(1);
        buffer.put((byte) (value ? 1 : 0));
        return this;
    }
    
    /**
     * Write 32-bit integer (4 bytes, big-endian).
     */
    public BinaryProtocol writeInt32(int value, String fieldName) {
        ensureWriterMode();
        ensureCapacity(4);
        buffer.putInt(value);
        return this;
    }
    
    /**
     * Write 64-bit long (8 bytes, big-endian).
     */
    public BinaryProtocol writeInt64(long value, String fieldName) {
        ensureWriterMode();
        ensureCapacity(8);
        buffer.putLong(value);
        return this;
    }
    
    /**
     * Write UTF-8 string with 4-byte length prefix.
     */
    public BinaryProtocol writeString(String value, String fieldName) {
        ensureWriterMode();
        if (value == null) {
            throw new IllegalArgumentException("String value cannot be null for field " + fieldName + " in " + context);
        }
        
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ensureCapacity(4 + bytes.length);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        return this;
    }
    
    /**
     * Write byte array with 4-byte length prefix.
     */
    public BinaryProtocol writeBytes(byte[] value, String fieldName) {
        ensureWriterMode();
        if (value == null) {
            throw new IllegalArgumentException("Byte array cannot be null for field " + fieldName + " in " + context);
        }
        
        ensureCapacity(4 + value.length);
        buffer.putInt(value.length);
        buffer.put(value);
        return this;
    }
    
    /**
     * Functional interface for writing typed values.
     */
    @FunctionalInterface
    public interface Writer<T> {
        void write(BinaryProtocol protocol, T value, String fieldName);
    }
    
    /**
     * Write optional field with type-safe writer function.
     */
    public <T> BinaryProtocol writeOptional(T value, String fieldName, Writer<T> writer) {
        ensureWriterMode();
        writePresenceFlag(value != null, fieldName);
        if (value != null) {
            writer.write(this, value, fieldName);
        }
        return this;
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Get the final byte array from writer mode.
     */
    public byte[] toByteArray() {
        ensureWriterMode();
        buffer.flip();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Check if more bytes are available to read.
     */
    public boolean hasRemaining() {
        ensureReaderMode();
        return buffer.hasRemaining();
    }
    
    /**
     * Get number of bytes remaining to read.
     */
    public int remaining() {
        ensureReaderMode();
        return buffer.remaining();
    }
    
    // ==================== INTERNAL HELPERS ====================
    
    private void ensureReaderMode() {
        if (mode != Mode.READER) {
            throw new IllegalStateException("Operation requires reader mode in " + context);
        }
    }
    
    private void ensureWriterMode() {
        if (mode != Mode.WRITER) {
            throw new IllegalStateException("Operation requires writer mode in " + context);
        }
    }
    
    private void ensureRemaining(int needed, String operation) {
        if (buffer.remaining() < needed) {
            throw new IllegalStateException(
                "Not enough bytes for " + operation + " in " + context + 
                ": needed " + needed + ", available " + buffer.remaining());
        }
    }
    
    private void ensureCapacity(int needed) {
        if (buffer.remaining() < needed) {
            // Grow buffer capacity
            int currentCapacity = buffer.capacity();
            int newCapacity = Math.max(currentCapacity * 2, currentCapacity + needed);
            
            ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity).order(ByteOrder.BIG_ENDIAN);
            buffer.flip();
            newBuffer.put(buffer);
            
            // Replace the buffer for dynamic growth
            this.buffer = newBuffer;
        }
    }
    
    // ==================== DEBUG UTILITIES ====================
    
    /**
     * Convert byte array to hex string for debugging.
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
    
    /**
     * Get debug information about current buffer state.
     */
    public String getDebugInfo() {
        return String.format("[%s] %s mode, position=%d, remaining=%d, capacity=%d", 
                context, mode, buffer.position(), buffer.remaining(), buffer.capacity());
    }
}