#include <glide/client.h>

#include "glide/glide_base.h"

namespace glide {

/**
 * Constructs a Client with a const configuration.
 */
Client::Client(const glide::Config& config) : config_(config) {}

/**
 * Connects the client using the serialized configuration.
 */
bool Client::connect() {
  std::optional<std::vector<uint8_t>> serialized_conf = config_.serialize();
  if (!serialized_conf) {
    return false;
  }
  connection_ = glide::create_client(serialized_conf.value().data(),
                                     serialized_conf.value().size(), on_success,
                                     on_failure);
  return connection_->conn_ptr != nullptr;
}

/**
 * Sets a key-value pair in the client's configuration.
 */
bool Client::set(const std::string& key, const std::string& value) {
  return command_.set(*this, key, value);
}

/**
 * Retrieves the value associated with the given key from the client's
 * configuration.
 */
std::string Client::get(const std::string& key) {
  return command_.get(*this, key);
}

/**
 * Executes a command with the given request type and arguments.
 */
void Client::exec_command(glide::RequestType type,
                          std::vector<std::string>& args,
                          CommandResponseData& channel) {
  // Prepare arguments.
  // TODO: set default size based on args. (reserve space).
  std::vector<uintptr_t> cmd_args;
  std::vector<unsigned long> cmd_args_len;
  for (auto& arg : args) {
    cmd_args.push_back(reinterpret_cast<uintptr_t>(arg.data()));
    cmd_args_len.push_back(static_cast<unsigned long>(arg.size()));
  };

  // Execute command.
  uintptr_t channel_ptr = reinterpret_cast<uintptr_t>(&channel);
  glide::command(connection_->conn_ptr, channel_ptr, type, cmd_args.size(),
          cmd_args.data(), cmd_args_len.data());
}

/**
 * Destructor for the Client class.
 */
Client::~Client() {
  if (connection_ && connection_->conn_ptr) {
    glide::close_client(connection_->conn_ptr);
    glide::free_connection_response(
        const_cast<ConnectionResponse*>(connection_));
  }
}

}  // namespace glide
