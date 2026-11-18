// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! CRC-32c, the integrity check the RDMA protocol carries.

use crc_fast::CrcAlgorithm;

/// CRC-32c of `bytes`
pub fn checksum(bytes: &[u8]) -> u32 {
    crc_fast::checksum(CrcAlgorithm::Crc32Iscsi, bytes) as u32
}

#[cfg(test)]
mod tests {
    use super::checksum;

    #[test]
    fn matches_known_crc32c_vectors() {
        assert_eq!(checksum(b""), 0x0000_0000);
        assert_eq!(checksum(b"123456789"), 0xE306_9283);
    }

    #[test]
    fn detects_a_single_bit_flip() {
        assert_ne!(
            checksum(b"hello over the fabric"),
            checksum(b"hallo over the fabric")
        );
    }

    #[test]
    fn is_order_sensitive() {
        assert_ne!(checksum(b"ab"), checksum(b"ba"));
    }
}
