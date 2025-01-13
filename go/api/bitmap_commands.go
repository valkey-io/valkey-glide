// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "github.com/valkey-io/valkey-glide/go/glide/api/options"

// Supports commands and transactions for the "Bitmap" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#bitmap
type BitmapCommands interface {
	// Sets or clears the bit at offset in the string value stored at key.
	// The offset is a zero-based index, with `0`` being the first element of
	// the list, `1` being the next element, and so on. The offset must be
	// less than `2^32` and greater than or equal to `0` If a key is
	// non-existent then the bit at offset is set to value and the preceding
	// bits are set to 0.
	//
	// Parameters:
	//  key - The key of the string.
	//  offset - The index of the bit to be set.
	//  value - The bit value to set at offset The value must be 0 or 1.
	//
	// Return value:
	//  The bit value that was previously stored at offset.
	//
	// Example:
	//  result, err := client.SetBit("key",1 , 1)
	//  result.Value(): 1
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/setbit/
	SetBit(key string, offset int64, value int64) (Result[int64], error)

	// Returns the bit value at offset in the string value stored at key.
	//  offset should be greater than or equal to zero.
	//
	// Parameters:
	//  key - The key of the string.
	//  offset - The index of the bit to return.
	//
	// Return value:
	// The bit at offset of the string. Returns zero if the key is empty or if the positive
	// offset exceeds the length of the string.
	//
	// Example:
	//  result, err := client.GetBit("key1",1,1)
	//  result.Value(): 1
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/getbit/
	GetBit(key string, offset int64) (Result[int64], error)

	// Counts the number of set bits (population counting) in a string stored at key.
	//
	// Parameters:
	//  key - The key for the string to count the set bits of.
	//
	// Return value:
	// The number of set bits in the string. Returns zero if the key is missing as it is
	// treated as an empty string.
	//
	// Example:
	//  result, err := client.BitCount("mykey")
	//  result.Value(): 26
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/bitcount/
	BitCount(key string) (Result[int64], error)

	// Counts the number of set bits (population counting) in a string stored at key. The
	// offsets start and end are zero-based indexes, with 0 being the first element of the
	// list, 1 being the next element and so on. These offsets can also be negative numbers
	// indicating offsets starting at the end of the list, with -1 being the last element
	// of the list, -2 being the penultimate, and so on.
	//
	// Parameters:
	//  key - The key for the string to count the set bits of.
	//  options - Start is the starting offset and end is the ending offset. BitmapIndexType
	//  is The index offset type. Could be either {@link BitmapIndexType#BIT} or
	//  {@link BitmapIndexType#BYTE}.
	//
	// Return value:
	// The number of set bits in the string interval specified by start, end, and options.
	// Returns zero if the key is missing as it is treated as an empty string.
	//
	// Example:
	//  opts := &options.BitCountOptions{}
	//	opts.SetStart(1)
	//	opts, err := opts.SetEnd(1)
	//	opts, err = opts.SetBitmapIndexType(options.BYTE)
	//  result, err := client.BitCount("mykey",options)
	//  result.Value(): 6
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/bitcount/
	BitCountWithOptions(key string, options *options.BitCountOptions) (Result[int64], error)
}
