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
}

impl RotatingBuffer {
    fn with_pool(pool: Pool<Vec<u8>>) -> Self {
        let next_read = pool.new_rc();
        RotatingBuffer {
            pool,
            current_read_buffer: next_read,
        }
    }

    pub(super) fn new(initial_buffers: usize, buffer_size: usize) -> Self {
        let pool = pool()
            .with(StartingSize(initial_buffers))
            .with(Supplier(move || Vec::with_capacity(buffer_size)))
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
    }

    /// Replace the buffer, and copy the ending of the current incomplete message to the beginning of the next buffer.
    fn copy_from_old_buffer(
        &mut self,
        old_buffer: SharedBuffer,
        required_length: Option<usize>,
        cursor: usize,
    ) {
        self.match_capacity(required_length.unwrap_or_else(|| self.current_read_buffer.capacity()));

        let old_buffer_len = old_buffer.len();
        let slice = &old_buffer[cursor..old_buffer_len];
        debug_assert!(self.current_read_buffer.len() == 0);
        self.current_read_buffer.extend_from_slice(slice);
    }

    fn get_new_buffer(&mut self) -> Buffer {
        let mut buffer = self.pool.new_rc();
        buffer.clear();
        buffer
    }

    /// Parses the requests in the buffer.
    pub(super) fn get_requests(&mut self) -> io::Result<Vec<WholeRequest>> {
        let mut cursor = 0;
        // We replace the buffer on every call, because we want to prevent the next read from affecting existing results.
        let new_buffer = self.get_new_buffer();
        let buffer = Rc::new(mem::replace(&mut self.current_read_buffer, new_buffer));
        let mut results = vec![];
        let buffer_length = buffer.len();
        while cursor < buffer_length {
            let parse_result = self.parse_request(&(cursor..buffer_length), buffer.clone())?;
            match parse_result {
                RequestState::Complete {
                    request,
                    cursor_next: next,
                } => {
                    results.push(request);
                    cursor = next;
                }
                RequestState::PartialNoHeader => {
                    self.copy_from_old_buffer(buffer, None, cursor);
                    return Ok(results);
                }
                RequestState::PartialWithHeader { length } => {
                    self.copy_from_old_buffer(buffer, Some(length), cursor);
                    return Ok(results);
                }
            };
        }

        self.current_read_buffer.clear();
        Ok(results)
    }

    pub(super) fn current_buffer(&mut self) -> &mut Vec<u8> {
        debug_assert!(self.current_read_buffer.capacity() > self.current_read_buffer.len());
        &mut self.current_read_buffer
    }
}

#[cfg(test)]
mod tests {
    use byteorder::WriteBytesExt;
    use num_traits::ToPrimitive;

    use super::*;

    impl RotatingBuffer {
        fn write_to_buffer(&mut self, val: u32) {
            self.current_buffer()
                .write_u32::<LittleEndian>(val)
                .unwrap();
        }
    }

    fn write_message(
        rotating_buffer: &mut RotatingBuffer,
        length: usize,
        callback_index: u32,
        request_type: u32,
        key_length: Option<usize>,
    ) {
        let buffer = rotating_buffer.current_buffer();
        let capacity = buffer.capacity();
        let initial_buffer_length = buffer.len();
        rotating_buffer.write_to_buffer(length as u32);
        rotating_buffer.write_to_buffer(callback_index);
        rotating_buffer.write_to_buffer(request_type);
        if let Some(key_length) = key_length {
            rotating_buffer.write_to_buffer(key_length as u32);
        }
        if capacity > rotating_buffer.current_read_buffer.len() {
            let buffer = rotating_buffer.current_buffer();
            let mut message = vec![0_u8; length + initial_buffer_length - buffer.len()];
            buffer.append(&mut message);
        }
    }

    fn write_get_message(rotating_buffer: &mut RotatingBuffer, length: usize, callback_index: u32) {
        write_message(
            rotating_buffer,
            length,
            callback_index,
            RequestType::GetString.to_u32().unwrap(),
            None,
        );
    }

    fn write_set_message(
        rotating_buffer: &mut RotatingBuffer,
        length: usize,
        callback_index: u32,
        key_length: usize,
    ) {
        write_message(
            rotating_buffer,
            length,
            callback_index,
            RequestType::SetString.to_u32().unwrap(),
            Some(key_length),
        );
    }

    #[test]
    fn get_right_sized_buffer() {
        let mut rotating_buffer = RotatingBuffer::new(1, 128);
        assert_eq!(rotating_buffer.current_buffer().capacity(), 128);
        assert_eq!(rotating_buffer.current_buffer().len(), 0);
    }

    #[test]
    fn get_requests() {
        const BUFFER_SIZE: usize = 50;
        const FIRST_MESSAGE_LENGTH: usize = 18;
        const SECOND_MESSAGE_LENGTH: usize = BUFFER_SIZE - FIRST_MESSAGE_LENGTH;
        const SECOND_MESSAGE_KEY_LENGTH: usize = 4;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE as usize);
        write_get_message(&mut rotating_buffer, FIRST_MESSAGE_LENGTH, 100);
        write_set_message(
            &mut rotating_buffer,
            SECOND_MESSAGE_LENGTH,
            5,
            SECOND_MESSAGE_KEY_LENGTH,
        );

        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 2);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (HEADER_END..FIRST_MESSAGE_LENGTH)
            }
        );
        assert_eq!(requests[0].callback_index, 100);
        let second_message_key_start = FIRST_MESSAGE_LENGTH + HEADER_WITH_KEY_LENGTH_END;
        assert_eq!(
            requests[1].request_type,
            RequestRanges::Set {
                key: (second_message_key_start
                    ..second_message_key_start + SECOND_MESSAGE_KEY_LENGTH),
                value: (second_message_key_start + SECOND_MESSAGE_KEY_LENGTH..BUFFER_SIZE)
            }
        );
        assert_eq!(requests[1].callback_index, 5);
    }

    #[test]
    fn repeating_requests_from_same_buffer() {
        const BUFFER_SIZE: usize = 50;
        const FIRST_MESSAGE_LENGTH: usize = 18;
        const SECOND_MESSAGE_LENGTH: usize = BUFFER_SIZE - FIRST_MESSAGE_LENGTH;
        const SECOND_MESSAGE_KEY_LENGTH: usize = 4;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE);
        write_get_message(&mut rotating_buffer, FIRST_MESSAGE_LENGTH, 100);

        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (HEADER_END..FIRST_MESSAGE_LENGTH)
            }
        );
        assert_eq!(requests[0].callback_index, 100);

        write_set_message(
            &mut rotating_buffer,
            SECOND_MESSAGE_LENGTH,
            5,
            SECOND_MESSAGE_KEY_LENGTH,
        );
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Set {
                key: (HEADER_WITH_KEY_LENGTH_END..20),
                value: (20..SECOND_MESSAGE_LENGTH)
            }
        );
        assert_eq!(requests[0].callback_index, 5);
    }

    #[test]
    fn next_write_doesnt_affect_values() {
        const BUFFER_SIZE: u32 = 16;
        const MESSAGE_LENGTH: usize = 16;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE as usize);
        write_message(
            &mut rotating_buffer,
            MESSAGE_LENGTH,
            100,
            RequestType::GetString.to_u32().unwrap(),
            Some(usize::MAX),
        );

        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (HEADER_END..MESSAGE_LENGTH as usize)
            }
        );
        assert_eq!(requests[0].callback_index, 100);
        assert_eq!(
            (&requests[0].buffer[12..MESSAGE_LENGTH])
                .read_u32::<LittleEndian>()
                .unwrap(),
            u32::MAX
        );

        while rotating_buffer.current_read_buffer.len()
            < rotating_buffer.current_read_buffer.capacity()
        {
            rotating_buffer.current_read_buffer.push(0_u8);
        }
        assert_eq!(
            (&requests[0].buffer[12..MESSAGE_LENGTH])
                .read_u32::<LittleEndian>()
                .unwrap(),
            u32::MAX
        );
    }

    #[test]
    fn copy_partial_header_message_to_next_buffer() {
        const FIRST_MESSAGE_LENGTH: usize = 16;
        const SECOND_MESSAGE_LENGTH: usize = 24;
        let mut rotating_buffer = RotatingBuffer::new(1, 24);
        write_get_message(&mut rotating_buffer, FIRST_MESSAGE_LENGTH, 100);
        rotating_buffer.write_to_buffer(SECOND_MESSAGE_LENGTH as u32); // 2nd message length
        rotating_buffer.write_to_buffer(5); // 2nd message callback index
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (HEADER_END..16)
            }
        );
        assert_eq!(requests[0].callback_index, 100);

        rotating_buffer.write_to_buffer(2); // 2nd message operation type
        rotating_buffer.write_to_buffer(4); // 2nd message key length
        let buffer = rotating_buffer.current_buffer();
        assert_eq!(buffer.len(), HEADER_WITH_KEY_LENGTH_END);
        let mut message = vec![0_u8; SECOND_MESSAGE_LENGTH - buffer.len()];
        buffer.append(&mut message);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Set {
                key: (HEADER_WITH_KEY_LENGTH_END..20),
                value: (20..24)
            }
        );
        assert_eq!(requests[0].callback_index, 5);
    }

    #[test]
    fn copy_full_header_message_to_next_buffer_and_increase_buffer_size() {
        const FIRST_MESSAGE_LENGTH: usize = 16;
        const SECOND_MESSAGE_LENGTH: usize = 32;
        const BUFFER_SIZE: usize = SECOND_MESSAGE_LENGTH - 4;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE);
        write_get_message(&mut rotating_buffer, FIRST_MESSAGE_LENGTH, 100);
        rotating_buffer.write_to_buffer(SECOND_MESSAGE_LENGTH as u32); // 2nd message length
        rotating_buffer.write_to_buffer(5); // 2nd message callback index
        rotating_buffer.write_to_buffer(2); // 2nd message operation type
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (HEADER_END..FIRST_MESSAGE_LENGTH)
            }
        );
        assert_eq!(requests[0].callback_index, 100);

        rotating_buffer.write_to_buffer(8); // 2nd message key length
        let buffer = rotating_buffer.current_buffer();
        assert_eq!(buffer.len(), HEADER_WITH_KEY_LENGTH_END);
        let mut message = vec![0_u8; SECOND_MESSAGE_LENGTH - buffer.len()];
        buffer.append(&mut message);
        assert_eq!(buffer.len(), SECOND_MESSAGE_LENGTH);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Set {
                key: (HEADER_WITH_KEY_LENGTH_END..24),
                value: (24..32)
            }
        );
        assert_eq!(requests[0].callback_index, 5);
    }

    #[test]
    fn copy_partial_header_message_to_next_buffer_and_then_increase_size() {
        const FIRST_MESSAGE_LENGTH: usize = 16;
        const SECOND_MESSAGE_LENGTH: usize = 40;
        const BUFFER_SIZE: usize = SECOND_MESSAGE_LENGTH - 12;
        let mut rotating_buffer = RotatingBuffer::new(1, BUFFER_SIZE);
        write_get_message(&mut rotating_buffer, FIRST_MESSAGE_LENGTH, 100);
        rotating_buffer.write_to_buffer(SECOND_MESSAGE_LENGTH as u32); // 2nd message length
        rotating_buffer.write_to_buffer(5); // 2nd message callback index
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Get {
                key: (HEADER_END..FIRST_MESSAGE_LENGTH)
            }
        );
        assert_eq!(requests[0].callback_index, 100);

        rotating_buffer.write_to_buffer(2); // 2nd message operation type
        rotating_buffer.write_to_buffer(8); // 2nd message key length
        let buffer = rotating_buffer.current_buffer();
        let mut message = vec![0_u8; SECOND_MESSAGE_LENGTH - buffer.len()];
        buffer.append(&mut message);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_eq!(
            requests[0].request_type,
            RequestRanges::Set {
                key: (HEADER_WITH_KEY_LENGTH_END..24),
                value: (24..SECOND_MESSAGE_LENGTH)
            }
        );
        assert_eq!(requests[0].callback_index, 5);
    }
}
