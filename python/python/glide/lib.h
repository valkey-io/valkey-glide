/* Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

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
} ResponseType;

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
 * The struct represents the response of the command.
 *
 * It will have one of the value populated depending on the return type of the command.
 *
 * The struct is freed by the external caller by using `free_command_response` to avoid memory leaks.
 * TODO: Add a type enum to validate what type of response is being sent in the CommandResponse.
 */
typedef struct CommandResponse {
  enum ResponseType response_type;
  long int_value;
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
   * The map is transformed into an array of (map_key: CommandResponse, map_value: CommandResponse) and passed to Go.
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
 * * The `conn_ptr` pointer in the returned `ConnectionResponse` must live while the client is open/active and must be explicitly freed by calling [`close_client`].
 * * The `connection_error_message` pointer in the returned `ConnectionResponse` must live until the returned `ConnectionResponse` pointer is passed to [`free_connection_response`].
 * * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
 */
const struct ConnectionResponse *create_client(void);

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
 */
char *get_response_type_string(enum ResponseType response_type);

/**
 * Deallocates a string generated via get_response_type_string.
 *
 * # Safety
 * free_response_type_string can be called only once per response_string.
 */
void free_response_type_string(char *response_string);

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
 * Frees the error_message received on a command failure.
 * TODO: Add a test case to check for memory leak.
 *
 * # Panics
 *
 * This functions panics when called with a null `c_char` pointer.
 *
 * # Safety
 *
 * `free_error_message` can only be called once per `error_message`. Calling it twice is undefined
 * behavior, since the address will be freed twice.
 */
void free_error_message(char *error_message);

/**
 * Executes a command.
 *
 * # Safety
 *
 * * TODO: finish safety section.
 */
struct CommandResponse *command(const void *client_adapter_ptr,
                                uintptr_t channel,
                                enum RequestType command_type,
                                unsigned long arg_count,
                                const uintptr_t *args,
                                const unsigned long *args_len);
