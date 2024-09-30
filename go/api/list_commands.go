// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "List Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=list
type ListCommands interface {
	// Inserts all the specified values at the head of the list stored at key. elements are inserted one after the other to the
	// head of the list, from the leftmost element to the rightmost element. If key does not exist, it is created as an empty
	// list before performing the push operation.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the list.
	//  elements - The elements to insert at the head of the list stored at key.
	//
	// Return value:
	//  A api.Result[int64] containing the length of the list after the push operation.
	//
	// For example:
	//  result, err := client.LPush("my_list", []string{"value1", "value2"})
	//  result.Value(): 2
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/lpush/
	LPush(key string, elements []string) (Result[int64], error)

	// Removes and returns the first elements of the list stored at key. The command pops a single element from the beginning
	// of the list.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key of the list.
	//
	// Return value:
	//  The Result[string] containing the value of the first element.
	//  If key does not exist, [api.CreateNilStringResult()] will be returned.
	//
	// For example:
	//  1. result, err := client.LPush("my_list", []string{"value1", "value2"})
	//     value, err := client.LPop("my_list")
	//     value.Value(): "value2"
	//     result.IsNil(): false
	//  2. result, err := client.LPop("non_existent")
	//     result.Value(): ""
	//     result.IsNil(); true
	//
	// [valkey.io]: https://valkey.io/commands/lpop/
	LPop(key string) (Result[string], error)

	// Removes and returns up to count elements of the list stored at key, depending on the list's length.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the list.
	//  count - The count of the elements to pop from the list.
	//
	// Return value:
	//  An array of the popped elements as Result[string] will be returned depending on the list's length
	//  If key does not exist, nil will be returned.
	//
	// For example:
	//  1. result, err := client.LPopCount("my_list", 2)
	//     result: []api.Result[string]{api.CreateStringResult("value1"), api.CreateStringResult("value2")}
	//  2. result, err := client.LPopCount("non_existent")
	//     result: nil
	//
	// [valkey.io]: https://valkey.io/commands/lpop/
	LPopCount(key string, count int64) ([]Result[string], error)

	// Returns the index of the first occurrence of element inside the list specified by key. If no match is found,
	// [api.CreateNilInt64Result()] is returned.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key     - The name of the list.
	//  element - The value to search for within the list.
	//
	// Return value:
	// The Result[int64] containing the index of the first occurrence of element, or [api.CreateNilInt64Result()] if element is
	// not in the list.
	//
	// For example:
	//  result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e"})
	//  position, err := client.LPos("my_list", "e")
	//  position.Value(): 4
	//  position.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/lpos/
	LPos(key string, element string) (Result[int64], error)

	// Returns the index of an occurrence of element within a list based on the given options. If no match is found,
	// [api.CreateNilInt64Result()] is returned.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key     - The name of the list.
	//  element - The value to search for within the list.
	//  options - The LPos options.
	//
	// Return value:
	//  The Result[int64] containing the index of element, or [api.CreateNilInt64Result()] if element is not in the list.
	//
	// For example:
	//  1. result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e"})
	//     result, err := client.LPosWithOptions("my_list", "e", &api.LPosOptions{
	//         IsRankSet: true,
	//         Rank:      int64(2),
	//     })
	//     result.Value(): 5 (Returns the second occurrence of the element "e")
	//  2. result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e"})
	//     result, err := client.LPosWithOptions("my_list", "e", &api.LPosOptions{
	//         IsRankSet:   true,
	//         Rank:        int64(1),
	//         IsMaxLenSet: true,
	//         MaxLen:      int64(1000),
	//     })
	//     result.Value(): 4
	//
	// [valkey.io]: https://valkey.io/commands/lpos/
	LPosWithOptions(key string, element string, options *LPosOptions) (Result[int64], error)

	// Returns an array of indices of matching elements within a list.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key     - The name of the list.
	//  element - The value to search for within the list.
	//  count   - The number of matches wanted.
	//
	// Return value:
	//  An array that holds the indices of the matching elements within the list.
	//
	// For example:
	//  result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	//  result, err := client.LPosCount("my_list", "e", int64(3))
	//  result: []api.Result[int64]{api.CreateInt64Result(4), api.CreateInt64Result(5), api.CreateInt64Result(6)}
	//
	//
	// [valkey.io]: https://valkey.io/commands/lpos/
	LPosCount(key string, element string, count int64) ([]Result[int64], error)

	// Returns an array of indices of matching elements within a list based on the given options. If no match is found, an
	// empty array is returned.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key     - The name of the list.
	//  element - The value to search for within the list.
	//  count   - The number of matches wanted.
	//  options - The LPos options.
	//
	// Return value:
	//  An array that holds the indices of the matching elements within the list.
	//
	// For example:
	//  1. result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	//     result, err := client.LPosWithOptions("my_list", "e", int64(1), &api.LPosOptions{
	//         IsRankSet: true,
	//         Rank: 	  int64(2),
	//     })
	//     result: []api.Result[int64]{api.CreateInt64Result(5)}
	//  2. result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	//     result, err := client.LPosWithOptions("my_list", "e", int64(3), &api.LPosOptions{
	//         IsRankSet:   true,
	//         Rank:        int64(2),
	//         IsMaxLenSet: true,
	//         MaxLen:      int64(1000),
	//     })
	//     result: []api.Result[int64]{api.CreateInt64Result(5), api.CreateInt64Result(6)}
	//
	//
	// [valkey.io]: https://valkey.io/commands/lpos/
	LPosCountWithOptions(key string, element string, count int64, options *LPosOptions) ([]Result[int64], error)

	// Inserts all the specified values at the tail of the list stored at key.
	// elements are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
	// If key does not exist, it is created as an empty list before performing the push operation.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the list.
	//  elements - The elements to insert at the tail of the list stored at key.
	//
	// Return value:
	//  The Result[int64] containing the length of the list after the push operation.
	//
	// For example:
	//  result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	//  result.Value(): 7
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/rpush/
	RPush(key string, elements []string) (Result[int64], error)
}
