// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import "testing"

// An empty hash tag ("{}") must not consume a later '}'. The first '}' after the
// first '{' closes the tag; an empty tag hashes the whole key. Slot values are the
// server's CLUSTER KEYSLOT results.
func TestSlotForKey_emptyHashTag(t *testing.T) {
	cases := []struct {
		key  string
		slot uint16
	}{
		{"{}user}:1", 15441},
		{"{}{a}", 13650},
		// Non-empty tag and no tag are unaffected.
		{"foo{bar}baz", 5061},
		{"{user1}", 8106},
		// First '}' closes the tag ("{bar"); a '{' with no closing '}' hashes the whole key.
		{"foo{{bar}}zap", 4015},
		{"{", 4092},
	}
	for _, c := range cases {
		if got := slotForKey([]byte(c.key)); got != c.slot {
			t.Errorf("slotForKey(%q) = %d, want %d", c.key, got, c.slot)
		}
	}
}
