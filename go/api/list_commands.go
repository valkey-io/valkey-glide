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

	// Returns the specified elements of the list stored at key.
	// The offsets start and end are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being the last element of the list, <code>-2</code> being the penultimate, and so on.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the list.
	//  start - The starting point of the range.
	//  end   - The end of the range.
	//
	// Return value:
	//  Array of elements as Result[string] in the specified range.
	//  If start exceeds the end of the list, or if start is greater than end, an empty array will be returned.
	//  If end exceeds the actual end of the list, the range will stop at the actual end of the list.
	//  If key does not exist an empty array will be returned.
	//
	// For example:
	//  1. result, err := client.LRange("my_list", 0, 2)
	//     result: []api.Result[string]{api.CreateStringResult("value1"), api.CreateStringResult("value2"), api.CreateStringResult("value3")}
	//  2. result, err := client.LRange("my_list", -2, -1)
	//     result: []api.Result[string]{api.CreateStringResult("value2"), api.CreateStringResult("value3")}
	//  3. result, err := client.LRange("non_existent_key", 0, 2)
	//     result: []api.Result[string]{}
	//
	// [valkey.io]: https://valkey.io/commands/lrange/
	LRange(key string, start int64, end int64) ([]Result[string], error)

	// Returns the element at index from the list stored at key.
	// The index is zero-based, so 0 means the first element, 1 the second element and so on. Negative indices can be used to designate elements starting at the tail of the list. Here, -1 means the last element, -2 means the penultimate and so forth.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the list.
	//  index - The index of the element in the list to retrieve.
	//
	// Return value:
	//  The Result[string] containing element at index in the list stored at key.
	//  If index is out of range or if key does not exist, [api.CreateNilStringResult()] is returned.
	//
	// For example:
	//  1. result, err := client.LIndex("myList", 0)
	//     result.Value(): "value1" // Returns the first element in the list stored at 'myList'.
	//     result.IsNil(): false
	//  2. result, err := client.LIndex("myList", -1)
	//     result.Value(): "value3" // Returns the last element in the list stored at 'myList'.
	//     result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/lindex/
	LIndex(key string, index int64) (Result[string], error)

	// Trims an existing list so that it will contain only the specified range of elements specified.
	// The offsets start and end are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
	// These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being the last element of the list, -2 being the penultimate, and so on.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the list.
	//  start - The starting point of the range.
	//  end   - The end of the range.
	//
	// Return value:
	//  The Result[string] containing always "OK".
	//  If start exceeds the end of the list, or if start is greater than end, the result will be an empty list (which causes key to be removed).
	//  If end exceeds the actual end of the list, it will be treated like the last element of the list.
	//  If key does not exist, OK will be returned without changes to the database.
	//
	// For example:
	//  result, err := client.LTrim("my_list", 0, 1)
	//  result.Value(): "OK"
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/ltrim/
	LTrim(key string, start int64, end int64) (Result[string], error)

	// Returns the length of the list stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key of the list.
	//
	// Return value:
	//  The Result[int64] containing the length of the list at key.
	//  If key does not exist, it is interpreted as an empty list and 0 is returned.
	//
	// For example:
	//  result, err := client.LLen("my_list")
	//  result.Value(): int64(3) // Indicates that there are 3 elements in the list.
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/llen/
	LLen(key string) (Result[int64], error)

	// Removes the first count occurrences of elements equal to element from the list stored at key.
	// If count is positive: Removes elements equal to element moving from head to tail.
	// If count is negative: Removes elements equal to element moving from tail to head.
	// If count is 0 or count is greater than the occurrences of elements equal to element, it removes all elements equal to element.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key     - The key of the list.
	//  count   - The count of the occurrences of elements equal to element to remove.
	//  element - The element to remove from the list.
	//
	// Return value:
	//  The Result[int64] containing the number of the removed elements.
	//  If key does not exist, 0 is returned.
	//
	// For example:
	//  result, err := client.LRem("my_list", 2, "value")
	//  result.Value(): int64(2)
	//  result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/lrem/
	LRem(key string, count int64, element string) (Result[int64], error)

	// Removes and returns the last elements of the list stored at key.
	// The command pops a single element from the end of the list.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key of the list.
	//
	// Return value:
	//  The Result[string] containing the value of the last element.
	//  If key does not exist, [api.CreateNilStringResult()] will be returned.
	//
	// For example:
	//  1. result, err := client.RPop("my_list")
	//     result.Value(): "value1"
	//     result.IsNil(): false
	//  2. result, err := client.RPop("non_exiting_key")
	//     result.Value(): ""
	//     result.IsNil(): true
	//
	// [valkey.io]: https://valkey.io/commands/rpop/
	RPop(key string) (Result[string], error)

	// Removes and returns up to count elements from the list stored at key, depending on the list's length.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the list.
	//  count - The count of the elements to pop from the list.
	//
	// Return value:
	//  An array of popped elements as Result[string] will be returned depending on the list's length.
	//  If key does not exist, nil will be returned.
	//
	// For example:
	//  1. result, err := client.RPopCount("my_list", 2)
	//     result: []api.Result[string]{api.CreateStringResult("value1"), api.CreateStringResult("value2")}
	//  2. result, err := client.RPop("non_exiting_key")
	//     result: nil
	//
	// [valkey.io]: https://valkey.io/commands/rpop/
	RPopCount(key string, count int64) ([]Result[string], error)

	// Inserts element in the list at key either before or after the pivot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key      - The key of the list.
	//  position - The relative position to insert into - either {@link InsertPosition#BEFORE} or {@link InsertPosition#AFTER} the pivot.
	//  pivot    - An element of the list.
	//  element  - The new element to insert.
	//
	// Return value:
	//  The Result[int64] containing the list length after a successful insert operation.
	//  If the key doesn't exist returns -1.
	//  If the pivot wasn't found, returns 0.
	//
	// For example:
	//  result, err := client.LInsert("my_list", BEFORE, "World", "There")
	//  result.Value() > 0L;
	//
	// [valkey.io]: https://valkey.io/commands/rpop/
	LInsert(key string, position *InsertPosition, pivot string, element string) (Result[int64], error)
}
