// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// SetCommands supports commands and transactions for the "Set Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=set
type SetCommands interface {
	// SAdd adds specified members to the set stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key     - The key where members will be added to its set.
	//  members - A list of members to add to the set stored at key.
	//
	// Return value:
	//  The Result[int64] containing number of members that were added to the set,
	//  or [api.NilResult[int64]](api.CreateNilInt64Result()) when the key does not exist.
	//
	// For example:
	//  result, err := client.SAdd("my_set", []string{"member1", "member2"})
	//  // result.Value(): 2
	//  // result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/sadd/
	SAdd(key string, members []string) (Result[int64], error)

	// SRem removes specified members from the set stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key     - The key from which members will be removed.
	//  members - A list of members to remove from the set stored at key.
	//
	// Return value:
	//  The Result[int64] containing number of members that were removed from the set, excluding non-existing members.
	//  Returns [api.NilResult[int64]](api.CreateNilInt64Result()) if key does not exist.
	//
	// For example:
	//  result, err := client.SRem("my_set", []string{"member1", "member2"})
	//  // result.Value(): 2
	//  // result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/srem/
	SRem(key string, members []string) (Result[int64], error)

	// SMembers retrieves all the members of the set value stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key - The key from which to retrieve the set members.
	//
	// Return value:
	//   A map[Result[string]]struct{} containing all members of the set.
	//   Returns an empty map if key does not exist.
	//
	// For example:
	//   // Assume set "my_set" contains: "member1", "member2"
	//   result, err := client.SMembers("my_set")
	//   // result equals:
	//   // map[Result[string]]struct{}{
	//   //   api.CreateStringResult("member1"): {},
	//   //   api.CreateStringResult("member2"): {}
	//   // }
	//
	// [valkey.io]: https://valkey.io/commands/smembers/
	SMembers(key string) (map[Result[string]]struct{}, error)

	// SCard retrieves the set cardinality (number of elements) of the set stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key - The key from which to retrieve the number of set members.
	//
	// Return value:
	//   The Result[int64] containing the cardinality (number of elements) of the set,
	//   or 0 if the key does not exist.
	//
	// Example:
	//   result, err := client.SCard("my_set")
	//   // result.Value(): 3
	//   // result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/scard/
	SCard(key string) (Result[int64], error)

	// SIsMember returns if member is a member of the set stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key    - The key of the set.
	//   member - The member to check for existence in the set.
	//
	// Return value:
	//   A Result[bool] containing true if the member exists in the set, false otherwise.
	//   If key doesn't exist, it is treated as an empty set and the method returns false.
	//
	// Example:
	//   result1, err := client.SIsMember("mySet", "member1")
	//   // result1.Value(): true
	//   // Indicates that "member1" exists in the set "mySet".
	//   result2, err := client.SIsMember("mySet", "nonExistingMember")
	//   // result2.Value(): false
	//   // Indicates that "nonExistingMember" does not exist in the set "mySet".
	//
	// [valkey.io]: https://valkey.io/commands/sismember/
	SIsMember(key string, member string) (Result[bool], error)

	// SDiff computes the difference between the first set and all the successive sets in keys.
	//
	// Note: When in cluster mode, all keys must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   keys - The keys of the sets to diff.
	//
	// Return value:
	//   A map[Result[string]]struct{} representing the difference between the sets.
	//   If a key does not exist, it is treated as an empty set.
	//
	// Example:
	//   result, err := client.SDiff([]string{"set1", "set2"})
	//   // result might contain:
	//   // map[Result[string]]struct{}{
	//   //   api.CreateStringResult("element"): {},
	//   // }
	//   // Indicates that "element" is present in "set1", but missing in "set2"
	//
	// [valkey.io]: https://valkey.io/commands/sdiff/
	SDiff(keys []string) (map[Result[string]]struct{}, error)

	// SDiffStore stores the difference between the first set and all the successive sets in keys
	// into a new set at destination.
	//
	// Note: When in cluster mode, destination and all keys must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   destination - The key of the destination set.
	//   keys        - The keys of the sets to diff.
	//
	// Return value:
	//   A Result[int64] containing the number of elements in the resulting set.
	//
	// Example:
	//   result, err := client.SDiffStore("mySet", []string{"set1", "set2"})
	//   // result.Value(): 5
	//   // Indicates that the resulting set "mySet" contains 5 elements
	//
	// [valkey.io]: https://valkey.io/commands/sdiffstore/
	SDiffStore(destination string, keys []string) (Result[int64], error)

	// SInter gets the intersection of all the given sets.
	//
	// Note: When in cluster mode, all keys must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   keys - The keys of the sets to intersect.
	//
	// Return value:
	//   A map[Result[string]]struct{} containing members which are present in all given sets.
	//   If one or more sets do not exist, an empty map will be returned.
	//
	//
	// Example:
	//   result, err := client.SInter([]string{"set1", "set2"})
	//   // result might contain:
	//   // map[Result[string]]struct{}{
	//   //   api.CreateStringResult("element"): {},
	//   // }
	//   // Indicates that "element" is present in both "set1" and "set2"
	//
	// [valkey.io]: https://valkey.io/commands/sinter/
	SInter(keys []string) (map[Result[string]]struct{}, error)

	// SInterCard gets the cardinality of the intersection of all the given sets.
	//
	// Since:
	//  Valkey 7.0 and above.
	//
	// Note: When in cluster mode, all keys must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   keys - The keys of the sets to intersect.
	//
	// Return value:
	//   A Result[int64] containing the cardinality of the intersection result.
	//   If one or more sets do not exist, 0 is returned.
	//
	// Example:
	//   result, err := client.SInterCard([]string{"set1", "set2"})
	//   // result.Value(): 2
	//   // Indicates that the intersection of "set1" and "set2" contains 2 elements
	//   result, err := client.SInterCard([]string{"set1", "nonExistingSet"})
	//   // result.Value(): 0
	//
	// [valkey.io]: https://valkey.io/commands/sintercard/
	SInterCard(keys []string) (Result[int64], error)

	// SInterCardLimit gets the cardinality of the intersection of all the given sets, up to the specified limit.
	//
	// Since:
	//  Valkey 7.0 and above.
	//
	// Note: When in cluster mode, all keys must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   keys  - The keys of the sets to intersect.
	//   limit - The limit for the intersection cardinality value.
	//
	// Return value:
	//   A Result[int64] containing the cardinality of the intersection result, or the limit if reached.
	//   If one or more sets do not exist, 0 is returned.
	//   If the intersection cardinality reaches 'limit' partway through the computation, returns 'limit' as the cardinality.
	//
	// Example:
	//   result, err := client.SInterCardLimit([]string{"set1", "set2"}, 3)
	//   // result.Value(): 2
	//   // Indicates that the intersection of "set1" and "set2" contains 2 elements (or at least 3 if the actual
	//   // intersection is larger)
	//
	// [valkey.io]: https://valkey.io/commands/sintercard/
	SInterCardLimit(keys []string, limit int64) (Result[int64], error)

	// SRandMember returns a random element from the set value stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key - The key from which to retrieve the set member.
	//
	// Return value:
	//   A Result[string] containing a random element from the set.
	//   Returns api.CreateNilStringResult() if key does not exist.
	//
	// Example:
	//   client.SAdd("test", []string{"one"})
	//   response, err := client.SRandMember("test")
	//   // response.Value() == "one"
	//   // err == nil
	//
	// [valkey.io]: https://valkey.io/commands/srandmember/
	SRandMember(key string) (Result[string], error)

	// SPop removes and returns one random member from the set stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key - The key of the set.
	//
	// Return value:
	//   A Result[string] containing the value of the popped member.
	//   Returns a NilResult if key does not exist.
	//
	// Example:
	//   value1, err := client.SPop("mySet")
	//   // value1.Value() might be "value1"
	//   // err == nil
	//   value2, err := client.SPop("nonExistingSet")
	//   // value2.IsNil() == true
	//   // err == nil
	//
	// [valkey.io]: https://valkey.io/commands/spop/
	SPop(key string) (Result[string], error)
}
