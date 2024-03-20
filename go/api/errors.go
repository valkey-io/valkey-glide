// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

// RequestError is a Redis client error that occurs when an error is reported during a request.
type RequestError struct {
	msg string
}

func (e *RequestError) Error() string { return e.msg }

// ExecAbortError is a Redis client error that occurs when a transaction is aborted.
type ExecAbortError struct {
	msg string
}

func (e *ExecAbortError) Error() string { return e.msg }

// TimeoutError is a Redis client error that occurs when a request times out.
type TimeoutError struct {
	msg string
}

func (e *TimeoutError) Error() string { return e.msg }

// DisconnectError is a Redis client error that indicates a connection problem between Glide and Redis.
type DisconnectError struct {
	msg string
}

func (e *DisconnectError) Error() string { return e.msg }

func goError(cErrorType C.RequestErrorType, cErrorMessage *C.char) error {
	msg := C.GoString(cErrorMessage)
	switch cErrorType {
	case C.ExecAbort:
		return &ExecAbortError{msg}
	case C.Timeout:
		return &TimeoutError{msg}
	case C.Disconnect:
		return &DisconnectError{msg}
	default:
		return &RequestError{msg}
	}
}
