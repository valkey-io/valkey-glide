/* Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

typedef enum Level {
  ERROR = 0,
  WARN = 1,
  INFO = 2,
  DEBUG = 3,
  TRACE = 4,
  OFF = 5,
} Level;

typedef enum PushKind {
  PushDisconnection,
  PushOther,
  PushInvalidate,
  PushMessage,
  PushPMessage,
  PushSMessage,
  PushUnsubscribe,
  PushPUnsubscribe,
  PushSUnsubscribe,
  PushSubscribe,
  PushPSubscribe,
  PushSSubscribe,
} PushKind;

typedef enum RequestErrorType {
  Unspecified = 0,
  ExecAbort = 1,
  Timeout = 2,
  Disconnect = 3,
} RequestErrorType;

typedef enum RequestType {
  /**
   * Invalid request type
   */
  InvalidRequest = 0,
  /**
   * An unknown command, where all arguments are defined by the user.
   */
  CustomCommand = 1,
  BitCount = 101,
  BitField = 102,
  BitFieldReadOnly = 103,
  BitOp = 104,
  BitPos = 105,
  GetBit = 106,
  SetBit = 107,
  Asking = 201,
  ClusterAddSlots = 202,
  ClusterAddSlotsRange = 203,
  ClusterBumpEpoch = 204,
  ClusterCountFailureReports = 205,
  ClusterCountKeysInSlot = 206,
  ClusterDelSlots = 207,
  ClusterDelSlotsRange = 208,
  ClusterFailover = 209,
  ClusterFlushSlots = 210,
  ClusterForget = 211,
  ClusterGetKeysInSlot = 212,
  ClusterInfo = 213,
  ClusterKeySlot = 214,
  ClusterLinks = 215,
  ClusterMeet = 216,
  ClusterMyId = 217,
  ClusterMyShardId = 218,
  ClusterNodes = 219,
  ClusterReplicas = 220,
  ClusterReplicate = 221,
  ClusterReset = 222,
  ClusterSaveConfig = 223,
  ClusterSetConfigEpoch = 224,
  ClusterSetslot = 225,
  ClusterShards = 226,
  ClusterSlaves = 227,
  ClusterSlots = 228,
  ReadOnly = 229,
  ReadWrite = 230,
  Auth = 301,
  ClientCaching = 302,
  ClientGetName = 303,
  ClientGetRedir = 304,
  ClientId = 305,
  ClientInfo = 306,
  ClientKillSimple = 307,
  ClientKill = 308,
  ClientList = 309,
  ClientNoEvict = 310,
  ClientNoTouch = 311,
  ClientPause = 312,
  ClientReply = 313,
  ClientSetInfo = 314,
  ClientSetName = 315,
  ClientTracking = 316,
  ClientTrackingInfo = 317,
  ClientUnblock = 318,
  ClientUnpause = 319,
  Echo = 320,
  Hello = 321,
  Ping = 322,
  Quit = 323,
  Reset = 324,
  Select = 325,
  Copy = 401,
  Del = 402,
  Dump = 403,
  Exists = 404,
  Expire = 405,
  ExpireAt = 406,
  ExpireTime = 407,
  Keys = 408,
  Migrate = 409,
  Move = 410,
  ObjectEncoding = 411,
  ObjectFreq = 412,
  ObjectIdleTime = 413,
  ObjectRefCount = 414,
  Persist = 415,
  PExpire = 416,
  PExpireAt = 417,
  PExpireTime = 418,
  PTTL = 419,
  RandomKey = 420,
  Rename = 421,
  RenameNX = 422,
  Restore = 423,
  Scan = 424,
  Sort = 425,
  SortReadOnly = 426,
  Touch = 427,
  TTL = 428,
  Type = 429,
  Unlink = 430,
  Wait = 431,
  WaitAof = 432,
  GeoAdd = 501,
  GeoDist = 502,
  GeoHash = 503,
  GeoPos = 504,
  GeoRadius = 505,
  GeoRadiusReadOnly = 506,
  GeoRadiusByMember = 507,
  GeoRadiusByMemberReadOnly = 508,
  GeoSearch = 509,
  GeoSearchStore = 510,
  HDel = 601,
  HExists = 602,
  HGet = 603,
  HGetAll = 604,
  HIncrBy = 605,
  HIncrByFloat = 606,
  HKeys = 607,
  HLen = 608,
  HMGet = 609,
  HMSet = 610,
  HRandField = 611,
  HScan = 612,
  HSet = 613,
  HSetNX = 614,
  HStrlen = 615,
  HVals = 616,
  HSetEx = 617,
  HGetEx = 618,
  HExpire = 619,
  HExpireAt = 620,
  HPExpire = 621,
  HPExpireAt = 622,
  HPersist = 623,
  HTtl = 624,
  HPTtl = 625,
  HExpireTime = 626,
  HPExpireTime = 627,
  PfAdd = 701,
  PfCount = 702,
  PfMerge = 703,
  BLMove = 801,
  BLMPop = 802,
  BLPop = 803,
  BRPop = 804,
  BRPopLPush = 805,
  LIndex = 806,
  LInsert = 807,
  LLen = 808,
  LMove = 809,
  LMPop = 810,
  LPop = 811,
  LPos = 812,
  LPush = 813,
  LPushX = 814,
  LRange = 815,
  LRem = 816,
  LSet = 817,
  LTrim = 818,
  RPop = 819,
  RPopLPush = 820,
  RPush = 821,
  RPushX = 822,
  PSubscribe = 901,
  Publish = 902,
  PubSubChannels = 903,
  PubSubNumPat = 904,
  PubSubNumSub = 905,
  PubSubShardChannels = 906,
  PubSubShardNumSub = 907,
  PUnsubscribe = 908,
  SPublish = 909,
  SSubscribe = 910,
  Subscribe = 911,
  SUnsubscribe = 912,
  Unsubscribe = 913,
  Eval = 1001,
  EvalReadOnly = 1002,
  EvalSha = 1003,
  EvalShaReadOnly = 1004,
  FCall = 1005,
  FCallReadOnly = 1006,
  FunctionDelete = 1007,
  FunctionDump = 1008,
  FunctionFlush = 1009,
  FunctionKill = 1010,
  FunctionList = 1011,
  FunctionLoad = 1012,
  FunctionRestore = 1013,
  FunctionStats = 1014,
  ScriptDebug = 1015,
  ScriptExists = 1016,
  ScriptFlush = 1017,
  ScriptKill = 1018,
  ScriptLoad = 1019,
  ScriptShow = 1020,
  AclCat = 1101,
  AclDelUser = 1102,
  AclDryRun = 1103,
  AclGenPass = 1104,
  AclGetUser = 1105,
  AclList = 1106,
  AclLoad = 1107,
  AclLog = 1108,
  AclSave = 1109,
  AclSetSser = 1110,
  AclUsers = 1111,
  AclWhoami = 1112,
  BgRewriteAof = 1113,
  BgSave = 1114,
  Command_ = 1115,
  CommandCount = 1116,
  CommandDocs = 1117,
  CommandGetKeys = 1118,
  CommandGetKeysAndFlags = 1119,
  CommandInfo = 1120,
  CommandList = 1121,
  ConfigGet = 1122,
  ConfigResetStat = 1123,
  ConfigRewrite = 1124,
  ConfigSet = 1125,
  DBSize = 1126,
  FailOver = 1127,
  FlushAll = 1128,
  FlushDB = 1129,
  Info = 1130,
  LastSave = 1131,
  LatencyDoctor = 1132,
  LatencyGraph = 1133,
  LatencyHistogram = 1134,
  LatencyHistory = 1135,
  LatencyLatest = 1136,
  LatencyReset = 1137,
  Lolwut = 1138,
  MemoryDoctor = 1139,
  MemoryMallocStats = 1140,
  MemoryPurge = 1141,
  MemoryStats = 1142,
  MemoryUsage = 1143,
  ModuleList = 1144,
  ModuleLoad = 1145,
  ModuleLoadEx = 1146,
  ModuleUnload = 1147,
  Monitor = 1148,
  PSync = 1149,
  ReplConf = 1150,
  ReplicaOf = 1151,
  RestoreAsking = 1152,
  Role = 1153,
  Save = 1154,
  ShutDown = 1155,
  SlaveOf = 1156,
  SlowLogGet = 1157,
  SlowLogLen = 1158,
  SlowLogReset = 1159,
  SwapDb = 1160,
  Sync = 1161,
  Time = 1162,
  SAdd = 1201,
  SCard = 1202,
  SDiff = 1203,
  SDiffStore = 1204,
  SInter = 1205,
  SInterCard = 1206,
  SInterStore = 1207,
  SIsMember = 1208,
  SMembers = 1209,
  SMIsMember = 1210,
  SMove = 1211,
  SPop = 1212,
  SRandMember = 1213,
  SRem = 1214,
  SScan = 1215,
  SUnion = 1216,
  SUnionStore = 1217,
  BZMPop = 1301,
  BZPopMax = 1302,
  BZPopMin = 1303,
  ZAdd = 1304,
  ZCard = 1305,
  ZCount = 1306,
  ZDiff = 1307,
  ZDiffStore = 1308,
  ZIncrBy = 1309,
  ZInter = 1310,
  ZInterCard = 1311,
  ZInterStore = 1312,
  ZLexCount = 1313,
  ZMPop = 1314,
  ZMScore = 1315,
  ZPopMax = 1316,
  ZPopMin = 1317,
  ZRandMember = 1318,
  ZRange = 1319,
  ZRangeByLex = 1320,
  ZRangeByScore = 1321,
  ZRangeStore = 1322,
  ZRank = 1323,
  ZRem = 1324,
  ZRemRangeByLex = 1325,
  ZRemRangeByRank = 1326,
  ZRemRangeByScore = 1327,
  ZRevRange = 1328,
  ZRevRangeByLex = 1329,
  ZRevRangeByScore = 1330,
  ZRevRank = 1331,
  ZScan = 1332,
  ZScore = 1333,
  ZUnion = 1334,
  ZUnionStore = 1335,
  XAck = 1401,
  XAdd = 1402,
  XAutoClaim = 1403,
  XClaim = 1404,
  XDel = 1405,
  XGroupCreate = 1406,
  XGroupCreateConsumer = 1407,
  XGroupDelConsumer = 1408,
  XGroupDestroy = 1409,
  XGroupSetId = 1410,
  XInfoConsumers = 1411,
  XInfoGroups = 1412,
  XInfoStream = 1413,
  XLen = 1414,
  XPending = 1415,
  XRange = 1416,
  XRead = 1417,
  XReadGroup = 1418,
  XRevRange = 1419,
  XSetId = 1420,
  XTrim = 1421,
  Append = 1501,
  Decr = 1502,
  DecrBy = 1503,
  Get = 1504,
  GetDel = 1505,
  GetEx = 1506,
  GetRange = 1507,
  GetSet = 1508,
  Incr = 1509,
  IncrBy = 1510,
  IncrByFloat = 1511,
  LCS = 1512,
  MGet = 1513,
  MSet = 1514,
  MSetNX = 1515,
  PSetEx = 1516,
  Set = 1517,
  SetEx = 1518,
  SetNX = 1519,
  SetRange = 1520,
  Strlen = 1521,
  Substr = 1522,
  Discard = 1601,
  Exec = 1602,
  Multi = 1603,
  UnWatch = 1604,
  Watch = 1605,
  JsonArrAppend = 2001,
  JsonArrIndex = 2002,
  JsonArrInsert = 2003,
  JsonArrLen = 2004,
  JsonArrPop = 2005,
  JsonArrTrim = 2006,
  JsonClear = 2007,
  JsonDebug = 2008,
  JsonDel = 2009,
  JsonForget = 2010,
  JsonGet = 2011,
  JsonMGet = 2012,
  JsonNumIncrBy = 2013,
  JsonNumMultBy = 2014,
  JsonObjKeys = 2015,
  JsonObjLen = 2016,
  JsonResp = 2017,
  JsonSet = 2018,
  JsonStrAppend = 2019,
  JsonStrLen = 2020,
  JsonToggle = 2021,
  JsonType = 2022,
  FtList = 2101,
  FtAggregate = 2102,
  FtAliasAdd = 2103,
  FtAliasDel = 2104,
  FtAliasList = 2105,
  FtAliasUpdate = 2106,
  FtCreate = 2107,
  FtDropIndex = 2108,
  FtExplain = 2109,
  FtExplainCli = 2110,
  FtInfo = 2111,
  FtProfile = 2112,
  FtSearch = 2113,
} RequestType;

typedef enum ResponseType {
  Null = 0,
  Int = 1,
  Float = 2,
  Bool = 3,
  String = 4,
  Array = 5,
  Map = 6,
  Sets = 7,
  Ok = 8,
  Error = 9,
} ResponseType;

typedef enum RouteType {
  AllNodes = 0,
  AllPrimaries,
  Random,
  SlotId,
  SlotKey,
  ByAddress,
} RouteType;

/**
 * A mirror of [`SlotAddr`]
 */
typedef enum SlotType {
  Primary = 0,
  Replica,
} SlotType;

typedef struct ScriptHashBuffer {
  uint8_t *ptr;
  uintptr_t len;
  uintptr_t capacity;
} ScriptHashBuffer;

/**
 * The struct represents the response of the command.
 *
 * It will have one of the value populated depending on the return type of the command.
 *
 * The struct is freed by the external caller by using `free_command_response` to avoid memory leaks.
 * TODO: Add a type enum to validate what type of response is being sent in the CommandResponse.
 */
typedef struct CommandResponse {
  enum ResponseType response_type;
  int64_t int_value;
  double float_value;
  bool bool_value;
  /**
   * Below two values are related to each other.
   * `string_value` represents the string.
   * `string_value_len` represents the length of the string.
   */
  char *string_value;
  long string_value_len;
  /**
   * Below two values are related to each other.
   * `array_value` represents the array of CommandResponse.
   * `array_value_len` represents the length of the array.
   */
  struct CommandResponse *array_value;
  long array_value_len;
  /**
   * Below two values represent the Map structure inside CommandResponse.
   * The map is transformed into an array of (map_key: CommandResponse, map_value: CommandResponse) and passed to the foreign language.
   * These are represented as pointers as the map can be null (optionally present).
   */
  struct CommandResponse *map_key;
  struct CommandResponse *map_value;
  /**
   * Below two values are related to each other.
   * `sets_value` represents the set of CommandResponse.
   * `sets_value_len` represents the length of the set.
   */
  struct CommandResponse *sets_value;
  long sets_value_len;
} CommandResponse;

/**
 * Represents an error returned from a command execution.
 *
 * This struct is returned as part of a [`CommandResult`] when a command fails in synchronous operations.
 * It contains both the error type and a message explaining the cause.
 *
 * # Fields
 *
 * - `command_error_message`: A null-terminated C string describing the error.
 * - `command_error_type`: An enum identifying the type of error. See [`RequestErrorType`] for details.
 *
 * # Safety
 *
 * The pointer `command_error_message` must remain valid and not be freed until after
 * [`free_command_result`] is called.
 *
 */
typedef struct CommandError {
  const char *command_error_message;
  enum RequestErrorType command_error_type;
} CommandError;

/**
 * Represents the result of executing a command, either a successful response or an error.
 *
 * This is the  return type for FFI functions that execute commands synchronously (e.g. with a SyncClient).
 * It is a tagged struct containing either a valid [`CommandResponse`] or a [`CommandError`].
 * If `command_error` is non-null, then `response` is guaranteed to be null and vice versa.
 *
 * # Fields
 *
 * - `response`: A pointer to a [`CommandResponse`] if the command was successful. Null if there was an error.
 * - `command_error`: A pointer to a [`CommandError`] if the command failed. Null if the command succeeded.
 *
 * # Ownership
 *
 * The returned pointer to `CommandResult` must be freed using [`free_command_result`] to avoid memory leaks.
 * This will recursively free both the response or the error, depending on which is set.
 *
 * # Safety
 *
 * The caller must check which field is non-null before accessing its contents.
 * Only one of the two fields (`response` or `command_error`) will be set.
 */
typedef struct CommandResult {
  struct CommandResponse *response;
  struct CommandError *command_error;
} CommandResult;

/**
 * The connection response.
 *
 * It contains either a connection or an error. It is represented as a struct instead of a union for ease of use in the wrapper language.
 *
 * The struct is freed by the external caller by using `free_connection_response` to avoid memory leaks.
 */
typedef struct ConnectionResponse {
  const void *conn_ptr;
  const char *connection_error_message;
} ConnectionResponse;

/**
 * Success callback that is called when a command succeeds.
 *
 * The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
 *
 * `index_ptr` is a baton-pass back to the caller language to uniquely identify the promise.
 * `message` is the value returned by the command. The 'message' is managed by Rust and is freed when the callback returns control back to the caller.
 *
 * # Safety
 * `message` must be a valid pointer to a `CommandResponse` and must be freed using [`free_command_response`].
 */
typedef void (*SuccessCallback)(uintptr_t index_ptr, const struct CommandResponse *message);

/**
 * Failure callback that is called when a command fails.
 *
 * The failure callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
 *
 * `index_ptr` is a baton-pass back to the caller language to uniquely identify the promise.
 * `error_message` is the error message returned by server for the failed command. The 'error_message' is managed by Rust and is freed when the callback returns control back to the caller.
 * `error_type` is the type of error returned by glide-core, depending on the `RedisError` returned.
 *
 * # Safety
 * `error_message` must be a valid pointer to a `c_char`.
 */
typedef void (*FailureCallback)(uintptr_t index_ptr,
                                const char *error_message,
                                enum RequestErrorType error_type);

/**
 * Specifies the type of client used to execute commands.
 *
 * This enum distinguishes between synchronous and asynchronous client modes.
 * It is passed from the calling language (e.g. Go or Python) to determine how
 * command execution should be handled.
 *
 * # Variants
 *
 * - `AsyncClient`: Executes commands asynchronously. Includes callbacks for success and failure
 *   that will be invoked once the command completes.
 * - `SyncClient`: Executes commands synchronously and returns a result directly.
 */
typedef enum ClientType_Tag {
  AsyncClient,
  SyncClient,
} ClientType_Tag;

typedef struct AsyncClient_Body {
  SuccessCallback success_callback;
  FailureCallback failure_callback;
} AsyncClient_Body;

typedef struct ClientType {
  ClientType_Tag tag;
  union {
    AsyncClient_Body async_client;
  };
} ClientType;

/**
 * PubSub callback that is called when a push notification is received.
 *
 * The PubSub callback needs to handle the push notification synchronously, since the data will be dropped by Rust once the callback returns.
 * The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
 *
 * # Parameters
 * * `client_ptr`: A baton-pass back to the caller language to uniquely identify the client.
 * * `kind`: An enum variant representing the PushKind (Message, PMessage, SMessage, etc.)
 * * `message`: A pointer to the raw message bytes.
 * * `message_len`: The length of the message data in bytes.
 * * `channel`: A pointer to the raw request name bytes.
 * * `channel_len`: The length of the request name in bytes.
 * * `pattern`: A pointer to the raw pattern bytes (null if no pattern).
 * * `pattern_len`: The length of the pattern in bytes (0 if no pattern).
 *
 * # Safety
 * The pointers are only valid during the callback execution and will be freed
 * automatically when the callback returns. Any data needed beyond the callback's
 * execution must be copied.
 */
typedef void (*PubSubCallback)(uintptr_t client_ptr,
                               enum PushKind kind,
                               const uint8_t *message,
                               int64_t message_len,
                               const uint8_t *channel,
                               int64_t channel_len,
                               const uint8_t *pattern,
                               int64_t pattern_len);

typedef struct CmdInfo {
  enum RequestType request_type;
  const uint8_t *const *args;
  uintptr_t arg_count;
  const uintptr_t *args_len;
} CmdInfo;

typedef struct BatchInfo {
  uintptr_t cmd_count;
  const struct CmdInfo *const *cmds;
  bool is_atomic;
} BatchInfo;

/**
 * A structure which represents a route. To avoid extra pointer mandgling, it has fields for all route types.
 * Depending on [`RouteType`], the struct stores:
 * * Only `route_type` is filled, if route is a simple route;
 * * `route_type`, `slot_id` and `slot_type`, if route is a Slot ID route;
 * * `route_type`, `slot_key` and `slot_type`, if route is a Slot key route;
 * * `route_type`, `hostname` and `port`, if route is a Address route;
 */
typedef struct RouteInfo {
  enum RouteType route_type;
  int32_t slot_id;
  /**
   * zero pointer is valid, means no slot key is given (`None`)
   */
  const char *slot_key;
  enum SlotType slot_type;
  /**
   * zero pointer is valid, means no hostname is given (`None`)
   */
  const char *hostname;
  int32_t port;
} RouteInfo;

typedef struct BatchOptionsInfo {
  bool retry_server_error;
  bool retry_connection_error;
  bool has_timeout;
  uint32_t timeout;
  const struct RouteInfo *route_info;
} BatchOptionsInfo;

/**
 * Configuration for exporting OpenTelemetry traces.
 *
 * - `endpoint`: The endpoint to which trace data will be exported. Expected format:
 *   - For gRPC: `grpc://host:port`
 *   - For HTTP: `http://host:port` or `https://host:port`
 *   - For file exporter: `file:///absolute/path/to/folder/file.json`
 * - `has_sample_percentage`: Whether sample percentage is specified
 * - `sample_percentage`: The percentage of requests to sample and create a span for, used to measure command duration. Only valid if has_sample_percentage is true.
 */
typedef struct OpenTelemetryTracesConfig {
  /**
   * The endpoint to which trace data will be exported, `null` if not specified.
   */
  const char *endpoint;
  /**
   * Whether sample percentage is specified
   */
  bool has_sample_percentage;
  /**
   * The percentage of requests to sample and create a span for, used to measure command duration. Only valid if has_sample_percentage is true.
   */
  uint32_t sample_percentage;
} OpenTelemetryTracesConfig;

/**
 * Configuration for exporting OpenTelemetry metrics.
 *
 * - `endpoint`: The endpoint to which metrics data will be exported. Expected format:
 *   - For gRPC: `grpc://host:port`
 *   - For HTTP: `http://host:port` or `https://host:port`
 *   - For file exporter: `file:///absolute/path/to/folder/file.json`
 */
typedef struct OpenTelemetryMetricsConfig {
  /**
   * The endpoint to which metrics data will be exported, `null` if not specified.
   */
  const char *endpoint;
} OpenTelemetryMetricsConfig;

/**
 * Configuration for OpenTelemetry integration in the Node.js client.
 *
 * This struct allows you to configure how telemetry data (traces and metrics) is exported to an OpenTelemetry collector.
 * - `traces`: Optional configuration for exporting trace data. If `None`, trace data will not be exported.
 * - `metrics`: Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
 * - `flush_interval_ms`: Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, a default value will be used.
 *
 * At least one of traces or metrics must be provided.
 */
typedef struct OpenTelemetryConfig {
  /**
   * Configuration for exporting trace data. Only valid if has_traces is true.
   */
  const struct OpenTelemetryTracesConfig *traces;
  /**
   * Configuration for exporting metrics data. Only valid if has_metrics is true.
   */
  const struct OpenTelemetryMetricsConfig *metrics;
  /**
   * Whether flush interval is specified
   */
  bool has_flush_interval_ms;
  /**
   * Interval in milliseconds between consecutive exports of telemetry data. Only valid if has_flush_interval_ms is true.
   */
  int64_t flush_interval_ms;
} OpenTelemetryConfig;

/**
 * Represents the result of a logging operation.
 *
 * This struct is used to communicate both success/failure status and relevant data
 * across the FFI boundary. For initialization operations, it contains the log level
 * that was set. For other operations, it primarily indicates success or failure.
 *
 * # Fields
 *
 * - `log_error`: A pointer to a null-terminated C string containing an error message.
 *   This field is `null` if the operation succeeded, or points to an error description
 *   if the operation failed.
 * - `level`: The log level value. For initialization operations, this contains the
 *   actual level that was set by the logger. For other operations, this field may
 *   be ignored when there's an error.
 *
 * # Safety
 *
 * The returned `LogResult` must be freed using [`free_log_result`] to avoid memory leaks.
 * This will properly deallocate both the struct itself and any error message it contains.
 *
 * - The `log_error` field must either be null or point to a valid, null-terminated C string
 * - The struct must be freed exactly once using [`free_log_result`]
 * - The error string must not be accessed after the struct has been freed
 * - The `level` field is only meaningful when `log_error` is null (success case)
 */
typedef struct LogResult {
  char *log_error;
  enum Level level;
} LogResult;

/**
 * Store a Lua script in the script cache and return its SHA1 hash.
 *
 * # Parameters
 *
 * * `script_bytes`: Pointer to the script bytes.
 * * `script_len`: Length of the script in bytes.
 *
 * # Returns
 *
 * A C string containing the SHA1 hash of the script. The caller is responsible for freeing this memory.
 * We can free the memory using [`drop_script`].
 *
 * # Safety
 *
 * * `script_bytes` must point to `script_len` consecutive properly initialized bytes.
 * * The returned buffer must be freed by the caller using [`free_script_hash_buffer`].
 */
struct ScriptHashBuffer *store_script(const uint8_t *script_bytes,
                                      uintptr_t script_len);

/**
 * Free a `ScriptHashBuffer` obtained from [`store_script`].
 *
 * # Parameters
 *
 * * `buffer`: Pointer to the `ScriptHashBuffer`.
 *
 * # Safety
 *
 * * `buffer` must be a pointer returned from [`store_script`].
 */
void free_script_hash_buffer(struct ScriptHashBuffer *buffer);

/**
 * Remove a script from the script cache.
 *
 * Returns a null pointer if it succeeds and a C string error message if it fails.
 *
 * # Parameters
 *
 * * `hash`: The SHA1 hash of the script to remove as a byte array.
 * * `len`: The length of `hash`.
 *
 * # Safety
 *
 * * `hash` must be a valid pointer to a UTF-8 string obtained from [`store_script`].
 */
char *drop_script(uint8_t *hash, uintptr_t len);

/**
 * Free an error message from a failed drop_script call.
 *
 * # Parameters
 *
 * * `error`: The error to free.
 *
 * # Safety
 *
 * * `error` must be an error returned by [`drop_script`].
 */
void free_drop_script_error(char *error);

/**
 *
 * This function frees both the `CommandResult` itself and its internal components if preset.
 *
 * # Behavior
 *
 * - If the provided `command_result_ptr` is null, the function returns immediately.
 * - If either `response` or `command_error` is non-null, they are deallocated accordingly.
 *
 * # Safety
 *
 * * `free_command_result` must only be called **once** for any given `CommandResult`.
 *   Calling it multiple times is undefined behavior and may lead to double-free errors.
 * * The `command_result_ptr` must be a valid pointer returned by a function that creates a `CommandResult`.
 * * The memory behind `command_result_ptr` must remain valid until this function is called.
 * * If `command_error.command_error_message` is non-null, it must be a valid pointer obtained from Rust
 *   and must outlive the `CommandError` itself.
 */
void free_command_result(struct CommandResult *command_result_ptr);

/**
 * Creates a new `ClientAdapter` with a new `GlideClient` configured using a Protobuf `ConnectionRequest`.
 *
 * The returned `ConnectionResponse` will only be freed by calling [`free_connection_response`].
 *
 * `connection_request_bytes` is an array of bytes that will be parsed into a Protobuf `ConnectionRequest` object.
 * `connection_request_len` is the number of bytes in `connection_request_bytes`.
 * `success_callback` is the callback that will be called when a command succeeds.
 * `failure_callback` is the callback that will be called when a command fails.
 *
 * # Safety
 *
 * * `connection_request_bytes` must point to `connection_request_len` consecutive properly initialized bytes. It must be a well-formed Protobuf `ConnectionRequest` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `connection_request_len` must not be greater than the length of the connection request bytes array. It must also not be greater than the max value of a signed pointer-sized integer.
 * * The `conn_ptr` pointer in the returned `ConnectionResponse` must live while the client is open/active and must be explicitly freed by calling [`close_client``].
 * * The `connection_error_message` pointer in the returned `ConnectionResponse` must live until the returned `ConnectionResponse` pointer is passed to [`free_connection_response``].
 * * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
 */
const struct ConnectionResponse *create_client(const uint8_t *connection_request_bytes,
                                               uintptr_t connection_request_len,
                                               const struct ClientType *client_type,
                                               PubSubCallback pubsub_callback);

/**
 * Closes the given `GlideClient`, freeing it from the heap.
 *
 * `client_adapter_ptr` is a pointer to a valid `GlideClient` returned in the `ConnectionResponse` from [`create_client`].
 *
 * # Panics
 *
 * This function panics when called with a null `client_adapter_ptr`.
 *
 * # Safety
 *
 * * `close_client` can only be called once per client. Calling it twice is undefined behavior, since the address will be freed twice.
 * * `close_client` must be called after `free_connection_response` has been called to avoid creating a dangling pointer in the `ConnectionResponse`.
 * * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
 * * `client_adapter_ptr` must be valid until `close_client` is called.
 */
void close_client(const void *client_adapter_ptr);

/**
 * Deallocates a `ConnectionResponse`.
 *
 * This function also frees the contained error. If the contained error is a null pointer, the function returns and only the `ConnectionResponse` is freed.
 *
 * # Panics
 *
 * This function panics when called with a null `ConnectionResponse` pointer.
 *
 * # Safety
 *
 * * `free_connection_response` can only be called once per `ConnectionResponse`. Calling it twice is undefined behavior, since the address will be freed twice.
 * * `connection_response_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
 * * `connection_response_ptr` must be valid until `free_connection_response` is called.
 * * The contained `connection_error_message` must be obtained from the `ConnectionResponse` returned from [`create_client`].
 * * The contained `connection_error_message` must be valid until `free_connection_response` is called and it must outlive the `ConnectionResponse` that contains it.
 */
void free_connection_response(struct ConnectionResponse *connection_response_ptr);

/**
 * Provides the string mapping for the ResponseType enum.
 *
 * Important: the returned pointer is a pointer to a constant string and should not be freed.
 */
const char *get_response_type_string(enum ResponseType response_type);

/**
 * Deallocates a `CommandResponse`.
 *
 * This function also frees the contained string_value and array_value. If the string_value and array_value are null pointers, the function returns and only the `CommandResponse` is freed.
 *
 * # Safety
 *
 * * `free_command_response` can only be called once per `CommandResponse`. Calling it twice is undefined behavior, since the address will be freed twice.
 * * `command_response_ptr` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
 * * `command_response_ptr` must be valid until `free_command_response` is called.
 */
void free_command_response(struct CommandResponse *command_response_ptr);

/**
 * Executes a command.
 *
 * # Safety
 *
 * * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
 * * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`]. See the safety documentation of [`std::sync::Arc::from_raw`].
 * * `request_id` must be a request ID from the foreign language and must be valid until either `success_callback` or `failure_callback` is finished.
 * * `args` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `args_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `arg_count` the number of elements in `args` and `args_len`. It must also not be greater than the max value of a signed pointer-sized integer.
 * * `arg_count` must be 0 if `args` and `args_len` are null.
 * * `args` and `args_len` must either be both null or be both not null.
 * * `route_bytes` is an optional array of bytes that will be parsed into a Protobuf `Routes` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `route_bytes_len` is the number of bytes in `route_bytes`. It must also not be greater than the max value of a signed pointer-sized integer.
 * * `route_bytes_len` must be 0 if `route_bytes` is null.
 * * `span_ptr` is a valid pointer to [`Arc<GlideSpan>`], a span created by [`create_otel_span`] or `0`. The span must be valid until the command is finished.
 * * This function should only be called should with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
 */
struct CommandResult *command(const void *client_adapter_ptr,
                              uintptr_t request_id,
                              enum RequestType command_type,
                              unsigned long arg_count,
                              const uintptr_t *args,
                              const unsigned long *args_len,
                              const uint8_t *route_bytes,
                              uintptr_t route_bytes_len,
                              uint64_t span_ptr);

/**
 * Allows the client to request a cluster scan command to be executed.
 *
 * `client_adapter_ptr` is a pointer to a valid `GlideClusterClient` returned in the `ConnectionResponse` from [`create_client`].
 * `request_id` is a unique identifier for a valid payload buffer which is created in the client.
 * `cursor` is a cursor string.
 * `arg_count` keeps track of how many option arguments are passed in the client.
 * `args` is a pointer to C string representation of the string args.
 * `args_len` is a pointer to the lengths of the C string representation of the string args.
 * `success_callback` is the callback that will be called when a command succeeds.
 * `failure_callback` is the callback that will be called when a command fails.
 *
 * # Safety
 *
 * * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
 * * `client_adapter_ptr` must be valid until `close_client` is called.
 * * `request_id` must be valid until it is passed in a call to [`free_command_response`].
 * * `cursor` must not be null. It must point to a valid C string ([`CStr`]). See the safety documentation of [`CStr::from_ptr`].
 * * `cursor` must remain valid until the end of this call. The caller is responsible for freeing the memory allocated for this string.
 * * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
 */
struct CommandResult *request_cluster_scan(const void *client_adapter_ptr,
                                           uintptr_t request_id,
                                           const char *cursor,
                                           unsigned long arg_count,
                                           const uintptr_t *args,
                                           const unsigned long *args_len);

/**
 * Remove a cluster scan cursor from the container.
 *
 * `cursor_id` is the cursor ID returned by a previous cluster scan operation.
 *
 * # Safety
 * * `cursor_id` must point to a valid C string.
 */
void remove_cluster_scan_cursor(const char *cursor_id);

/**
 * Allows the client to request an update to the connection password.
 *
 * `client_adapter_ptr` is a pointer to a valid `GlideClusterClient` returned in the `ConnectionResponse` from [`create_client`].
 * `request_id` is a unique identifier for a valid payload buffer which is created in the client.
 * `password` is a pointer to C string representation of the password.
 * `immediate_auth` is a boolean flag to indicate if the password should be updated immediately.
 * `success_callback` is the callback that will be called when a command succeeds.
 * `failure_callback` is the callback that will be called when a command fails.
 *
 * # Safety
 *
 * * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
 * * `client_adapter_ptr` must be valid until `close_client` is called.
 * * `request_id` must be valid until it is passed in a call to [`free_command_response`].
 * * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
 */
struct CommandResult *update_connection_password(const void *client_adapter_ptr,
                                                 uintptr_t request_id,
                                                 const char *password,
                                                 bool immediate_auth);

/**
 * Executes a Lua script.
 *
 * # Parameters
 *
 * * `client_adapter_ptr`: Pointer to a valid `GlideClusterClient` returned from [`create_client`].
 * * `request_id`: Unique identifier for a valid payload buffer created in the calling language.
 * * `hash`: SHA1 hash of the script for script caching.
 * * `keys_count`: Number of keys in the keys array.
 * * `keys`: Array of keys used by the script.
 * * `keys_len`: Array of lengths for each key.
 * * `args_count`: Number of arguments in the args array.
 * * `args`: Array of arguments to pass to the script.
 * * `args_len`: Array of lengths for each argument.
 * * `route_bytes`: Optional array of bytes for routing information.
 * * `route_bytes_len`: Length of the route_bytes array.
 *
 * # Safety
 *
 * * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
 * * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`].
 * * `request_id` must be valid until either `success_callback` or `failure_callback` is finished.
 * * `hash` must be a valid null-terminated C string.
 * * `keys` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `keys_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `keys_count` must be 0 if `keys` and `keys_len` are null.
 * * `keys` and `keys_len` must either be both null or be both not null.
 * * `args` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `args_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `args_count` must be 0 if `args` and `args_len` are null.
 * * `args` and `args_len` must either be both null or be both not null.
 * * `route_bytes` is an optional array of bytes that will be parsed into a Protobuf `Routes` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
 * * `route_bytes_len` is the number of bytes in `route_bytes`. It must also not be greater than the max value of a signed pointer-sized integer.
 * * `route_bytes_len` must be 0 if `route_bytes` is null.
 * * This function should only be called with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
 */
struct CommandResult *invoke_script(const void *client_adapter_ptr,
                                    uintptr_t request_id,
                                    const char *hash,
                                    unsigned long keys_count,
                                    const uintptr_t *keys,
                                    const unsigned long *keys_len,
                                    unsigned long args_count,
                                    const uintptr_t *args,
                                    const unsigned long *args_len,
                                    const uint8_t *route_bytes,
                                    uintptr_t route_bytes_len);

/**
 * Execute a batch.
 *
 * # Safety
 * * `client_ptr` must not be `null`.
 * * `client_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`]. See the safety documentation of [`Box::from_raw`].
 * * This function should only be called should with a pointer created by [`create_client`], before [`close_client`] was called with the pointer.
 * * `batch_ptr` must not be `null`.
 * * `batch_ptr` must be able to be safely casted to a valid [`BatchInfo`]. See the safety documentation of [`create_pipeline`].
 * * `options_ptr` could be `null`, but if it is not `null`, it must be a valid [`BatchOptionsInfo`] pointer. See the safety documentation of [`get_pipeline_options`].
 */
struct CommandResult *batch(const void *client_ptr,
                            uintptr_t callback_index,
                            const struct BatchInfo *batch_ptr,
                            bool raise_on_error,
                            const struct BatchOptionsInfo *options_ptr,
                            uint64_t span_ptr);

/**
 * Creates an OpenTelemetry span with the given name and returns a pointer to the span as u64.
 *
 */
uint64_t create_otel_span(enum RequestType request_type);

/**
 * Creates an OpenTelemetry span with a fixed name "batch" and returns a pointer to the span as u64.
 *
 */
uint64_t create_batch_otel_span(void);

/**
 * Creates an OpenTelemetry batch span with a parent span and returns a pointer to the span as u64.
 * This function creates a child span with the name "Batch" under the provided parent span.
 * Returns 0 on failure.
 *
 * # Parameters
 * * `parent_span_ptr`: A u64 pointer to the parent span created by create_otel_span, create_named_otel_span, or create_batch_otel_span
 *
 * # Returns
 * * A u64 pointer to the created child batch span, or 0 if creation fails
 *
 * # Safety
 * * `parent_span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`], [`create_named_otel_span`], or [`create_batch_otel_span`], or 0.
 * * If `parent_span_ptr` is 0 or invalid, the function will create an independent batch span as fallback.
 */
uint64_t create_batch_otel_span_with_parent(uint64_t parent_span_ptr);

/**
 * Creates an OpenTelemetry span with a custom name and returns a pointer to the span as u64.
 * This function is intended for creating parent spans that can be used with create_otel_span_with_parent.
 * Returns 0 on failure.
 *
 * # Parameters
 * * `span_name`: A null-terminated C string containing the name for the span
 *
 * # Returns
 * * A u64 pointer to the created span, or 0 if creation fails
 *
 * # Safety
 * * `span_name` must be a valid pointer to a null-terminated C string
 * * The string must be valid UTF-8
 * * The caller is responsible for eventually calling drop_otel_span with the returned pointer
 */
uint64_t create_named_otel_span(const char *span_name);

/**
 * Creates an OpenTelemetry span with the given request type as a child of the provided parent span.
 * Returns a pointer to the child span as u64, or 0 on failure.
 *
 * # Parameters
 * * `request_type`: The type of request to create a span for
 * * `parent_span_ptr`: A pointer to the parent span (created by create_otel_span or create_named_otel_span)
 *
 * # Returns
 * * A u64 pointer to the created child span, or 0 if creation fails
 *
 * # Safety
 * * `parent_span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`], [`create_named_otel_span`], or [`create_batch_otel_span`], or 0.
 * * If `parent_span_ptr` is 0 or invalid, the function will create an independent span as fallback.
 */
uint64_t create_otel_span_with_parent(enum RequestType request_type,
                                      uint64_t parent_span_ptr);

/**
 * Drops an OpenTelemetry span given its pointer as u64.
 *
 * # Safety
 * * `span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`] or `0`.
 */
void drop_otel_span(uint64_t span_ptr);

/**
 * Initializes OpenTelemetry with the given configuration.
 *
 * # Safety
 * * `open_telemetry_config` and its underlying traces and metrics pointers must be valid until the function returns.
 */
const char *init_open_telemetry(const struct OpenTelemetryConfig *open_telemetry_config);

/**
 * Frees a C string.
 *
 * # Safety
 * * `s` must be a valid pointer to a C string or `null`.
 */
void free_c_string(char *s);

/**
 * Logs a message using the logger backend.
 *
 * # Parameters
 *
 * * `level` - The severity level of the current message (e.g., Error, Warn, Info).
 * * `identifier` - A pointer to a null-terminated C string identifying the source of the log message.
 * * `message` - A pointer to a null-terminated C string containing the actual log message.
 *
 * # Safety
 *
 *  The returned pointer must be freed using [`free_log_result`].
 *
 * * `identifier` must be a valid, non-null pointer to a null-terminated UTF-8 encoded C string.
 * * `message` must be a valid, non-null pointer to a null-terminated UTF-8 encoded C string.
 *
 * # Note
 *
 * The caller (Python Sync wrapper, Go wrapper, etc.) is responsible for filtering log messages according to the logger's current log level.
 * This function will log any message it receives.
 */
struct LogResult *glide_log(enum Level level,
                            const char *identifier,
                            const char *message);

/**
 * Initializes the logger with the provided log level and optional log file path.
 *
 * Success is indicated by a `LogResult` with a null `log_error` field and the actual
 * log level set in the `level` field. Failure is indicated by a `LogResult` with a non-null
 * `log_error` field containing an error message, and the `level` field should be ignored.
 *
 * # Parameters
 *
 * * `level` - A pointer to a `Level` enum value that sets the maximum log level. If null, a WARN level will be used.
 * * `file_name` - A pointer to a null-terminated C string representing the desired log file path.
 *
 * # Returns
 *
 * A pointer to a `LogResult` struct containing either:
 * - Success: `log_error` is null, `level` contains the actual log level that was set
 * - Error: `log_error` contains the error message, `level` should be ignored
 *
 *
 * # Safety
 *
 * The returned pointer must be freed using [`free_log_result`].
 *
 * * `level` may be null. If not null, it must point to a valid instance of the `Level` enum.
 * * `file_name` may be null. If not null, it must point to a valid, null-terminated C string.
 *   If the string contains invalid UTF-8, an error will be returned instead of panicking.
 */
struct LogResult *init(const enum Level *level,
                       const char *file_name);

/**
 * Frees a log result.
 *
 * This function deallocates a `LogResult` struct and any error message it contains.
 *
 * # Parameters
 *
 * * `result_ptr` - A pointer to the `LogResult` to free, or null.
 *
 * # Safety
 *
 * * `result_ptr` must be a valid pointer to a `LogResult` returned by [`glide_log`] or [`init`], or null.
 * * This function must be called exactly once for each `LogResult`.
 */
void free_log_result(struct LogResult *result_ptr);
