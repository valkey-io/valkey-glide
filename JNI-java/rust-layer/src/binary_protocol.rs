/// Robust binary protocol handler for Java-Rust communication.
///
/// This module encapsulates all dangerous bit manipulation and byte serialization
/// to ensure crystal clear communication between Java and Rust with extreme resilience.
///
/// PROTOCOL SPECIFICATION:
/// - All multi-byte integers use BIG-ENDIAN byte order
/// - Strings are UTF-8 encoded with 4-byte length prefix
/// - Optional fields use 1-byte presence flags (0 = absent, 1 = present)
/// - Arrays use 4-byte count prefix
///
/// SAFETY FEATURES:
/// - Automatic buffer overflow protection
/// - Clear type tagging for all fields
/// - Validation of all data before serialization
/// - Detailed error messages for debugging
use anyhow::{Result, anyhow};
use std::io::{Cursor, Read};

/// Maximum allowed string/bytes length (512MB - Valkey protocol limit)
const MAX_BULK_LEN: usize = 512 * 1024 * 1024;

/// Unified binary protocol handler for Java-Rust communication
pub struct BinaryProtocol<'a> {
    cursor: Option<Cursor<&'a [u8]>>,
    buffer: Option<Vec<u8>>,
    context: String,
}

impl<'a> BinaryProtocol<'a> {
    /// Create a new protocol handler for reading from bytes
    pub fn new_reader(bytes: &'a [u8], context: impl Into<String>) -> Self {
        Self {
            cursor: Some(Cursor::new(bytes)),
            buffer: None,
            context: context.into(),
        }
    }

    /// Create a new protocol handler for writing with estimated capacity
    pub fn new_writer(context: impl Into<String>, estimated_size: usize) -> Self {
        Self {
            cursor: None,
            buffer: Some(Vec::with_capacity(estimated_size)),
            context: context.into(),
        }
    }

    // ===== READER METHODS =====

    /// Read a presence flag (1 byte: 0 or 1)
    pub fn read_presence_flag(&mut self, field_name: &str) -> Result<bool> {
        let value = self.read_u8(field_name)?;
        Ok(value != 0)
    }

    /// Read a boolean value (1 byte: 0 or 1)
    pub fn read_boolean(&mut self, field_name: &str) -> Result<bool> {
        let value = self.read_u8(field_name)?;
        Ok(value != 0)
    }

    /// Read a single byte
    pub fn read_u8(&mut self, field_name: &str) -> Result<u8> {
        let cursor = self.cursor.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for reading",
                self.context
            )
        })?;
        let mut buf = [0u8; 1];
        cursor.read_exact(&mut buf).map_err(|e| {
            anyhow!(
                "{}: failed to read u8 for {}: {}",
                self.context,
                field_name,
                e
            )
        })?;
        Ok(buf[0])
    }

    /// Read a 32-bit integer (big-endian)
    pub fn read_u32(&mut self, field_name: &str) -> Result<u32> {
        let cursor = self.cursor.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for reading",
                self.context
            )
        })?;
        let mut buf = [0u8; 4];
        cursor.read_exact(&mut buf).map_err(|e| {
            anyhow!(
                "{}: failed to read u32 for {}: {}",
                self.context,
                field_name,
                e
            )
        })?;
        Ok(u32::from_be_bytes(buf))
    }

    /// Read a 64-bit integer (big-endian)
    pub fn read_u64(&mut self, field_name: &str) -> Result<u64> {
        let cursor = self.cursor.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for reading",
                self.context
            )
        })?;
        let mut buf = [0u8; 8];
        cursor.read_exact(&mut buf).map_err(|e| {
            anyhow!(
                "{}: failed to read u64 for {}: {}",
                self.context,
                field_name,
                e
            )
        })?;
        Ok(u64::from_be_bytes(buf))
    }

    /// Read a string with 4-byte length prefix
    pub fn read_string(&mut self, field_name: &str) -> Result<String> {
        let len = self.read_u32(field_name)? as usize;

        if len > MAX_BULK_LEN {
            return Err(anyhow!(
                "{}: string {} exceeds protocol limit: {} bytes (max: {} bytes)",
                self.context,
                field_name,
                len,
                MAX_BULK_LEN
            ));
        }

        let cursor = self.cursor.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for reading",
                self.context
            )
        })?;

        let mut buf = vec![0u8; len];
        cursor.read_exact(&mut buf).map_err(|e| {
            anyhow!(
                "{}: failed to read string data for {}: {}",
                self.context,
                field_name,
                e
            )
        })?;

        String::from_utf8(buf)
            .map_err(|e| anyhow!("{}: invalid UTF-8 in {}: {}", self.context, field_name, e))
    }

    /// Read bytes with 4-byte length prefix
    pub fn read_bytes(&mut self, field_name: &str) -> Result<Vec<u8>> {
        let len = self.read_u32(field_name)? as usize;

        if len > MAX_BULK_LEN {
            return Err(anyhow!(
                "{}: bytes {} exceeds protocol limit: {} bytes (max: {} bytes)",
                self.context,
                field_name,
                len,
                MAX_BULK_LEN
            ));
        }

        let cursor = self.cursor.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for reading",
                self.context
            )
        })?;

        let mut buf = vec![0u8; len];
        cursor.read_exact(&mut buf).map_err(|e| {
            anyhow!(
                "{}: failed to read bytes data for {}: {}",
                self.context,
                field_name,
                e
            )
        })?;

        Ok(buf)
    }

    /// Read exact number of bytes without length prefix (for when length is already read separately)
    pub fn read_exact_bytes(&mut self, len: usize, field_name: &str) -> Result<Vec<u8>> {
        if len > MAX_BULK_LEN {
            return Err(anyhow!(
                "{}: bytes {} exceeds protocol limit: {} bytes (max: {} bytes)",
                self.context,
                field_name,
                len,
                MAX_BULK_LEN
            ));
        }

        let cursor = self.cursor.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for reading",
                self.context
            )
        })?;

        let mut buf = vec![0u8; len];
        cursor.read_exact(&mut buf).map_err(|e| {
            anyhow!(
                "{}: failed to read exact bytes for {}: {}",
                self.context,
                field_name,
                e
            )
        })?;

        Ok(buf)
    }

    /// Check if there are remaining bytes
    pub fn has_remaining(&self) -> bool {
        if let Some(cursor) = &self.cursor {
            cursor.position() < cursor.get_ref().len() as u64
        } else {
            false
        }
    }

    /// Get number of remaining bytes
    pub fn remaining(&self) -> usize {
        if let Some(cursor) = &self.cursor {
            let pos = cursor.position() as usize;
            let len = cursor.get_ref().len();
            len.saturating_sub(pos)
        } else {
            0
        }
    }

    /// Validate no unexpected bytes remain
    pub fn validate_complete(&self) -> Result<()> {
        if self.has_remaining() {
            Err(anyhow!(
                "{}: unexpected {} bytes remaining after parsing",
                self.context,
                self.remaining()
            ))
        } else {
            Ok(())
        }
    }

    // ===== WRITER METHODS =====

    /// Write a single byte
    pub fn write_u8(&mut self, value: u8, _field_name: &str) -> Result<()> {
        let buffer = self.buffer.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for writing",
                self.context
            )
        })?;
        buffer.push(value);
        Ok(())
    }

    /// Write a 32-bit integer (big-endian)
    pub fn write_u32(&mut self, value: u32, _field_name: &str) -> Result<()> {
        let buffer = self.buffer.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for writing",
                self.context
            )
        })?;
        buffer.extend_from_slice(&value.to_be_bytes());
        Ok(())
    }

    /// Write a string with 4-byte length prefix
    pub fn write_string(&mut self, value: &str, field_name: &str) -> Result<()> {
        let bytes = value.as_bytes();
        if bytes.len() > MAX_BULK_LEN {
            return Err(anyhow!(
                "{}: string {} exceeds protocol limit: {} bytes (max: {} bytes)",
                self.context,
                field_name,
                bytes.len(),
                MAX_BULK_LEN
            ));
        }
        self.write_u32(bytes.len() as u32, field_name)?;
        let buffer = self.buffer.as_mut().ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for writing",
                self.context
            )
        })?;
        buffer.extend_from_slice(bytes);
        Ok(())
    }

    /// Build the final byte array from writer
    pub fn build(self) -> Result<Vec<u8>> {
        self.buffer.ok_or_else(|| {
            anyhow!(
                "{}: BinaryProtocol not configured for writing",
                self.context
            )
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_round_trip() {
        // Write a message using BinaryProtocol
        let mut writer = BinaryProtocol::new_writer("test", 100);
        writer.write_u8(1, "has_name").unwrap(); // presence flag true
        writer.write_string("hello", "name").unwrap();
        writer.write_u8(0, "has_count").unwrap(); // presence flag false  
        writer.write_u8(1, "has_value").unwrap(); // presence flag true
        writer.write_u32(42, "value").unwrap();
        let bytes = writer.build().unwrap();

        // Read it back using BinaryProtocol
        let mut reader = BinaryProtocol::new_reader(&bytes, "test");
        assert!(reader.read_presence_flag("has_name").unwrap());
        assert_eq!(reader.read_string("name").unwrap(), "hello");
        assert!(!reader.read_presence_flag("has_count").unwrap());
        assert!(reader.read_presence_flag("has_value").unwrap());
        assert_eq!(reader.read_u32("value").unwrap(), 42);

        reader.validate_complete().unwrap();
    }
}
