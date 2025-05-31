#include <glide/callback.h>
#include <glide/client.h>
#include <glide/future.h>
#include <glide/glide_base.h>
#include <glide/helper.h>
#include <sys/types.h>
#include <unistd.h>

#include <cstddef>
#include <cstdint>
#include <optional>

namespace glide {

/**
 * Constructs a Client with the provided configuration.
 *
 * @param config The configuration object to use for this client instance.
 */
Client::Client(const Config &config) : config_(config) {}

/**
 * Establishes a connection to the server using the client's configuration.
 *
 * @return true if the connection was successfully established, false otherwise.
 */
bool Client::connect() {
  std::optional<std::vector<uint8_t>> serialized_conf = config_.serialize();
  if (!serialized_conf) {
    return false;
  }
  connection_ = core::create_client(serialized_conf.value().data(),
                                    serialized_conf.value().size(), on_success,
                                    on_failure);
  return connection_->conn_ptr != nullptr;
}

/**
 * Sets a key-value pair in the Redis database with string values.
 *
 * @param key The key to set.
 * @param value The string value to associate with the key.
 * @return A Future containing the status of the operation.
 */
Future<absl::Status> Client::set(const std::string &key,
                                 const std::string &value) {
  Future<absl::Status> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);
  std::vector<std::string> args = {key, value};
  exec_command(core::RequestType::Set, args, future_ptr);
  return future;
}

/**
 * Sets a key-value pair in the Redis database with binary values.
 *
 * @param key The key to set.
 * @param value The binary value to associate with the key.
 * @return A Future containing the status of the operation.
 */
Future<absl::Status> Client::set(const std::string &key,
                                 const std::vector<std::byte> &value) {
  Future<absl::Status> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);

  std::vector<uintptr_t> args = {reinterpret_cast<uintptr_t>(key.data()),
                                 reinterpret_cast<uintptr_t>(value.data())};
  std::vector<unsigned long> args_len = {
      static_cast<unsigned long>(key.size()),
      static_cast<unsigned long>(value.size())};
  exec_command_b(core::RequestType::Set, args, args_len, future_ptr);
  return future;
}

/**
 * Sets multiple field-value pairs in a hash stored at the given key with string
 * values.
 *
 * @param key The key of the hash.
 * @param values A map of field-value pairs to set in the hash.
 * @return A Future containing the status of the operation.
 */
Future<absl::Status> Client::hset(
    const std::string &key, const std::map<std::string, std::string> &values) {
  Future<absl::Status> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);
  std::vector<std::string> args = {key};
  for (const auto &pair : values) {
    args.push_back(pair.first);
    args.push_back(pair.second);
  }
  exec_command(core::RequestType::HSet, args, future_ptr);
  return future;
}

/**
 * Sets multiple field-value pairs in a hash stored at the given key with binary
 * values.
 *
 * @param key The key of the hash.
 * @param values A map of field-value pairs with binary values to set in the
 * hash.
 * @return A Future containing the status of the operation.
 */
Future<absl::Status> Client::hset(
    const std::string &key,
    const std::map<std::string, std::vector<std::byte>> &values) {
  Future<absl::Status> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);
  std::vector<uintptr_t> args = {reinterpret_cast<uintptr_t>(key.data())};
  std::vector<unsigned long> args_len;
  args_len.reserve(values.size() + 1);
  args_len.push_back(key.size());
  for (const auto &pair : values) {
    args.push_back(reinterpret_cast<uintptr_t>(pair.first.data()));
    args.push_back(reinterpret_cast<uintptr_t>(pair.second.data()));
    args_len.push_back(pair.first.size());
    args_len.push_back(pair.second.size());
  }
  exec_command_b(core::RequestType::HSet, args, args_len, future_ptr);
  return future;
}

/**
 * Executes a Redis command with string arguments.
 *
 * @param type The type of Redis command to execute.
 * @param args Vector of string arguments for the command.
 * @param channel_ptr Pointer to the channel for handling the response.
 */
void Client::exec_command(core::RequestType type,
                          std::vector<std::string> &args,
                          uintptr_t channel_ptr) {
  std::vector<uintptr_t> cmd_args;
  cmd_args.reserve(args.size());
  std::vector<unsigned long> cmd_args_len;
  cmd_args_len.reserve(args.size());
  for (auto &arg : args) {
    cmd_args.push_back(reinterpret_cast<uintptr_t>(arg.data()));
    cmd_args_len.push_back(static_cast<unsigned long>(arg.size()));
  };

  // Execute command.
  core::command(connection_->conn_ptr, channel_ptr, type, cmd_args.size(),
                cmd_args.data(), cmd_args_len.data(), nullptr, 0);
}

/**
 * Executes a Redis command with binary arguments.
 *
 * @param type The type of Redis command to execute.
 * @param args Vector of pointers to binary argument data.
 * @param args_len Vector containing the length of each argument.
 * @param channel_ptr Pointer to the channel for handling the response.
 */
void Client::exec_command_b(core::RequestType type,
                            std::vector<uintptr_t> &args,
                            std::vector<unsigned long> &args_len,
                            uintptr_t channel_ptr) {
  core::command(connection_->conn_ptr, channel_ptr, type, args.size(),
                args.data(), args_len.data(), nullptr, 0);
}

/**
 * Destructor for the Client class.
 * Properly closes the connection and frees allocated resources.
 */
Client::~Client() {
  if (connection_ && connection_->conn_ptr) {
    core::close_client(connection_->conn_ptr);
    core::free_connection_response(
        const_cast<core::ConnectionResponse *>(connection_));
  }
}

}  // namespace glide
