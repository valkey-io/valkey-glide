#include <glide/callback.h>
#include <glide/client.h>
#include <glide/future.h>
#include <glide/glide_base.h>
#include <glide/helper.h>

#include <cstdint>
#include <optional>

namespace glide {

/**
 * Constructs a Client with a const configuration.
 */
Client::Client(const Config &config) : config_(config) {}

/**
 * Connects the client using the serialized configuration.
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
 * Sets a key-value pair in the client's configuration.
 */
Future<absl::Status> Client::set(const std::string &key,
                                 const std::string &value) {
  std::vector<std::string> args = {key, value};
  Future<absl::Status> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);
  exec_command(core::RequestType::Set, args, future_ptr);
  return future;
}

/**
 * Retrieves the value associated with the given key from the client's
 * configuration.
 */
Future<absl::StatusOr<std::string>> Client::get(const std::string &key) {
  std::vector<std::string> args = {key};

  Future<absl::StatusOr<std::string>> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);
  exec_command(core::RequestType::Get, args, future_ptr);
  return future;
}

/**
 * Retrieves the value associated with the given key from the client's
 * configuration.
 */
Future<absl::StatusOr<std::string>> Client::getdel(const std::string &key) {
  std::vector<std::string> args = {key};
  Future<absl::StatusOr<std::string>> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);
  exec_command(core::RequestType::GetDel, args, future_ptr);
  return future;
}

/**
 * Sets multiple field-value pairs in a hash stored at the given key.
 */
Future<absl::Status> Client::hset(
    const std::string &key,
    const std::map<std::string, std::string> &field_values) {
  std::vector<std::string> args = {key};
  for (const auto &pair : field_values) {
    args.push_back(pair.first);
    args.push_back(pair.second);
  }
  Future<absl::Status> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);
  exec_command(core::RequestType::HSet, args, future_ptr);
  return future;
}

/**
 * Retrieves the value associated with a field in a hash stored at the given
 * key.
 */
Future<absl::StatusOr<std::string>> Client::hget(const std::string &key,
                                                 const std::string &field) {
  std::vector<std::string> args = {key, field};
  Future<absl::StatusOr<std::string>> future;
  auto future_ptr = reinterpret_cast<uintptr_t>(&future);
  exec_command(core::RequestType::HGet, args, future_ptr);
  return future;
}

/**
 * Executes a command with the given request type and arguments.
 */
void Client::exec_command(core::RequestType type,
                          std::vector<std::string> &args,
                          uintptr_t channel_ptr) {
  // Prepare arguments.
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
 * Destructor for the Client class.
 */
Client::~Client() {
  if (connection_ && connection_->conn_ptr) {
    core::close_client(connection_->conn_ptr);
    core::free_connection_response(
        const_cast<core::ConnectionResponse *>(connection_));
  }
}

}  // namespace glide
