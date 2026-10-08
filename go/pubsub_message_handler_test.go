// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"bytes"
	"log"
	"strings"
	"testing"

	"github.com/valkey-io/valkey-glide/go/v2/models"
)

func TestMessageHandlerRecoversPanicWithoutLoggingPayload(t *testing.T) {
	const secret = "SECRET_PUBSUB_CALLBACK_TOKEN"
	var output bytes.Buffer
	originalOutput := log.Writer()
	originalFlags := log.Flags()
	originalPrefix := log.Prefix()
	log.SetOutput(&output)
	log.SetFlags(0)
	log.SetPrefix("")
	t.Cleanup(func() {
		log.SetOutput(originalOutput)
		log.SetFlags(originalFlags)
		log.SetPrefix(originalPrefix)
	})

	handler := NewMessageHandler(
		func(_ *models.PubSubMessage, _ any) { panic(secret) },
		nil,
	)

	if err := handler.handleMessage(nil); err != nil {
		t.Fatalf("handleMessage returned an error after callback panic: %v", err)
	}
	if strings.Contains(output.String(), secret) {
		t.Fatalf("callback panic secret was logged: %q", output.String())
	}
	if output.String() != "panic in message callback\n" {
		t.Fatalf("unexpected callback panic log: %q", output.String())
	}
}
