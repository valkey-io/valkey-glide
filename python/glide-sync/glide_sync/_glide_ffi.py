# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import platform
from pathlib import Path

from cffi import FFI


def find_libglide_ffi(lib_dir: Path) -> Path:
    """
    Searches for the correct shared library file depending on the OS.
    """
    possible_names = {
        "Linux": "libglide_ffi.so",
        "Darwin": "libglide_ffi.dylib",
        "Windows": "glide_ffi.dll",
    }

    system = platform.system()
    lib_name = possible_names.get(system)
    if not lib_name:
        raise RuntimeError(f"Unsupported platform: {system}")

    lib_path = lib_dir / lib_name
    if not lib_path.exists():
        raise FileNotFoundError(f"Could not find {lib_name} in {lib_dir}")

    return lib_path


CURR_DIR = Path(__file__).resolve().parent
LIB_FILE = find_libglide_ffi(CURR_DIR)


class _GlideFFI:
    """
    Singleton class that manages the Glide FFI library.
    Provides access to the FFI instance and loaded library.
    """

    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(_GlideFFI, cls).__new__(cls)
            cls._instance._init_ffi()
        return cls._instance

    def _init_ffi(self):
        self._ffi = FFI()

        self._ffi.cdef(
            """
            // ============== SCRIPT MANAGEMENT ==============
            typedef struct {
                uint8_t* ptr;
                size_t len;
                size_t capacity;
            } ScriptHashBuffer;

            ScriptHashBuffer* store_script(const uint8_t* script_bytes, size_t script_len);
            void free_script_hash_buffer(ScriptHashBuffer* buffer);
            char* drop_script(uint8_t* hash, size_t len);
            void free_drop_script_error(char* error);

            // ============== COMMAND EXECUTION ==============
            typedef enum {
                Null = 0,
                Int = 1,
                Float = 2,
                Bool = 3,
                String = 4,
                Array = 5,
                Map = 6,
                Sets = 7,
                Ok = 8,
                Error = 9
            } ResponseType;

            typedef struct CommandResponse {
                int response_type;
                long int_value;
                double float_value;
                bool bool_value;
                char* string_value;
                long string_value_len;
                struct CommandResponse* array_value;
                long array_value_len;
                struct CommandResponse* map_key;
                struct CommandResponse* map_value;
                struct CommandResponse* sets_value;
                long sets_value_len;
            } CommandResponse;

            typedef struct {
                const char* command_error_message;
                int command_error_type;
            } CommandError;

            typedef struct {
                CommandResponse* response;
                CommandError* command_error;
            } CommandResult;

            const char* get_response_type_string(int response_type);
            void free_command_response(CommandResponse* command_response_ptr);
            void free_command_result(CommandResult* command_result_ptr);

            CommandResult* command(
                const void* client_adapter_ptr,
                uintptr_t channel,
                int command_type,
                unsigned long arg_count,
                const size_t *args,
                const unsigned long* args_len,
                const unsigned char* route_bytes,
                size_t route_bytes_len,
                uint64_t span_ptr
            );

            CommandResult* invoke_script(
                const void* client_adapter_ptr,
                uintptr_t request_id,
                const char* hash,
                unsigned long keys_count,
                const size_t* keys,
                const unsigned long* keys_len,
                unsigned long args_count,
                const size_t* args,
                const unsigned long* args_len,
                const unsigned char* route_bytes,
                size_t route_bytes_len
            );

            CommandResult* update_connection_password(
                const void* client_adapter_ptr,
                uintptr_t request_id,
                const char* password,
                bool immediate_authentication
            );

            // ============== CLIENT MANAGEMENT ==============
            typedef enum {
                Async = 0,
                Sync = 1
            } ClientTypeEnum;

            typedef void (*SuccessCallback)(uintptr_t index_ptr, const CommandResponse* message);
            typedef void (*FailureCallback)(uintptr_t index_ptr, const char* error_message, int error_type);

            typedef void (*PubSubCallback)(
                uintptr_t client_ptr,
                int kind,
                const uint8_t* message,
                int64_t message_len,
                const uint8_t* channel,
                int64_t channel_len,
                const uint8_t* pattern,
                int64_t pattern_len
            );

            typedef struct {
                int _type;
                union {
                    struct {
                        SuccessCallback success_callback;
                        FailureCallback failure_callback;
                    } async_client;
                };
            } ClientType;

            typedef struct {
                const void* conn_ptr;
                const char* connection_error_message;
            } ConnectionResponse;

            const ConnectionResponse* create_client(
                const uint8_t* connection_request_bytes,
                size_t connection_request_len,
                const ClientType* client_type,
                PubSubCallback pubsub_callback
            );
            void close_client(const void* client_adapter_ptr);
            void free_connection_response(ConnectionResponse* connection_response_ptr);

            // ============== BATCH EXECUTION ==============
            typedef enum {
                AllNodes = 0,
                AllPrimaries,
                Random,
                SlotId,
                SlotKey,
                ByAddress
            } RouteType;

            typedef enum {
                Primary = 0,
                Replica
            } SlotType;

            typedef struct {
                int route_type;
                int slot_id;
                const char* slot_key;
                int slot_type;
                const char* hostname;
                int port;
            } RouteInfo;

            typedef struct {
                int request_type;
                const uint8_t** args;
                size_t arg_count;
                const size_t* args_len;
            } CmdInfo;

            typedef struct {
                size_t cmd_count;
                const CmdInfo** cmds;
                bool is_atomic;
            } BatchInfo;

            typedef struct {
                bool retry_server_error;
                bool retry_connection_error;
                bool has_timeout;
                uint32_t timeout;
                const RouteInfo* route_info;
            } BatchOptionsInfo;

            CommandResult* batch(
                const void* client_ptr,
                uintptr_t callback_index,
                const BatchInfo* batch_ptr,
                bool raise_on_error,
                const BatchOptionsInfo* options_ptr,
                uint64_t span_ptr
            );

            // ============== CLUSTER SCAN ==============
            CommandResult* request_cluster_scan(
                const void* client_adapter_ptr,
                uintptr_t request_id,
                const char* cursor,
                unsigned long arg_count,
                const size_t* args,
                const unsigned long* args_len
            );

            void remove_cluster_scan_cursor(const char* cursor_id);

            // ============== LOGGING ==============
            typedef enum {
                ERROR = 0,
                WARN = 1,
                INFO = 2,
                DEBUG = 3,
                TRACE = 4,
                OFF = 5
            } Level;

            typedef struct {
                char* log_error;
                int level;
            } LogResult;

            LogResult* glide_log(int level, const char* identifier, const char* message);
            LogResult* init(const Level* level, const char* file_name);
            void free_log_result(LogResult* result_ptr);

            // ============== OPENTELEMETRY ==============
            typedef struct {
                const char* endpoint;
                bool has_sample_percentage;
                uint32_t sample_percentage;
            } OpenTelemetryTracesConfig;

            typedef struct {
                const char* endpoint;
            } OpenTelemetryMetricsConfig;

            typedef struct {
                const OpenTelemetryTracesConfig* traces;
                const OpenTelemetryMetricsConfig* metrics;
                bool has_flush_interval_ms;
                int64_t flush_interval_ms;
            } OpenTelemetryConfig;

            uint64_t create_otel_span(int request_type);
            uint64_t create_batch_otel_span();
            uint64_t create_named_otel_span(const char* span_name);
            uint64_t create_otel_span_with_parent(int request_type, uint64_t parent_span_ptr);
            void drop_otel_span(uint64_t span_ptr);
            const char* init_open_telemetry(const OpenTelemetryConfig* open_telemetry_config);

            // ============== UTILITY FUNCTIONS ==============
            void free_c_string(char* s);
            """
        )

        # Load the shared library
        self._lib = self._ffi.dlopen(str(LIB_FILE.resolve()))

    @property
    def ffi(self):
        """Access to the FFI instance for creating C types and buffers."""
        return self._ffi

    @property
    def lib(self):
        """Access to the loaded library for calling functions."""
        return self._lib
