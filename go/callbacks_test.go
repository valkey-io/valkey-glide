// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// Test files cannot use cgo, so these tests pass the Go types the cgo length and
// pointer parameters alias: int64 for int64_t and unsafe.Pointer for uint8_t *.

import (
	"bytes"
	"log"
	"math"
	"reflect"
	"strings"
	"testing"
	"time"
	"unsafe"
)

// The FFI layer declares the pub/sub callback length parameters as int64_t. Assert
// the Go side agrees, so narrowing them back to a 32-bit type cannot slip through.
func TestPubSubCallbackLengthParamsAre64Bit(t *testing.T) {
	callbackType := reflect.TypeOf(pubSubCallback)
	for name, index := range map[string]int{"message_len": 3, "channel_len": 5, "pattern_len": 7} {
		if size := callbackType.In(index).Size(); size != 8 {
			t.Errorf("%s is %d bytes wide, want 8", name, size)
		}
	}
}

func TestPubSubBytes(t *testing.T) {
	payload := []byte("hello")

	tests := []struct {
		name   string
		length int64
		want   string
		wantOk bool
	}{
		{name: "in range", length: 5, want: "hello", wantOk: true},
		{name: "zero", length: 0, want: "", wantOk: true},
		{name: "negative", length: -1, wantOk: false},
		{name: "above 32-bit maximum", length: math.MaxInt32 + 1, wantOk: false},
		// The length that motivated issue 6816: its low 32 bits are a small
		// positive number, so narrowing without a check yields a short read.
		{name: "narrows to a valid length", length: 1<<32 + 5, wantOk: false},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			got, ok := pubSubBytes("message", unsafe.Pointer(&payload[0]), test.length)
			if ok != test.wantOk {
				t.Fatalf("ok = %v, want %v", ok, test.wantOk)
			}
			if ok && string(got) != test.want {
				t.Errorf("got %q, want %q", got, test.want)
			}
			if !ok && got != nil {
				t.Errorf("got %q, want no bytes", got)
			}
		})
	}
}

func TestPubSubCallbackDropsOutOfRangeLength(t *testing.T) {
	handler := NewMessageHandler(nil, nil)
	client := &baseClient{}
	client.setMessageHandler(handler)

	// Stands in for the pointer the FFI layer passes back to identify the client.
	token := []byte{0}
	clientPtr := unsafe.Pointer(&token[0])
	registerClient(client, uintptr(clientPtr))
	defer unregisterClient(uintptr(clientPtr))

	payload := []byte("hello")
	payloadPtr := unsafe.Pointer(&payload[0])

	var logged bytes.Buffer
	previousOutput := log.Writer()
	log.SetOutput(&logged)
	defer log.SetOutput(previousOutput)

	// A length whose low 32 bits are 5: truncating it would deliver a 5 byte
	// message in place of a 4 GiB one.
	pubSubCallback(clientPtr, 0, payloadPtr, 1<<32+5, payloadPtr, 5, nil, 0)

	if message := handler.GetQueue().Pop(); message != nil {
		t.Fatalf("delivered %+v, want the message dropped", message)
	}
	if !strings.Contains(logged.String(), "Dropping pub/sub message") {
		t.Errorf("log output %q does not report the drop", logged.String())
	}

	// A message with in range lengths still arrives intact.
	pubSubCallback(clientPtr, 0, payloadPtr, 5, payloadPtr, 5, nil, 0)

	select {
	case message := <-handler.GetQueue().WaitForMessage():
		if message.Message != "hello" || message.Channel != "hello" {
			t.Errorf("got message %q on channel %q, want %q on %q",
				message.Message, message.Channel, "hello", "hello")
		}
	case <-time.After(5 * time.Second):
		t.Error("timed out waiting for the message")
	}
}
