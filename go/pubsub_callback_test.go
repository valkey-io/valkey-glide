// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"bytes"
	"log"
	"strings"
	"sync"
	"testing"
	"time"
	"unsafe"
)

func TestHandlePubSubCallbackCopiesBorrowedPayload(t *testing.T) {
	const callbackID = uintptr(0xA11CE)
	client := &baseClient{
		pending:        make(map[uintptr]struct{}),
		mu:             &sync.Mutex{},
		messageHandler: NewMessageHandler(nil, nil),
	}
	registerClient(client, callbackID)
	t.Cleanup(func() { unregisterClient(callbackID) })

	message := []byte("message")
	channel := []byte("channel")
	pattern := []byte("pattern")
	if !handlePubSubCallback(
		callbackID,
		unsafe.Pointer(&message[0]),
		int64(len(message)),
		unsafe.Pointer(&channel[0]),
		int64(len(channel)),
		unsafe.Pointer(&pattern[0]),
		int64(len(pattern)),
	) {
		t.Fatal("valid callback payload was rejected")
	}

	select {
	case received := <-client.messageHandler.GetQueue().WaitForMessage():
		if received.Message != "message" || received.Channel != "channel" {
			t.Fatalf("unexpected PubSub message: %#v", received)
		}
		if received.Pattern.IsNil() || received.Pattern.Value() != "pattern" {
			t.Fatalf("unexpected PubSub pattern: %#v", received.Pattern)
		}
	case <-time.After(time.Second):
		t.Fatal("valid PubSub callback was not delivered")
	}
}

func TestHandlePubSubCallbackRejectsInvalidLengthsWithoutCopying(t *testing.T) {
	const secret = "SECRET_PUBSUB_PAYLOAD"
	payload := []byte(secret)
	payloadPointer := unsafe.Pointer(&payload[0])
	validLength := int64(len(payload))
	overGoStringNCapacity := maxGoStringNLength + 1

	tests := map[string][3]int64{
		"negative message":  {-1, validLength, 0},
		"negative channel":  {validLength, -1, 0},
		"negative pattern":  {validLength, validLength, -1},
		"oversized message": {overGoStringNCapacity, validLength, 0},
		"oversized channel": {validLength, overGoStringNCapacity, 0},
		"oversized pattern": {validLength, validLength, overGoStringNCapacity},
	}

	for name, lengths := range tests {
		t.Run(name, func(t *testing.T) {
			var output bytes.Buffer
			originalOutput := log.Writer()
			originalFlags := log.Flags()
			log.SetOutput(&output)
			log.SetFlags(0)
			t.Cleanup(func() {
				log.SetOutput(originalOutput)
				log.SetFlags(originalFlags)
			})

			accepted := handlePubSubCallback(
				1,
				payloadPointer,
				lengths[0],
				payloadPointer,
				lengths[1],
				payloadPointer,
				lengths[2],
			)
			if accepted {
				t.Fatal("invalid callback length was accepted")
			}
			if strings.Contains(output.String(), secret) {
				t.Fatalf("PubSub payload was logged: %q", output.String())
			}
			if output.String() != "invalid PubSub callback payload\n" {
				t.Fatalf("unexpected invalid-payload log: %q", output.String())
			}
		})
	}
}
