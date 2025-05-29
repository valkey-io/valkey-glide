// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideErrors

// #include "../lib.h"
import "C"

// ConnectionError is a client error that occurs when there is an error while connecting or when a connection
// disconnects.
type ConnectionError struct {
	msg string
}

func NewConnectionError(message string) *ConnectionError {
	return &ConnectionError{msg: message}
}

func (e *ConnectionError) Error() string { return e.msg }

// RequestError is a client error that occurs when an error is reported during a request.
type RequestError struct {
	msg string
}

func NewRequestError(message string) *RequestError {
	return &RequestError{msg: message}
}

func (e *RequestError) Error() string { return e.msg }

// ExecAbortError is a client error that occurs when a transaction is aborted.
type ExecAbortError struct {
	msg string
}

func NewExecAbortError(message string) *ExecAbortError {
	return &ExecAbortError{msg: message}
}

func (e *ExecAbortError) Error() string { return e.msg }

// TimeoutError is a client error that occurs when a request times out.
type TimeoutError struct {
	msg string
}

func NewTimeoutError(message string) *TimeoutError {
	return &TimeoutError{msg: message}
}

func (e *TimeoutError) Error() string { return e.msg }

// DisconnectError is a client error that indicates a connection problem between Glide and server.
type DisconnectError struct {
	msg string
}

func NewDisconnectError(message string) *DisconnectError {
	return &DisconnectError{msg: message}
}

func (e *DisconnectError) Error() string { return e.msg }

// ClosingError is a client error that indicates that the client has closed and is no longer usable.
type ClosingError struct {
	msg string
}

func NewClosingError(message string) *ClosingError {
	return &ClosingError{msg: message}
}

func (e *ClosingError) Error() string { return e.msg }

// ConfigurationError is a client error that occurs when there is an issue with client configuration.
type ConfigurationError struct {
	msg string
}

func NewConfigurationError(message string) *ConfigurationError {
	return &ConfigurationError{msg: message}
}

func (e *ConfigurationError) Error() string { return e.msg }

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
