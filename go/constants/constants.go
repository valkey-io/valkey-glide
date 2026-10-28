// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Package constants defines common constants used throughout Valkey GLIDE.
package constants

import (
	"errors"
)

// Valkey API keywords
const (
	CountKeyword      string = "COUNT"      // Specify the number of elements to return or scan (LPOS, LMPOP, BLMPOP, ZMPOP, BZMPOP, SCAN, HSCAN, SSCAN, ZSCAN, GEOSEARCH, XRANGE, XREVRANGE, XREAD, XREADGROUP, XAUTOCLAIM).
	FullKeyword       string = "FULL"       // Return the full stream info state (XINFO STREAM).
	MatchKeyword      string = "MATCH"      // Filter results by a glob-style pattern (SCAN, HSCAN, SSCAN, ZSCAN).
	NoValuesKeyword   string = "NOVALUES"   // Return only field names (HSCAN).
	WithScoreKeyword  string = "WITHSCORE"  // Include the score of the member (ZRANK, ZREVRANK).
	WithScoresKeyword string = "WITHSCORES" // Include scores along with members (ZRANGE, ZRANDMEMBER, ZDIFF, ZINTER, ZUNION).
	NoScoresKeyword   string = "NOSCORES"   // Return only members (ZSCAN).
	WithValuesKeyword string = "WITHVALUES" // Include values along with field names (HRANDFIELD).
	AggregateKeyWord  string = "AGGREGATE"  // Specify how scores are combined when merging sorted sets (ZINTER, ZINTERSTORE, ZUNION, ZUNIONSTORE).
	WeightsKeyword    string = "WEIGHTS"    // Specify a multiplication factor for the scores of each input sorted set (ZINTER, ZINTERSTORE, ZUNION, ZUNIONSTORE).
	RankKeyword       string = "RANK"       // Specify which match to return when multiple elements match (LPOS).
	MaxLenKeyword     string = "MAXLEN"     // Limit the number of elements compared, or trim a stream by length (LPOS, XTRIM).
	CopyKeyword       string = "COPY"       // Keep the keys on the source instance (MIGRATE).
	ReplaceKeyword    string = "REPLACE"    // Replace an existing key or library (COPY, RESTORE, MIGRATE, FUNCTION LOAD).
	ABSTTLKeyword     string = "ABSTTL"     // Interpret the TTL as an absolute Unix timestamp in milliseconds (RESTORE).
	AuthKeyword       string = "AUTH"       // Authenticate to the target instance with a password (MIGRATE).
	Auth2Keyword      string = "AUTH2"      // Authenticate to the target instance with a username and password (MIGRATE).
	KeysKeyword       string = "KEYS"       // Migrate multiple keys in a single command (MIGRATE).
	StoreKeyword      string = "STORE"      // Store the result in a destination key (SORT).
	DbKeyword         string = "DB"         // Specify the destination database (COPY).
	TypeKeyword       string = "TYPE"       // Filter results by data type (SCAN).
	ChangedKeyword    string = "CH"         // Count changed elements, not only added elements (ZADD, GEOADD).
	IncrKeyword       string = "INCR"       // Increment the score of the member, like ZINCRBY (ZADD).
	IfExistsKeyword   string = "XX"         // Return -1 if the key does not exist (SISMEMBER).

	/// Stream commands
	IdleKeyword         string = "IDLE"        // Filter entries by minimum idle time, or set the idle time, in milliseconds (XPENDING, XCLAIM).
	TimeKeyword         string = "TIME"        // Set the idle time to a Unix time in milliseconds (XCLAIM).
	RetryCountKeyword   string = "RETRYCOUNT"  // Set the retry counter (XCLAIM).
	ForceKeyword        string = "FORCE"       // Create pending entries for IDs not already in the PEL (XCLAIM).
	JustIdKeyword       string = "JUSTID"      // Return only the IDs of claimed entries (XCLAIM, XAUTOCLAIM).
	EntriesReadKeyword  string = "ENTRIESREAD" // Set the entries-read counter of the consumer group (XGROUP CREATE, XGROUP SETID).
	MakeStreamKeyword   string = "MKSTREAM"    // Create the stream if it does not exist (XGROUP CREATE).
	NoMakeStreamKeyword string = "NOMKSTREAM"  // Do not create the stream if it does not exist (XADD).
	BlockKeyword        string = "BLOCK"       // Block for up to the given number of milliseconds (XREAD, XREADGROUP).
	NoAckKeyword        string = "NOACK"       // Skip adding read entries to the PEL (XREADGROUP).
	LimitKeyword        string = "LIMIT"       // Cap the computed cardinality, or the number of entries trimmed (SINTERCARD, ZINTERCARD, XTRIM).
	MinIdKeyword        string = "MINID"       // Trim stream entries with IDs lower than the threshold (XTRIM).
	GroupKeyword        string = "GROUP"       // Specify the consumer group and consumer (XREADGROUP).
	StreamsKeyword      string = "STREAMS"     // Mark the start of the stream keys and IDs (XREAD, XREADGROUP).
	WithCodeKeyword     string = "WITHCODE"    // Include library code (FUNCTION LIST).
	LibraryNameKeyword  string = "LIBRARYNAME" // Filter results by library name pattern (FUNCTION LIST).
)

type InfBoundary string

const (
	// The highest bound in the sorted set
	PositiveInfinity InfBoundary = "+"
	// The lowest bound in the sorted set
	NegativeInfinity InfBoundary = "-"
)

const ReturnOldValue = "GET"

// A ConditionalSet defines whether a new value should be set or not.
type ConditionalSet string

const (
	// OnlyIfExists only sets the key if it already exists.
	// Equivalent to "XX" in the Valkey API.
	OnlyIfExists ConditionalSet = "XX"

	// OnlyIfDoesNotExist only sets the key if it does not already exist.
	// Equivalent to "NX" in the Valkey API.
	OnlyIfDoesNotExist ConditionalSet = "NX"

	// OnlyIfEquals only sets the key if it already exists and the value is equal to the given value.
	// Equivalent to "IFEQ" in the Valkey API.
	// Since Valkey 8.1.0.
	// Not supported when compression is enabled. See [Valkey GLIDE – Data Compression].
	//
	// [Valkey GLIDE – Data Compression]: https://glide.valkey.io/concepts/client-features/compression/#incompatible-commands
	OnlyIfEquals ConditionalSet = "IFEQ"

	// OnlyIfNotEquals only sets the key if it does not exist or if the value is not equal to the given value.
	// Equivalent to "IFNE" in the Valkey API.
	// Since Valkey 9.2.0.
	// Not supported when compression is enabled. See [Valkey GLIDE – Data Compression].
	//
	// [Valkey GLIDE – Data Compression]: https://glide.valkey.io/concepts/client-features/compression/#incompatible-commands
	OnlyIfNotEquals ConditionalSet = "IFNE"

	// OnlyIfFieldsDoNotExist only sets the hash fields if none of them already exist.
	// Equivalent to "FNX" in the Valkey API.
	// Used with hash field expiration commands.
	OnlyIfFieldsDoNotExist ConditionalSet = "FNX"

	// OnlyIfAllFieldsExist only sets the hash fields if all of them already exist.
	// Equivalent to "FXX" in the Valkey API.
	// Used with hash field expiration commands.
	OnlyIfAllFieldsExist ConditionalSet = "FXX"
)

func (conditionalSet ConditionalSet) ToString() (string, error) {
	switch conditionalSet {
	case OnlyIfExists:
		return string(OnlyIfExists), nil
	case OnlyIfDoesNotExist:
		return string(OnlyIfDoesNotExist), nil
	case OnlyIfEquals:
		return string(OnlyIfEquals), nil
	case OnlyIfNotEquals:
		return string(OnlyIfNotEquals), nil
	case OnlyIfFieldsDoNotExist:
		return string(OnlyIfFieldsDoNotExist), nil
	case OnlyIfAllFieldsExist:
		return string(OnlyIfAllFieldsExist), nil
	default:
		return "", errors.New("invalid conditional set")
	}
}

type ExpireCondition string

const (
	// HasExistingExpiry only sets the key if it already exists.
	// Equivalent to "XX" in the Valkey API.
	HasExistingExpiry ExpireCondition = "XX"

	// HasNoExpiry only sets the key if it does not already exist.
	// Equivalent to "NX" in the Valkey API.
	HasNoExpiry ExpireCondition = "NX"

	// NewExpiryGreaterThanCurrent only sets the key if its greater than current.
	// Equivalent to "GT" in the Valkey API.
	NewExpiryGreaterThanCurrent ExpireCondition = "GT"

	// NewExpiryLessThanCurrent only sets the key if its lesser than current.
	// Equivalent to "LT" in the Valkey API.
	NewExpiryLessThanCurrent ExpireCondition = "LT"
)

func (expireCondition ExpireCondition) ToString() (string, error) {
	switch expireCondition {
	case HasExistingExpiry:
		return string(HasExistingExpiry), nil
	case HasNoExpiry:
		return string(HasNoExpiry), nil
	case NewExpiryGreaterThanCurrent:
		return string(NewExpiryGreaterThanCurrent), nil
	case NewExpiryLessThanCurrent:
		return string(NewExpiryLessThanCurrent), nil
	default:
		return "", errors.New("invalid expire condition")
	}
}

// An ExpiryType is used to configure the type of expiration for a value.
type ExpiryType string

const (
	// Keep the existing expiration of the value.
	KeepExisting ExpiryType = "KEEPTTL"
	// Expire the value after [options.Expiry.Count] seconds
	Seconds ExpiryType = "EX"
	// Expire the value after [options.Expiry.Count] milliseconds.
	Milliseconds ExpiryType = "PX"
	// Expire the value after the Unix time specified by [options.Expiry.Count], in seconds.
	UnixSeconds ExpiryType = "EXAT"
	// Expire the value after the Unix time specified by [options.Expiry.Count], in milliseconds.
	UnixMilliseconds ExpiryType = "PXAT"
	// Remove the expiry associated with the key.
	Persist ExpiryType = "PERSIST"
)

// A InsertPosition defines where to insert new elements into a list.
//
// See [valkey.io]
//
// [valkey.io]: https://valkey.io/commands/linsert/
type InsertPosition string

const (
	// Insert new element before the pivot.
	Before InsertPosition = "BEFORE"
	// Insert new element after the pivot.
	After InsertPosition = "AFTER"
)

func (insertPosition InsertPosition) ToString() (string, error) {
	switch insertPosition {
	case Before:
		return string(Before), nil
	case After:
		return string(After), nil
	default:
		return "", errors.New("invalid insert position")
	}
}

// Enumeration representing element popping or adding direction for the [api.ListCommands].
type ListDirection string

const (
	// Represents the option that elements should be popped from or added to the left side of a list.
	Left ListDirection = "LEFT"
	// Represents the option that elements should be popped from or added to the right side of a list.
	Right ListDirection = "RIGHT"
)

func (listDirection ListDirection) ToString() (string, error) {
	switch listDirection {
	case Left:
		return string(Left), nil
	case Right:
		return string(Right), nil
	default:
		return "", errors.New("invalid list direction")
	}
}

// Mandatory parameter for [ZMPop] and for [BZMPop].
// Defines which elements to pop from the sorted set.
type ScoreFilter string

const (
	// Pop elements with the highest scores.
	MAX ScoreFilter = "MAX"
	// Pop elements with the lowest scores.
	MIN ScoreFilter = "MIN"
)

func (scoreFilter ScoreFilter) ToString() (string, error) {
	switch scoreFilter {
	case MAX:
		return string(MAX), nil
	case MIN:
		return string(MIN), nil
	default:
		return "", errors.New("invalid score filter")
	}
}

type EvictionType string

const (
	// It represents the idletime of object.
	IDLETIME EvictionType = "IDLETIME"
	// It represents the frequency of object.
	FREQ EvictionType = "FREQ"
)

// Enumeration representing information section which could be queried by `INFO` command.
type Section string

const (
	// ALL: Return all sections (excluding module generated ones)
	All Section = "all"
	// CLIENTS: Client connections section
	Clients Section = "clients"
	// CLUSTER: Valkey Cluster section
	Cluster Section = "cluster"
	// CLUSTER_INFO: Valkey Cluster information
	// Since Valkey 9.1.0.
	ClusterInfo Section = "cluster_info"
	// COMMANDSTATS: Valkey command statistics
	Commandstats Section = "commandstats"
	// CPU: CPU consumption statistics
	Cpu Section = "cpu"
	// DEFAULT: Return only the default set of sections
	Default Section = "default"
	// ERRORSTATS: Valkey error statistics
	Errorstats Section = "errorstats"
	// EVERYTHING: Includes all and modules
	Everything Section = "everything"
	// KEYSPACE: Database related statistics
	Keyspace Section = "keyspace"
	// LATENCYSTATS: Valkey command latency percentile distribution statistics
	Latencystats Section = "latencystats"
	// MEMORY: Memory consumption related information
	Memory Section = "memory"
	// MODULES: Modules section
	Modules Section = "modules"
	// PERSISTENCE: RDB and AOF related information
	Persistence Section = "persistence"
	// REPLICATION: Master/replica replication information
	Replication Section = "replication"
	// SENTINEL: Valkey Sentinel section (only applicable to Sentinel instances)
	Sentinel Section = "sentinel"
	// SERVER: General information about the server
	Server Section = "server"
	// STATS: General statistics
	Stats Section = "stats"
)

// Identifies Scan Type of ClusterScan command
type ObjectType string

const (
	// Represents a string data type
	ObjectTypeString ObjectType = "string"
	// Represents a list data type
	ObjectTypeList ObjectType = "list"
	// Represents a set data type
	ObjectTypeSet ObjectType = "set"
	// Represents a sorted set data type
	ObjectTypeZSet ObjectType = "zset"
	// Represents a hash data type
	ObjectTypeHash ObjectType = "hash"
	// Represents a stream data type
	ObjectTypeStream ObjectType = "stream"
)

// The unit of measurement for the geospatial data
type GeoUnit string

const (
	// Represents distance in kilometers
	GeoUnitKilometers GeoUnit = "km"
	// Represents distance in meters
	GeoUnitMeters GeoUnit = "m"
	// Represents distance in miles
	GeoUnitMiles GeoUnit = "mi"
	// Represents distance in feet
	GeoUnitFeet GeoUnit = "ft"
)

// Valkey API keywords for the `GeoSearch` command
const (
	WithCoordValkeyApi = "WITHCOORD"
	WithDistValkeyApi  = "WITHDIST"
	WithHashValkeyApi  = "WITHHASH"
)

// The search origin API keyword for the `GeoCoordOrigin`
const (
	GeoCoordOriginAPIKeyword  = "FROMLONLAT"
	GeoMemberOriginAPIKeyword = "FROMMEMBER"
)

// The shape of the search area for the `GeoSearch` command
type SearchShape string

const (
	BYRADIUS SearchShape = "BYRADIUS"
	BYBOX    SearchShape = "BYBOX"
)

// DistanceMetric is the distance metric used to measure similarity between vectors.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type DistanceMetric string

const (
	// DistanceMetricL2 is the Euclidean distance.
	DistanceMetricL2 DistanceMetric = "L2"
	// DistanceMetricIP is the inner product.
	DistanceMetricIP DistanceMetric = "IP"
	// DistanceMetricCosine is the cosine distance.
	DistanceMetricCosine DistanceMetric = "COSINE"
)

// VectorDataType is the data type for vector fields.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type VectorDataType string

const (
	// VectorDataTypeFloat32 is the FLOAT32 vector type (default).
	VectorDataTypeFloat32 VectorDataType = "FLOAT32"
)

// IndexDataType is the type of the index dataset.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
type IndexDataType string

const (
	// IndexDataTypeHash indicates data stored in hashes; field identifiers are field names within the hashes.
	IndexDataTypeHash IndexDataType = "HASH"
	// IndexDataTypeJSON indicates data stored as JSON documents; field identifiers are JSON Path expressions.
	IndexDataTypeJSON IndexDataType = "JSON"
)

// FtSearchSortOrder is the sort order for FT.SEARCH SORTBY clause.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.search/
type FtSearchSortOrder string

const (
	// FtSearchSortOrderAsc sorts results in ascending order.
	FtSearchSortOrderAsc FtSearchSortOrder = "ASC"
	// FtSearchSortOrderDesc sorts results in descending order.
	FtSearchSortOrderDesc FtSearchSortOrder = "DESC"
)

// FtAggregateOrderBy is the sort order for FT.AGGREGATE SORTBY clause.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.aggregate/
type FtAggregateOrderBy string

const (
	// FtAggregateOrderByAsc sorts results in ascending order.
	FtAggregateOrderByAsc FtAggregateOrderBy = "ASC"
	// FtAggregateOrderByDesc sorts results in descending order.
	FtAggregateOrderByDesc FtAggregateOrderBy = "DESC"
)

// FunctionRestorePolicy represents the policy to use when restoring functions.
// See https://valkey.io/commands/function-restore/ for details.
type FunctionRestorePolicy string

const (
	// AppendPolicy appends the restored libraries to the existing libraries and aborts on collision.
	// This is the default policy.
	AppendPolicy FunctionRestorePolicy = "APPEND"
	// FlushPolicy deletes all existing libraries before restoring the payload.
	FlushPolicy FunctionRestorePolicy = "FLUSH"
	// ReplacePolicy appends the restored libraries to the existing libraries, replacing any existing ones
	// in case of name collisions. Note that this policy doesn't prevent function name collisions, only libraries.
	ReplacePolicy FunctionRestorePolicy = "REPLACE"
)
