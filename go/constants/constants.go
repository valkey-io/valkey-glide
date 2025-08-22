// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Package constants defines common constants used throughout Valkey GLIDE.
package constants

import (
	"errors"
)

const (
	CountKeyword      string = "COUNT"      // Valkey API keyword used to extract specific number of matching indices from a list.
	FullKeyword       string = "FULL"       // Valkey API keyword used in XINFO STREAM
	MatchKeyword      string = "MATCH"      // Valkey API keyword used to indicate the match filter.
	NoValuesKeyword   string = "NOVALUES"   // Valkey API keyword for the no value option for hcsan command.
	WithScoreKeyword  string = "WITHSCORE"  // Valkey API keyword for the with score option for zrank and zrevrank commands.
	WithScoresKeyword string = "WITHSCORES" // Valkey API keyword for ZRandMember and ZDiff command to return scores along with members.
	NoScoresKeyword   string = "NOSCORES"   // Valkey API keyword for the no scores option for zscan command.
	WithValuesKeyword string = "WITHVALUES" // Valkey API keyword to query hash values along their names in `HRANDFIELD`.
	AggregateKeyWord  string = "AGGREGATE"  // Valkey API keyword for the aggregate option for multiple commands.
	WeightsKeyword    string = "WEIGHTS"    // Valkey API keyword for the weights option for multiple commands.
	RankKeyword       string = "RANK"       // Valkey API keyword use to determine the rank of the match to return.
	MaxLenKeyword     string = "MAXLEN"     // Valkey API keyword used to determine the maximum number of list items to compare.
	ReplaceKeyword    string = "REPLACE"    // Subcommand string to replace existing key.
	ABSTTLKeyword     string = "ABSTTL"     // Subcommand string to represent absolute timestamp (in milliseconds) for TTL.
	StoreKeyword      string = "STORE"
	DbKeyword         string = "DB"
	TypeKeyword       string = "TYPE"
	ChangedKeyword    string = "CH"   // Valkey API keyword used to return total number of elements changed
	IncrKeyword       string = "INCR" // Valkey API keyword to make zadd act like ZINCRBY.
	/// Valkey API keywords for stream commands
	IdleKeyword         string = "IDLE"       // ValKey API string to designate IDLE time in milliseconds
	TimeKeyword         string = "TIME"       // ValKey API string to designate TIME time in unix-milliseconds
	RetryCountKeyword   string = "RETRYCOUNT" // ValKey API string to designate RETRYCOUNT
	ForceKeyword        string = "FORCE"      // ValKey API string to designate FORCE
	JustIdKeyword       string = "JUSTID"     // ValKey API string to designate JUSTID
	EntriesReadKeyword  string = "ENTRIESREAD"
	MakeStreamKeyword   string = "MKSTREAM"
	NoMakeStreamKeyword string = "NOMKSTREAM"
	BlockKeyword        string = "BLOCK"
	NoAckKeyword        string = "NOACK"
	LimitKeyword        string = "LIMIT"
	MinIdKeyword        string = "MINID"
	GroupKeyword        string = "GROUP"
	StreamsKeyword      string = "STREAMS"
	WithCodeKeyword     string = "WITHCODE"
	LibraryNameKeyword  string = "LIBRARYNAME"
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
	// OnlyIfExists only sets the key if it already exists. Equivalent to "XX" in the valkey API.
	OnlyIfExists ConditionalSet = "XX"
	// OnlyIfDoesNotExist only sets the key if it does not already exist. Equivalent to "NX" in the valkey API.
	OnlyIfDoesNotExist ConditionalSet = "NX"
	// OnlyIfEquals only sets the key if it already exists and the value is equal to the given value. Equivalent to "IFEQ" in
	// the valkey API.
	//
	// since Valkey 8.1 and above.
	OnlyIfEquals ConditionalSet = "IFEQ"
	// OnlyIfFieldsDoNotExist only sets the hash fields if none of them already exist. Equivalent to "FNX" in the valkey API.
	// Used with hash field expiration commands.
	OnlyIfFieldsDoNotExist ConditionalSet = "FNX"
	// OnlyIfAllFieldsExist only sets the hash fields if all of them already exist. Equivalent to "FXX" in the valkey API.
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
	// HasExistingExpiry only sets the key if it already exists. Equivalent to "XX" in the valkey API.
	HasExistingExpiry ExpireCondition = "XX"
	// HasNoExpiry only sets the key if it does not already exist. Equivalent to "NX" in the valkey API.
	HasNoExpiry ExpireCondition = "NX"
	// NewExpiryGreaterThanCurrent only sets the key if its greater than current. Equivalent to "GT" in the valkey API.
	NewExpiryGreaterThanCurrent ExpireCondition = "GT"
	// NewExpiryLessThanCurrent only sets the key if its lesser than current. Equivalent to "LT" in the valkey API.
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
	// SERVER: General information about the server
	Server Section = "server"
	// CLIENTS: Client connections section
	Clients Section = "clients"
	// MEMORY: Memory consumption related information
	Memory Section = "memory"
	// PERSISTENCE: RDB and AOF related information
	Persistence Section = "persistence"
	// STATS: General statistics
	Stats Section = "stats"
	// REPLICATION: Master/replica replication information
	Replication Section = "replication"
	// CPU: CPU consumption statistics
	Cpu Section = "cpu"
	// COMMANDSTATS: Valkey command statistics
	Commandstats Section = "commandstats"
	// LATENCYSTATS: Valkey command latency percentile distribution statistics
	Latencystats Section = "latencystats"
	// SENTINEL: Valkey Sentinel section (only applicable to Sentinel instances)
	Sentinel Section = "sentinel"
	// CLUSTER: Valkey Cluster section
	Cluster Section = "cluster"
	// MODULES: Modules section
	Modules Section = "modules"
	// KEYSPACE: Database related statistics
	Keyspace Section = "keyspace"
	// ERRORSTATS: Valkey error statistics
	Errorstats Section = "errorstats"
	// ALL: Return all sections (excluding module generated ones)
	All Section = "all"
	// DEFAULT: Return only the default set of sections
	Default Section = "default"
	// EVERYTHING: Includes all and modules
	Everything Section = "everything"
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
