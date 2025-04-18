// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package errors

// #include "../../lib.h"
import "C"

// ConnectionError is a client error that occurs when there is an error while connecting or when a connection
// disconnects.
type ConnectionError struct {
	Msg string
}

func (e *ConnectionError) Error() string { return e.Msg }

// RequestError is a client error that occurs when an error is reported during a request.
type RequestError struct {
	Msg string
}

func (e *RequestError) Error() string { return e.Msg }

// ExecAbortError is a client error that occurs when a transaction is aborted.
type ExecAbortError struct {
	msg string
}

func (e *ExecAbortError) Error() string { return e.msg }

// TimeoutError is a client error that occurs when a request times out.
type TimeoutError struct {
	msg string
}

func (e *TimeoutError) Error() string { return e.msg }

// DisconnectError is a client error that indicates a connection problem between Glide and server.
type DisconnectError struct {
	msg string
}

func (e *DisconnectError) Error() string { return e.msg }

// ClosingError is a client error that indicates that the client has closed and is no longer usable.
type ClosingError struct {
	Msg string
}

func (e *ClosingError) Error() string { return e.Msg }

// ConfigurationError is a client error that occurs when there is an issue with client configuration.
type ConfigurationError struct {
	Msg string
}

func (e *ConfigurationError) Error() string { return e.Msg }

// GoError converts a C error type to a corresponding Go error.
func GoError(cErrorType uint32, errorMessage string) error {
	switch cErrorType {
	case C.ExecAbort:
		return &ExecAbortError{errorMessage}
	case C.Timeout:
		return &TimeoutError{errorMessage}
	case C.Disconnect:
		return &DisconnectError{errorMessage}
	default:
		return &RequestError{errorMessage}
	}
}
