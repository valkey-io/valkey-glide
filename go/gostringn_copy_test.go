// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"
	"testing"
	"unsafe"
)

func TestGoStringNMatchesGoBytesString(t *testing.T) {
	cases := [][]byte{
		{},
		[]byte("hello"),
		{0},
		{0, 1, 2, 0, 3},
		append([]byte("prefix"), append(make([]byte, 64), []byte("suffix")...)...),
	}
	large := make([]byte, 1<<20) // 1 MiB
	for i := range large {
		large[i] = byte(i % 251)
	}
	cases = append(cases, large)

	for i, want := range cases {
		withCBytes(want, func(p unsafe.Pointer, n int) {
			gotN := convertViaGoStringN(p, n)
			gotB := convertViaGoBytesString(p, n)
			if gotN != gotB {
				t.Fatalf("case %d: GoStringN != GoBytes+string", i)
			}
			if len(gotN) != len(want) {
				t.Fatalf("case %d: len=%d want %d", i, len(gotN), len(want))
			}
			for j := range want {
				if gotN[j] != want[j] {
					t.Fatalf("case %d byte %d: got %d want %d", i, j, gotN[j], want[j])
				}
			}
		})
	}
}

var stringFromCSizes = []int{64, 1024, 65536, 1 << 20}

func BenchmarkStringFromC_GoStringN(b *testing.B) {
	for _, size := range stringFromCSizes {
		b.Run(fmt.Sprintf("%dB", size), func(b *testing.B) {
			payload := make([]byte, size)
			withCBytes(payload, func(p unsafe.Pointer, n int) {
				b.SetBytes(int64(n))
				b.ReportAllocs()
				b.ResetTimer()
				for i := 0; i < b.N; i++ {
					s := convertViaGoStringN(p, n)
					if len(s) != n {
						b.Fatal(len(s))
					}
				}
			})
		})
	}
}

func BenchmarkStringFromC_GoBytesThenString(b *testing.B) {
	for _, size := range stringFromCSizes {
		b.Run(fmt.Sprintf("%dB", size), func(b *testing.B) {
			payload := make([]byte, size)
			withCBytes(payload, func(p unsafe.Pointer, n int) {
				b.SetBytes(int64(n))
				b.ReportAllocs()
				b.ResetTimer()
				for i := 0; i < b.N; i++ {
					s := convertViaGoBytesString(p, n)
					if len(s) != n {
						b.Fatal(len(s))
					}
				}
			})
		})
	}
}
