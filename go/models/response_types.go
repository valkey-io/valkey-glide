// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package models

// A value to return alongside with error in case if command failed
var (
	DefaultFloatResponse  float64
	DefaultBoolResponse   bool
	DefaultIntResponse    int64
	DefaultStringResponse string
)

type Result[T any] struct {
	val   T
	isNil bool
}

// KeyWithMemberAndScore is used by BZPOPMIN/BZPOPMAX, which return an object consisting of the key of the sorted set that was
// popped, the popped member, and its score.
type KeyWithMemberAndScore struct {
	Key    string
	Member string
	Score  float64
}

// Response of the [ZMPop] and [BZMPop] command.
type KeyWithArrayOfMembersAndScores struct {
	Key              string
	MembersAndScores []MemberAndScore
}

// MemberAndScore is used by ZRANDMEMBER, which return an object consisting of the sorted set member, and its score.
type MemberAndScore struct {
	Member string
	Score  float64
}

// Response type of [XRange] and [XRevRange] commands.
type XRangeResponse struct {
	StreamId string
	Entries  [][]string
}

// Response type of [XAutoClaim] command.
type XAutoClaimResponse struct {
	NextEntry       string
	ClaimedEntries  map[string][][]string
	DeletedMessages []string
}

// Response type of [XAutoClaimJustId] command.
type XAutoClaimJustIdResponse struct {
	NextEntry       string
	ClaimedEntries  []string
	DeletedMessages []string
}

func (result Result[T]) IsNil() bool {
	return result.isNil
}

func (result Result[T]) Value() T {
	return result.val
}

func CreateStringResult(str string) Result[string] {
	return Result[string]{val: str, isNil: false}
}

func CreateNilStringResult() Result[string] {
	return Result[string]{val: "", isNil: true}
}

func CreateInt64Result(intVal int64) Result[int64] {
	return Result[int64]{val: intVal, isNil: false}
}

func CreateNilInt64Result() Result[int64] {
	return Result[int64]{val: 0, isNil: true}
}

func CreateFloat64Result(floatVal float64) Result[float64] {
	return Result[float64]{val: floatVal, isNil: false}
}

func CreateNilFloat64Result() Result[float64] {
	return Result[float64]{val: 0, isNil: true}
}

func CreateResultOf[T any](val T) Result[T] {
	return Result[T]{val: val, isNil: false}
}

func CreateNilResultOf[T any]() Result[T] {
	return Result[T]{isNil: true}
}

func CreateKeyWithMemberAndScoreResult(kmsVal KeyWithMemberAndScore) Result[KeyWithMemberAndScore] {
	return Result[KeyWithMemberAndScore]{val: kmsVal, isNil: false}
}

func CreateNilKeyWithMemberAndScoreResult() Result[KeyWithMemberAndScore] {
	return Result[KeyWithMemberAndScore]{val: KeyWithMemberAndScore{"", "", 0.0}, isNil: true}
}

func CreateKeyWithArrayOfMembersAndScoresResult(
	kmsVals KeyWithArrayOfMembersAndScores,
) Result[KeyWithArrayOfMembersAndScores] {
	return Result[KeyWithArrayOfMembersAndScores]{val: kmsVals, isNil: false}
}

func CreateNilKeyWithArrayOfMembersAndScoresResult() Result[KeyWithArrayOfMembersAndScores] {
	return Result[KeyWithArrayOfMembersAndScores]{val: KeyWithArrayOfMembersAndScores{"", nil}, isNil: true}
}

// Enum to distinguish value types stored in `ClusterValue`
type ValueType int

const (
	SingleValue ValueType = 1
	MultiValue  ValueType = 2
	NoValue     ValueType = 3
)

// Enum-like structure which stores either a single-node response or multi-node response.
// Multi-node response stored in a map, where keys are hostnames or "<ip>:<port>" strings.
//
// Example:
//
//	// Command failed:
//	value, err := clusterClient.CustomCommand(context.Background(), args)
//	value.IsEmpty(): true
//	err != nil: true
//
//	// Command returns response from multiple nodes:
//	value, _ := clusterClient.Info(context.Background())
//	for node, nodeResponse := range value.MultiValue() {
//	    response := nodeResponse
//	    // `node` stores cluster node IP/hostname, `response` stores the command output from that node
//	}
//
//	// Command returns a response from single node:
//	value, _ := clusterClient.InfoWithOptions(context.Background(), api.ClusterInfoOptions{InfoOptions: nil, Route:
//
// api.RandomRoute.ToPtr()})
//
//	response := value.SingleValue()
//	// `response` stores the command output from a cluster node
type ClusterValue[T any] struct {
	valueType   ValueType
	singleValue T
	mutiValue   map[string]T
}

// Get the single value stored (value returned by a single cluster node).
func (value ClusterValue[T]) SingleValue() T {
	return value.singleValue
}

// Get the multi value stored (value returned by multiple cluster nodes).
func (value ClusterValue[T]) MultiValue() map[string]T {
	return value.mutiValue
}

// Get the value type
func (value ClusterValue[T]) ValueType() ValueType {
	return value.valueType
}

func (value ClusterValue[T]) IsSingleValue() bool {
	return value.valueType == SingleValue
}

func (value ClusterValue[T]) IsMultiValue() bool {
	return value.valueType == MultiValue
}

func (value ClusterValue[T]) IsEmpty() bool {
	return value.valueType == NoValue
}

func CreateClusterValue[T any](data any) ClusterValue[T] {
	switch any(data).(type) {
	case map[string]any:
		return CreateClusterMultiValue(data.(map[string]T))
	case nil:
		return CreateEmptyClusterValue[T]()
	default:
		return CreateClusterSingleValue(data.(T))
	}
}

func CreateClusterSingleValue[T any](data T) ClusterValue[T] {
	return ClusterValue[T]{
		valueType:   SingleValue,
		singleValue: data,
	}
}

func CreateClusterMultiValue[T any](data map[string]T) ClusterValue[T] {
	return ClusterValue[T]{
		valueType: MultiValue,
		mutiValue: data,
	}
}

func CreateEmptyClusterValue[T any]() ClusterValue[T] {
	return ClusterValue[T]{
		valueType: NoValue,
	}
}

// XPendingSummary represents a summary of pending messages in a stream group.
// It includes the total number of pending messages, the ID of the first and last pending messages,
// and a list of consumer pending messages.
type XPendingSummary struct {
	// NumOfMessages is the total number of pending messages in the stream group.
	NumOfMessages int64

	// StartId is the ID of the first pending message in the stream group.
	StartId Result[string]

	// EndId is the ID of the last pending message in the stream group.
	EndId Result[string]

	// ConsumerMessages is a list of pending messages for each consumer in the stream group.
	ConsumerMessages []ConsumerPendingMessage
}

// ConsumerPendingMessage represents a pending message for a consumer in a Redis stream group.
// It includes the consumer's name and the count of pending messages for that consumer.
type ConsumerPendingMessage struct {
	// ConsumerName is the name of the consumer.
	ConsumerName string

	// MessageCount is the number of pending messages for the consumer.
	MessageCount int64
}

// XPendingDetail represents the details of a pending message in a stream group.
// It includes the message ID, the consumer's name, the idle time, and the delivery count.
type XPendingDetail struct {
	// Id is the ID of the pending message.
	Id string

	// ConsumerName is the name of the consumer who has the pending message.
	ConsumerName string

	// IdleTime is the amount of time (in milliseconds) that the message has been idle.
	IdleTime int64

	// DeliveryCount is the number of times the message has been delivered.
	DeliveryCount int64
}

func CreateNilXPendingSummary() XPendingSummary {
	return XPendingSummary{0, CreateNilStringResult(), CreateNilStringResult(), make([]ConsumerPendingMessage, 0)}
}

// XInfoConsumerInfo represents a group information returned by `XInfoConsumers` command.
type XInfoConsumerInfo struct {
	// The consumer's name.
	Name string
	// The number of entries in the PEL: pending messages for the consumer, which are messages that were delivered but are yet
	// to be acknowledged.
	Pending int64
	// The number of milliseconds that have passed since the consumer's last attempted interaction (Examples: XREADGROUP,
	// XCLAIM, XAUTOCLAIM).
	Idle int64
	// The number of milliseconds that have passed since the consumer's last successful interaction (Examples: XREADGROUP that
	// actually read some entries into the PEL, XCLAIM/XAUTOCLAIM that actually claimed some entries).
	Inactive Result[int64]
}

// XInfoGroupInfo represents a group information returned by `XInfoGroups` command.
type XInfoGroupInfo struct {
	// The consumer group's name.
	Name string
	// The number of consumers in the group.
	Consumers int64
	// The length of the group's Pending Entries List (PEL), which are messages that were delivered but are yet to be
	// acknowledged.
	Pending int64
	// The ID of the last entry delivered to the group's consumers.
	LastDeliveredId string
	// The logical "read counter" of the last entry delivered to the group's consumers.
	// Included in the response only on valkey 7.0.0 and above.
	EntriesRead Result[int64]
	// The number of entries in the stream that are still waiting to be delivered to the group's consumers, or a `nil` when
	// that number can't be determined.
	// Included in the response only on valkey 7.0.0 and above.
	Lag Result[int64]
}

// StreamEntry represents a single entry/element in a stream
type StreamEntry struct {
	// The unique identifier of the entry
	ID string
	// The fields associated with the entry
	Fields []KeyValue
}

// KeyValue represents the Key-value pairs added to the entry.
type KeyValue struct {
	// The name of the field
	Key string
	// The value of the field
	Value string
}

// StreamResponse represents a stream with its entries
type StreamResponse struct {
	// The entries in the stream
	Entries []StreamEntry
}

// XClaimResponse represents a claimed entry in a stream
type XClaimResponse struct {
	// The fields associated with the claimed entry
	Fields []KeyValue
}

// XInfoStreamResponse represents the information about a stream
type XInfoStreamResponse struct {
	// The number of entries in the stream
	Length int64
	// The number of keys in the underlying radix data structure
	RadixTreeKeys int64
	// The number of nodes in the underlying radix data structure
	RadixTreeNodes int64
	// The number of consumer groups defined for the stream
	Groups int64
	// The ID of the least-recently entry that was added to the stream
	LastGeneratedID string
	// The maximal entry ID that was deleted from the stream
	MaxDeletedEntryID string
	// The count of all entries added to the stream during its lifetime
	EntriesAdded int64
	// The ID and field-value tuples of the first entry in the stream
	FirstEntry StreamEntry
	// The ID and field-value tuples of the last entry in the stream
	LastEntry StreamEntry
}

// KeyValues represents a key and a list of associated values
type KeyValues struct {
	// The key associated with the values
	Key string
	// The slice of string values associated with the key
	Values []string
}
