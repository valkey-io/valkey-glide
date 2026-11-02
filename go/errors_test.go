// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"errors"
	"testing"
)

func TestRestoreNoScriptMessage(t *testing.T) {
	cases := map[string]struct {
		in   string
		want string
	}{
		"valkey detail": {
			in:   "An error was signalled by the server: - NoScriptError: No matching script.",
			want: "NOSCRIPT No matching script.",
		},
		"redis detail": {
			in:   "An error was signalled by the server: - NoScriptError: No matching script. Please use EVAL.",
			want: "NOSCRIPT No matching script. Please use EVAL.",
		},
		"detail containing separators": {
			in:   "An error was signalled by the server: - NoScriptError: a: b - c",
			want: "NOSCRIPT a: b - c",
		},
		"empty detail": {
			in:   "An error was signalled by the server: - NoScriptError: ",
			want: "NOSCRIPT ",
		},
		"code without detail": {
			in:   "An error was signalled by the server:- NoScriptError",
			want: "NOSCRIPT",
		},
		"other server error": {
			in:   "An error was signalled by the server: - ResponseError: unknown command 'FOO'",
			want: "An error was signalled by the server: - ResponseError: unknown command 'FOO'",
		},
		"kind with a longer name": {
			in:   "An error was signalled by the server: - NoScriptErrorX: x",
			want: "An error was signalled by the server: - NoScriptErrorX: x",
		},
		"code alone with trailing text": {
			in:   "An error was signalled by the server:- NoScriptError extra",
			want: "An error was signalled by the server:- NoScriptError extra",
		},
		"not at the start": {
			in:   "scope command error: An error was signalled by the server: - NoScriptError: No matching script.",
			want: "scope command error: An error was signalled by the server: - NoScriptError: No matching script.",
		},
		"extension error": {
			in:   "WRONGTYPE: Operation against a key holding the wrong kind of value",
			want: "WRONGTYPE: Operation against a key holding the wrong kind of value",
		},
		"already server format": {
			in:   "NOSCRIPT No matching script.",
			want: "NOSCRIPT No matching script.",
		},
		"empty": {
			in:   "",
			want: "",
		},
	}

	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			if got := restoreNoScriptMessage(tc.in); got != tc.want {
				t.Errorf("restoreNoScriptMessage(%q) = %q, want %q", tc.in, got, tc.want)
			}
		})
	}
}

// Values mirror the RequestErrorType enum in lib.h; cgo cannot be used from _test.go files.
func TestGoErrorRestoresNoScriptMessage(t *testing.T) {
	const (
		unspecifiedErrorType = 0
		execAbortErrorType   = 1
	)
	const coreMessage = "An error was signalled by the server: - NoScriptError: No matching script."

	err := GoError(unspecifiedErrorType, coreMessage)
	if err.Error() != "NOSCRIPT No matching script." {
		t.Errorf("GoError message = %q, want %q", err.Error(), "NOSCRIPT No matching script.")
	}

	err = GoError(execAbortErrorType, coreMessage)
	var execAbortErr *ExecAbortError
	if !errors.As(err, &execAbortErr) {
		t.Fatalf("GoError type = %T, want *ExecAbortError", err)
	}
	if err.Error() != "NOSCRIPT No matching script." {
		t.Errorf("GoError message = %q, want %q", err.Error(), "NOSCRIPT No matching script.")
	}

	const otherMessage = "An error was signalled by the server: - ResponseError: unknown command 'FOO'"
	if err = GoError(unspecifiedErrorType, otherMessage); err.Error() != otherMessage {
		t.Errorf("GoError message = %q, want %q", err.Error(), otherMessage)
	}
}
