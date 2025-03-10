// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestPushKind(t *testing.T) {

	var kind PushKind = Message

	assert.Equal(t, "Message", kind.String())
}
