use std::{
    io::{self, Error, ErrorKind},
    mem,
    ops::Range,
    rc::Rc,
};

use byteorder::{LittleEndian, ReadBytesExt};
use lifeguard::{pool, Pool, StartingSize, Supplier};
use num_traits::FromPrimitive;

use super::headers::*;

/// An enum representing a request during the parsing phase.
pub(super) enum RequestState {
    /// Parsing completed.
    Complete {
        request: WholeRequest,
        /// The index of the beginning of the next request.
        cursor_next: usize,
    },
    /// Parsing failed because the buffer didn't contain the full header of the request.
    PartialNoHeader,
    /// Parsing failed because the buffer didn't contain the body of the request.
    PartialWithHeader {
        /// Length of the request.
        length: usize,
    },
}

pub(super) struct ReadHeader {
    pub(super) length: usize,
    pub(super) callback_index: u32,
    pub(super) request_type: RequestType,
}

/// An object handling a arranging read buffers, and parsing the data in the buffers into requests.
pub(super) struct RotatingBuffer {
    /// Object pool for the internal buffers.
    pool: Pool<Vec<u8>>,
    /// Buffer for next read request.
    current_read_buffer: Buffer,
    /// The index from which the next read request should start.
    next_read_index: usize,
}

impl RotatingBuffer {
    fn with_pool(pool: Pool<Vec<u8>>) -> Self {
        let next_read = pool.new_rc();
        RotatingBuffer {
            pool,
            current_read_buffer: next_read,
            next_read_index: 0,
        }
    }

    pub(super) fn new(initial_buffers: usize, buffer_size: usize) -> Self {
        let pool = pool()
            .with(StartingSize(initial_buffers))
            .with(Supplier(move || vec![0_u8; buffer_size]))
            .build();
        Self::with_pool(pool)
    }

    fn read_header(input: &[u8]) -> io::Result<ReadHeader> {
        let length = (&input[..MESSAGE_LENGTH_END]).read_u32::<LittleEndian>()? as usize;
        let callback_index =
            (&input[MESSAGE_LENGTH_END..CALLBACK_INDEX_END]).read_u32::<LittleEndian>()?;
        let request_type = (&input[CALLBACK_INDEX_END..HEADER_END]).read_u32::<LittleEndian>()?;
        let request_type = FromPrimitive::from_u32(request_type).ok_or_else(|| {
            Error::new(
                ErrorKind::InvalidInput,
                format!("failed to parse request type {}", request_type),
            )
        })?;
        Ok(ReadHeader {
            length,
            callback_index,
            request_type,
        })
    }

    fn parse_request(
        &self,
        request_range: &Range<usize>,
        buffer: SharedBuffer,
    ) -> io::Result<RequestState> {
        if request_range.len() < HEADER_END {
            return Ok(RequestState::PartialNoHeader);
        }

        let header = Self::read_header(&buffer[request_range.start..request_range.end])?;
        let header_end = request_range.start + HEADER_END;
        let next = request_range.start + header.length;
        if next > request_range.end {
            return Ok(RequestState::PartialWithHeader {
                length: header.length,
            });
        }
        // TODO - use serde for easier deserialization.
        let request = match header.request_type {
            RequestType::GetString => WholeRequest {
                callback_index: header.callback_index,
                request_type: RequestRanges::Get {
                    key: header_end..next,
                },
                buffer,
            },
            RequestType::SetString => {
                let key_start = header_end + 4;
                let key_length =
                    (&buffer[header_end..key_start]).read_u32::<LittleEndian>()? as usize;
                let key_end = key_start + key_length;
                WholeRequest {
                    callback_index: header.callback_index,
                    request_type: RequestRanges::Set {
                        key: key_start..key_end,
                        value: key_end..next,
                    },
                    buffer,
                }
            }
        };
        Ok(RequestState::Complete {
            request,
            cursor_next: next,
        })
    }

    /// Adjusts the current buffer size so that it will fit [required_length]
    fn match_capacity(&mut self, required_length: usize) {
        if required_length <= self.current_read_buffer.len() {
            return;
        }

        let extra_capacity = required_length.next_power_of_two() - self.current_read_buffer.len();
        self.current_read_buffer.reserve(extra_capacity);
        let capacity = self.current_read_buffer.capacity();
        unsafe { self.current_read_buffer.set_len(capacity) };
    }

    /// Replace the buffer, and copy the ending of the current incomplete message to the beginning of the next buffer.
    fn copy_from_old_buffer(
        &mut self,
        old_buffer: SharedBuffer,
        message_end: usize,
        required_length: Option<usize>,
        cursor: usize,
    ) {
        self.next_read_index = message_end - cursor;
        self.match_capacity(required_length.unwrap_or_else(|| self.current_read_buffer.capacity()));

        let slice = &old_buffer[cursor..message_end];
        let iter = slice.iter().copied();
        self.current_read_buffer
            .splice(..self.next_read_index, iter);
        debug_assert!(&self.current_read_buffer[..self.next_read_index] == slice);
    }

    fn get_new_buffer(&mut self) -> Buffer {
        let mut buffer = self.pool.new_rc();
        let capacity = buffer.capacity();
        // TODO - why do we sometimes get vectors with length 0?
        unsafe { buffer.set_len(capacity) };
        buffer
    }

    /// Parses the requests in the buffer, in the range [self.next_read_index .. self.next_read_index + size_of_read].
    pub(super) fn get_requests(&mut self, size_of_read: usize) -> io::Result<Vec<WholeRequest>> {
        let mut cursor = 0;
        let message_end = size_of_read + self.next_read_index;
        debug_assert!(message_end <= self.current_read_buffer.len());
        // We replace the buffer on every call, because we want to prevent the next read from affecting existing results.
        let new_buffer = self.get_new_buffer();
        let buffer = Rc::new(mem::replace(&mut self.current_read_buffer, new_buffer));
        let mut results = vec![];
        while cursor < message_end {
            let parse_result = self.parse_request(&(cursor..message_end), buffer.clone())?;
            match parse_result {
                RequestState::Complete {
                    request,
                    cursor_next: next,
                } => {
                    results.push(request);
                    cursor = next;
                }
                RequestState::PartialNoHeader => {
                    self.copy_from_old_buffer(buffer, message_end, None, cursor);
                    return Ok(results);
                }
                RequestState::PartialWithHeader { length } => {
                    self.copy_from_old_buffer(buffer, message_end, Some(length), cursor);
                    return Ok(results);
                }
            };
        }

        self.next_read_index = 0;
        Ok(results)
    }

    pub(super) fn current_buffer(&mut self) -> &mut [u8] {
        let read_buffer_length = self.current_read_buffer.len();
        debug_assert!(read_buffer_length > 0);
        let range = self.next_read_index..read_buffer_length;
        debug_assert!(!range.is_empty());
        &mut self.current_read_buffer[range]
    }
}

#[cfg(test)]
mod tests {
    use byteorder::WriteBytesExt;

    use super::*;

    #[test]
    fn get_right_sized_buffer() {
        let mut rotating_buffer = RotatingBuffer::new(1, 128);
        assert_eq!(rotating_buffer.current_buffer().len(), 128);
    }

    #[test]
    fn get_buffer_always_has_positive_length() {
        const BUFFER_SIZE: u32 = 24;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE as usize);
        let buffer = rotating_buffer.current_buffer();
        // write to the very end of the buffer, so it will fill without spillover.
        (&mut buffer[0..4])
            .write_u32::<LittleEndian>(BUFFER_SIZE)
            .unwrap(); // 1st message length
        (&mut buffer[4..8]).write_u32::<LittleEndian>(100).unwrap(); // 1st message callback index
        (&mut buffer[8..12]).write_u32::<LittleEndian>(1).unwrap(); // 1st message operation type
        let requests = rotating_buffer.get_requests(BUFFER_SIZE as usize).unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (12..BUFFER_SIZE as usize)
            }
        );
        assert_eq!(requests[0].callback_index, 100);
        assert!(!rotating_buffer.current_buffer().is_empty());
    }

    #[test]
    fn get_requests() {
        const BUFFER_SIZE: u32 = 50;
        const FIRST_MESSAGE_LENGTH: u32 = 18;
        const SECOND_MESSAGE_LENGTH: u32 = BUFFER_SIZE - FIRST_MESSAGE_LENGTH;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE as usize);
        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4])
            .write_u32::<LittleEndian>(FIRST_MESSAGE_LENGTH)
            .unwrap(); // 1st message length
        (&mut buffer[4..8]).write_u32::<LittleEndian>(100).unwrap(); // 1st message callback index
        (&mut buffer[8..12]).write_u32::<LittleEndian>(1).unwrap(); // 1st message operation type
        (&mut buffer[18..22])
            .write_u32::<LittleEndian>(SECOND_MESSAGE_LENGTH)
            .unwrap(); // 2nd message length
        (&mut buffer[22..26]).write_u32::<LittleEndian>(5).unwrap(); // 2nd message callback index
        (&mut buffer[26..30]).write_u32::<LittleEndian>(2).unwrap(); // 2nd message operation type
        (&mut buffer[30..34]).write_u32::<LittleEndian>(4).unwrap(); // 2nd message key length
        let requests = rotating_buffer.get_requests(50).unwrap();
        assert_eq!(requests.len(), 2);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (12..FIRST_MESSAGE_LENGTH as usize)
            }
        );
        assert_eq!(requests[0].callback_index, 100);
        assert_eq!(
            requests[1].request_type,
            RequestRanges::Set {
                key: (34..38),
                value: (38..BUFFER_SIZE as usize)
            }
        );
        assert_eq!(requests[1].callback_index, 5);
    }

    #[test]
    fn repeating_requests_from_same_buffer() {
        const BUFFER_SIZE: u32 = 50;
        const FIRST_MESSAGE_LENGTH: u32 = 18;
        const SECOND_MESSAGE_LENGTH: u32 = BUFFER_SIZE - FIRST_MESSAGE_LENGTH;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE as usize);
        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4])
            .write_u32::<LittleEndian>(FIRST_MESSAGE_LENGTH)
            .unwrap(); // 1st message length
        (&mut buffer[4..8]).write_u32::<LittleEndian>(100).unwrap(); // 1st message callback index
        (&mut buffer[8..12]).write_u32::<LittleEndian>(1).unwrap(); // 1st message operation type

        let requests = rotating_buffer
            .get_requests(FIRST_MESSAGE_LENGTH as usize)
            .unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (12..FIRST_MESSAGE_LENGTH as usize)
            }
        );
        assert_eq!(requests[0].callback_index, 100);

        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4])
            .write_u32::<LittleEndian>(SECOND_MESSAGE_LENGTH)
            .unwrap(); // 2nd message length
        (&mut buffer[4..8]).write_u32::<LittleEndian>(5).unwrap(); // 2nd message callback index
        (&mut buffer[8..12]).write_u32::<LittleEndian>(2).unwrap(); // 2nd message operation type
        (&mut buffer[12..16]).write_u32::<LittleEndian>(4).unwrap(); // 2nd message key length
        let requests = rotating_buffer
            .get_requests(SECOND_MESSAGE_LENGTH as usize)
            .unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Set {
                key: (16..20),
                value: (20..32)
            }
        );
        assert_eq!(requests[0].callback_index, 5);
    }

    #[test]
    fn next_write_doesnt_affect_values() {
        const BUFFER_SIZE: u32 = 16;
        const MESSAGE_LENGTH: usize = 16;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE as usize);
        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4])
            .write_u32::<LittleEndian>(MESSAGE_LENGTH as u32)
            .unwrap(); // 1st message length
        (&mut buffer[4..8]).write_u32::<LittleEndian>(100).unwrap(); // 1st message callback index
        (&mut buffer[8..12]).write_u32::<LittleEndian>(1).unwrap(); // 1st message operation type
        (&mut buffer[12..MESSAGE_LENGTH])
            .write_u32::<LittleEndian>(u32::MAX)
            .unwrap(); // 1st message operation type

        let requests = rotating_buffer
            .get_requests(MESSAGE_LENGTH as usize)
            .unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (12..MESSAGE_LENGTH as usize)
            }
        );
        assert_eq!(requests[0].callback_index, 100);
        assert_eq!(
            (&requests[0].buffer[12..MESSAGE_LENGTH])
                .read_u32::<LittleEndian>()
                .unwrap(),
            u32::MAX
        );

        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4]).write_u32::<LittleEndian>(0).unwrap();
        (&mut buffer[4..8]).write_u32::<LittleEndian>(0).unwrap();
        (&mut buffer[8..12]).write_u32::<LittleEndian>(0).unwrap();
        (&mut buffer[12..MESSAGE_LENGTH])
            .write_u32::<LittleEndian>(0)
            .unwrap();
        assert_eq!(
            (&requests[0].buffer[12..MESSAGE_LENGTH])
                .read_u32::<LittleEndian>()
                .unwrap(),
            u32::MAX
        );
    }

    #[test]
    fn copy_partial_header_message_to_next_buffer() {
        let mut rotating_buffer = RotatingBuffer::new(1, 24);
        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4]).write_u32::<LittleEndian>(16).unwrap(); // 1st message length
        (&mut buffer[4..8]).write_u32::<LittleEndian>(100).unwrap(); // 1st message callback index
        (&mut buffer[8..12]).write_u32::<LittleEndian>(1).unwrap(); // 1st message operation type
        (&mut buffer[16..20]).write_u32::<LittleEndian>(24).unwrap(); // 2nd message length
        (&mut buffer[20..24]).write_u32::<LittleEndian>(5).unwrap(); // 2nd message callback index
        let requests = rotating_buffer.get_requests(24).unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get { key: (12..16) }
        );
        assert_eq!(requests[0].callback_index, 100);

        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4]).write_u32::<LittleEndian>(2).unwrap(); // 2nd message operation type
        (&mut buffer[4..8]).write_u32::<LittleEndian>(4).unwrap(); // 2nd message key length
        let requests = rotating_buffer.get_requests(16).unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Set {
                key: (16..20),
                value: (20..24)
            }
        );
        assert_eq!(requests[0].callback_index, 5);
    }

    #[test]
    fn copy_full_header_message_to_next_buffer_and_increase_buffer_size() {
        let mut rotating_buffer = RotatingBuffer::new(1, 28);
        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4]).write_u32::<LittleEndian>(16).unwrap(); // 1st message length
        (&mut buffer[4..8]).write_u32::<LittleEndian>(100).unwrap(); // 1st message callback index
        (&mut buffer[8..12]).write_u32::<LittleEndian>(1).unwrap(); // 1st message operation type
        (&mut buffer[16..20]).write_u32::<LittleEndian>(32).unwrap(); // 2nd message length
        (&mut buffer[20..24]).write_u32::<LittleEndian>(5).unwrap(); // 2nd message callback index
        (&mut buffer[24..28]).write_u32::<LittleEndian>(2).unwrap(); // 2nd message operation type
        let requests = rotating_buffer.get_requests(28).unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get { key: (12..16) }
        );
        assert_eq!(requests[0].callback_index, 100);

        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4]).write_u32::<LittleEndian>(8).unwrap(); // 2nd message key length
        let requests = rotating_buffer.get_requests(20).unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Set {
                key: (16..24),
                value: (24..32)
            }
        );
        assert_eq!(requests[0].callback_index, 5);
    }

    #[test]
    fn copy_partial_header_message_to_next_buffer_and_then_increase_size() {
        let mut rotating_buffer = RotatingBuffer::new(1, 28);
        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4]).write_u32::<LittleEndian>(16).unwrap(); // 1st message length
        (&mut buffer[4..8]).write_u32::<LittleEndian>(100).unwrap(); // 1st message callback index
        (&mut buffer[8..12]).write_u32::<LittleEndian>(1).unwrap(); // 1st message operation type
        let message_length = 40;
        (&mut buffer[16..20])
            .write_u32::<LittleEndian>(message_length)
            .unwrap(); // 2nd message length
        (&mut buffer[20..24]).write_u32::<LittleEndian>(5).unwrap(); // 2nd message callback index
        let requests = rotating_buffer.get_requests(24).unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get { key: (12..16) }
        );
        assert_eq!(requests[0].callback_index, 100);

        let buffer = rotating_buffer.current_buffer();
        (&mut buffer[0..4]).write_u32::<LittleEndian>(2).unwrap(); // 2nd message operation type
        (&mut buffer[4..8]).write_u32::<LittleEndian>(8).unwrap(); // 2nd message key length
        let middle_write = buffer.len();
        let requests = rotating_buffer.get_requests(middle_write).unwrap();
        assert_eq!(requests.len(), 0);

        let requests = rotating_buffer
            .get_requests(message_length as usize - middle_write)
            .unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Set {
                key: (16..24),
                value: (24..40)
            }
        );
        assert_eq!(requests[0].callback_index, 5);
    }
}
