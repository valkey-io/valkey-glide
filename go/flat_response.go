// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
import "C"

import (
	"encoding/binary"
	"fmt"
	"math"
	"strings"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// FlatResponse tags - must match ffi/src/lib.rs serialize_command_response
const (
	tagNull   = 0
	tagInt    = 1
	tagFloat  = 2
	tagBool   = 3
	tagString = 4
	tagArray  = 5
	tagMap    = 6
	tagSets   = 7
	tagOk     = 8
	tagError  = 9
)

// flatReader reads values from a flat buffer serialized by Rust.
type flatReader struct {
	data []byte
	pos  int
}

func newFlatReader(data *C.uint8_t, length C.size_t) *flatReader {
	return &flatReader{
		data: unsafe.Slice((*byte)(unsafe.Pointer(data)), int(length)),
		pos:  0,
	}
}

func (r *flatReader) readByte() (byte, error) {
	if r.pos >= len(r.data) {
		return 0, fmt.Errorf("flat buffer: unexpected end of data at pos %d", r.pos)
	}
	b := r.data[r.pos]
	r.pos++
	return b, nil
}

func (r *flatReader) readU32() (uint32, error) {
	if r.pos+4 > len(r.data) {
		return 0, fmt.Errorf("flat buffer: unexpected end of data reading u32 at pos %d", r.pos)
	}
	v := binary.LittleEndian.Uint32(r.data[r.pos:])
	r.pos += 4
	return v, nil
}

func (r *flatReader) readI64() (int64, error) {
	if r.pos+8 > len(r.data) {
		return 0, fmt.Errorf("flat buffer: unexpected end of data reading i64 at pos %d", r.pos)
	}
	v := int64(binary.LittleEndian.Uint64(r.data[r.pos:]))
	r.pos += 8
	return v, nil
}

func (r *flatReader) readF64() (float64, error) {
	if r.pos+8 > len(r.data) {
		return 0, fmt.Errorf("flat buffer: unexpected end of data reading f64 at pos %d", r.pos)
	}
	bits := binary.LittleEndian.Uint64(r.data[r.pos:])
	r.pos += 8
	return math.Float64frombits(bits), nil
}

func (r *flatReader) readBytes(n int) ([]byte, error) {
	if r.pos+n > len(r.data) {
		return nil, fmt.Errorf("flat buffer: unexpected end of data reading %d bytes at pos %d", n, r.pos)
	}
	// Zero-copy: return a slice of the underlying buffer.
	// Safe because the flat buffer (malloc'd) outlives the reader.
	b := r.data[r.pos : r.pos+n]
	r.pos += n
	return b, nil
}

// parseAny reads one value from the flat buffer and returns it as any.
func (r *flatReader) parseAny() (any, error) {
	tag, err := r.readByte()
	if err != nil {
		return nil, err
	}

	switch tag {
	case tagNull:
		return nil, nil

	case tagInt:
		return r.readI64()

	case tagFloat:
		return r.readF64()

	case tagBool:
		b, err := r.readByte()
		if err != nil {
			return nil, err
		}
		return b != 0, nil

	case tagString:
		length, err := r.readU32()
		if err != nil {
			return nil, err
		}
		return r.readBytes(int(length))

	case tagArray:
		count, err := r.readU32()
		if err != nil {
			return nil, err
		}
		arr := make([]any, count)
		for i := uint32(0); i < count; i++ {
			arr[i], err = r.parseAny()
			if err != nil {
				return nil, err
			}
		}
		return arr, nil

	case tagMap:
		count, err := r.readU32()
		if err != nil {
			return nil, err
		}
		m := make(map[any]any, count)
		for i := uint32(0); i < count; i++ {
			key, err := r.parseAny()
			if err != nil {
				return nil, err
			}
			val, err := r.parseAny()
			if err != nil {
				return nil, err
			}
			// Map keys must be comparable; byte slices aren't, so convert to string
			switch k := key.(type) {
			case []byte:
				m[string(k)] = val
			default:
				m[k] = val
			}
		}
		return m, nil

	case tagSets:
		count, err := r.readU32()
		if err != nil {
			return nil, err
		}
		set := make([]any, count)
		for i := uint32(0); i < count; i++ {
			set[i], err = r.parseAny()
			if err != nil {
				return nil, err
			}
		}
		return set, nil

	case tagOk:
		return "OK", nil

	case tagError:
		length, err := r.readU32()
		if err != nil {
			return nil, err
		}
		msg, err := r.readBytes(int(length))
		if err != nil {
			return nil, err
		}
		return nil, fmt.Errorf("%s", string(msg))

	default:
		return nil, fmt.Errorf("flat buffer: unknown tag %d at pos %d", tag, r.pos-1)
	}
}

// parseString reads a value and expects it to be a string (byte slice).
func (r *flatReader) parseString() (string, bool, error) {
	tag, err := r.readByte()
	if err != nil {
		return "", false, err
	}
	switch tag {
	case tagNull:
		return "", true, nil
	case tagString:
		length, err := r.readU32()
		if err != nil {
			return "", false, err
		}
		if r.pos+int(length) > len(r.data) {
			return "", false, fmt.Errorf("flat buffer: unexpected end of data reading string at pos %d", r.pos)
		}
		// Zero-copy string: points directly into the flat buffer.
		// Safe because the malloc'd buffer outlives the caller (freed in cr.free()).
		s := unsafe.String(&r.data[r.pos], int(length))
		r.pos += int(length)
		return s, false, nil
	case tagOk:
		return "OK", false, nil
	default:
		return "", false, fmt.Errorf("flat buffer: expected string, got tag %d", tag)
	}
}

// parseI64 reads a value and expects it to be an int64.
func (r *flatReader) parseI64() (int64, bool, error) {
	tag, err := r.readByte()
	if err != nil {
		return 0, false, err
	}
	switch tag {
	case tagNull:
		return 0, true, nil
	case tagInt:
		v, err := r.readI64()
		return v, false, err
	default:
		return 0, false, fmt.Errorf("flat buffer: expected int, got tag %d", tag)
	}
}

// SerializeAndFree serializes a CommandResponse to a flat buffer and frees the original.
// Returns the flat buffer data that the caller must free with C.free_flat_buffer_response.
func serializeResponseToFlatBuffer(response *C.struct_CommandResponse) C.struct_FlatBufferResponse {
	return C.serialize_response_to_flat_buffer(response)
}


// ==================== Flat Buffer Handler Functions ====================
// These are drop-in replacements for the CGo struct-walking handlers in
// response_handlers.go. When the Rust side sends a FlatBuffer response type
// (type tag 10), the flat buffer data is embedded directly in string_value/
// string_value_len, bypassing CommandResponse struct tree entirely.

const cFlatBufferType = 10 // Must match ResponseType::FlatBuffer in Rust

// getFlatReader gets a flatReader from a CommandResponse (legacy path).
func getFlatReader(response *C.struct_CommandResponse) *flatReader {
	if response != nil {
		fb := C.serialize_response_to_flat_buffer(response)
		defer C.free_flat_buffer_response(fb)
		data := C.GoBytes(unsafe.Pointer(fb.data), C.int(fb.len))
		return &flatReader{data: data, pos: 0}
	}
	return &flatReader{data: []byte{0}, pos: 0}
}

func handleFlatStringResponse(response *C.struct_CommandResponse) (string, error) {
	r := getFlatReader(response)
	s, isNil, err := r.parseString()
	if err != nil {
		return "", err
	}
	if isNil {
		return "", fmt.Errorf("unexpected nil string response")
	}
	return s, nil
}

func handleFlatStringOrNilResponse(response *C.struct_CommandResponse) (models.Result[string], error) {
	r := getFlatReader(response)
	s, isNil, err := r.parseString()
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	if isNil {
		return models.CreateNilStringResult(), nil
	}
	return models.CreateStringResult(s), nil
}

func handleFlatIntResponse(response *C.struct_CommandResponse) (int64, error) {
	r := getFlatReader(response)
	v, isNil, err := r.parseI64()
	if err != nil {
		return 0, err
	}
	if isNil {
		return 0, fmt.Errorf("unexpected nil int response")
	}
	return v, nil
}

func handleFlatOkResponse(response *C.struct_CommandResponse) (string, error) {
	r := getFlatReader(response)
	tag, err := r.readByte()
	if err != nil {
		return "", err
	}
	if tag == tagOk {
		return "OK", nil
	}
	return "", fmt.Errorf("expected OK, got tag %d", tag)
}

func handleFlatStringArrayResponse(response *C.struct_CommandResponse) ([]string, error) {
	r := getFlatReader(response)
	tag, err := r.readByte()
	if err != nil {
		return nil, err
	}
	if tag == tagNull {
		return nil, nil
	}
	if tag != tagArray {
		return nil, fmt.Errorf("expected array, got tag %d", tag)
	}
	count, err := r.readU32()
	if err != nil {
		return nil, err
	}
	result := make([]string, count)
	for i := uint32(0); i < count; i++ {
		s, _, err := r.parseString()
		if err != nil {
			return nil, err
		}
		result[i] = s
	}
	return result, nil
}


// getFlatReader gets a flatReader from a commandResult, preferring the flat buffer.
func getFlatReaderDirect(cr commandResult) *flatReader {
	if cr.flatBuf != nil {
		return &flatReader{data: cr.flatBuf, pos: 0}
	}
	// Fallback: serialize the CommandResponse struct tree
	if cr.response != nil {
		fb := C.serialize_response_to_flat_buffer(cr.response)
		defer C.free_flat_buffer_response(fb)
		data := C.GoBytes(unsafe.Pointer(fb.data), C.int(fb.len))
		C.free_command_response(cr.response)
		return &flatReader{data: data, pos: 0}
	}
	return &flatReader{data: []byte{0}, pos: 0} // Null
}

// free releases the resources held by a commandResult.
func (cr *commandResult) free() {
	if cr.flatBufPtr != nil {
		C.free(cr.flatBufPtr)
		cr.flatBufPtr = nil
		cr.flatBuf = nil
	}
	if cr.response != nil {
		C.free_command_response(cr.response)
		cr.response = nil
	}
}

func handleFlatStringOrNilResponseDirect(cr commandResult) (models.Result[string], error) {
	defer cr.free()
	r := getFlatReaderDirect(cr)
	s, isNil, err := r.parseString()
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	if isNil {
		return models.CreateNilStringResult(), nil
	}
	// Copy the string since it may point into the malloc'd buffer being freed
	return models.CreateStringResult(strings.Clone(s)), nil
}

func handleFlatIntResponseDirect(cr commandResult) (int64, error) {
	defer cr.free()
	r := getFlatReaderDirect(cr)
	v, isNil, err := r.parseI64()
	if err != nil {
		return 0, err
	}
	if isNil {
		return 0, fmt.Errorf("unexpected nil int response")
	}
	return v, nil
}

func handleFlatOkResponseDirect(cr commandResult) (string, error) {
	defer cr.free()
	r := getFlatReaderDirect(cr)
	tag, err := r.readByte()
	if err != nil {
		return "", err
	}
	if tag == tagOk {
		return "OK", nil
	}
	return "", fmt.Errorf("expected OK, got tag %d", tag)
}

func handleFlatStringArrayResponseDirect(cr commandResult) ([]string, error) {
	defer cr.free()
	r := getFlatReaderDirect(cr)
	tag, err := r.readByte()
	if err != nil {
		return nil, err
	}
	if tag == tagNull {
		return nil, nil
	}
	if tag != tagArray {
		return nil, fmt.Errorf("expected array, got tag %d", tag)
	}
	count, err := r.readU32()
	if err != nil {
		return nil, err
	}

	// First pass: compute total string bytes needed so we can do one allocation.
	savedPos := r.pos
	totalBytes := 0
	for i := uint32(0); i < count; i++ {
		t, err := r.readByte()
		if err != nil {
			return nil, err
		}
		switch t {
		case tagString:
			length, err := r.readU32()
			if err != nil {
				return nil, err
			}
			totalBytes += int(length)
			r.pos += int(length)
		case tagOk:
			totalBytes += 2 // "OK"
		case tagNull:
			// zero bytes
		default:
			return nil, fmt.Errorf("expected string in array, got tag %d", t)
		}
	}

	// Single allocation for all string data
	bulk := make([]byte, 0, totalBytes)
	result := make([]string, count)

	// Second pass: copy strings into the bulk buffer
	r.pos = savedPos
	for i := uint32(0); i < count; i++ {
		t, _ := r.readByte()
		switch t {
		case tagString:
			length, _ := r.readU32()
			start := len(bulk)
			bulk = append(bulk, r.data[r.pos:r.pos+int(length)]...)
			r.pos += int(length)
			result[i] = unsafe.String(&bulk[start], int(length))
		case tagOk:
			start := len(bulk)
			bulk = append(bulk, 'O', 'K')
			result[i] = unsafe.String(&bulk[start], 2)
		case tagNull:
			result[i] = ""
		}
	}
	return result, nil
}
