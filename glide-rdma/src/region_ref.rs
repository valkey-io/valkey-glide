// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

/// Where in a client's registered memory a transfer should land or read from.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RegionRef {
    /// The client endpoint's fabric address. Sent at handshake, not per transfer.
    pub address: Vec<u8>,
    /// The key a peer presents to access the registered region.
    pub remote_key: u64,
    /// The buffer's virtual address on `FI_MR_VIRT_ADDR` providers like efa, and 0
    /// where addressing is by offset into the region, like tcp.
    pub remote_address: u64,
}

impl RegionRef {
    /// Command arguments a region reference occupies.
    pub const ARG_COUNT: usize = 2;

    /// The arguments a transfer carries: `<rkey> <remote-address>`.
    pub fn to_args(&self) -> [String; Self::ARG_COUNT] {
        [self.remote_key.to_string(), self.remote_address.to_string()]
    }
}

/// A string passed to [`decode_hex`] was not valid hex.
#[derive(Debug, thiserror::Error)]
#[error("invalid hex in address")]
pub struct InvalidHex;

/// Hex-encode opaque bytes for a RESP argument or reply.
pub fn encode_hex(bytes: &[u8]) -> String {
    const DIGITS: &[u8; 16] = b"0123456789abcdef";
    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        output.push(DIGITS[usize::from(byte >> 4)] as char);
        output.push(DIGITS[usize::from(byte & 0x0f)] as char);
    }
    output
}

/// Decode an [`encode_hex`] string back to bytes.
pub fn decode_hex(text: &[u8]) -> Result<Vec<u8>, InvalidHex> {
    let (pairs, remainder) = text.as_chunks::<2>();
    // A leftover byte means an odd-length input, which can't be valid hex.
    if !remainder.is_empty() {
        return Err(InvalidHex);
    }
    pairs
        .iter()
        .map(|&[high, low]| {
            let high = (high as char).to_digit(16).ok_or(InvalidHex)?;
            let low = (low as char).to_digit(16).ok_or(InvalidHex)?;
            Ok((high << 4 | low) as u8)
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::{decode_hex, encode_hex};

    #[test]
    fn hex_round_trips() {
        let bytes = vec![0x00, 0x0f, 0xf0, 0xff, 0xab, 0xcd];
        assert_eq!(encode_hex(&bytes), "000ff0ffabcd");
        assert_eq!(decode_hex(b"000ff0ffabcd").unwrap(), bytes);
    }

    #[test]
    fn rejects_odd_length_and_non_hex() {
        assert!(decode_hex(b"abc").is_err());
        assert!(decode_hex(b"zz").is_err());
    }
}
