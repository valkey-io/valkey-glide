#ifndef CLIENT_HPP_
#define CLIENT_HPP_

#include <absl/status/status.h>
#include <absl/status/statusor.h>

#include <cstdint>
#include <map>
#include <string>
#include <vector>

#include "config.h"
#include "glide/future.h"
#include "glide_base.h"

namespace glide {

/**
 * The Client class is responsible for managing the connection of a client
 * to a server using a given configuration. It provides methods
 * to connect to the server and handles the connection lifecycle.
 */
class Client {
 public:
  /**
   * Constructs a Client with a const configuration.
   *
   * @param config A const reference to a glide::Config object.
   */
  explicit Client(const Config &config);

  /**
   * Connects the client using the serialized configuration.
   *
   * @return True if the connection is successful, false otherwise.
   */
  bool connect();

  /**
   * Sets a key-value pair in the client's configuration.
   *
   * @param key The key to set.
   * @param value The value to associate with the key.
   * @return A Future containing the status of the operation.
   */
  Future<absl::Status> set(const std::string &key, const std::string &value);

  /**
   * Retrieves the value associated with the given key from the client's
   * configuration.
   *
   * @param key The key whose associated value is to be returned.
   * @return A Future containing the value associated with the specified key,
   *         or an error status if the key is not found or an error occurs.
   */
  Future<absl::StatusOr<std::string>> get(const std::string &key);

  /**
   * Gets a value associated with the given string `key` and deletes the key.
   *
   * @param key The key whose associated value is to be returned and deleted.
   * @return A Future containing the value associated with the specified key,
   *         or an error status if the key is not found or an error occurs.
   */
  Future<absl::StatusOr<std::string>> getdel(const std::string &key);

  /**
   * Sets multiple field-value pairs in a hash stored at the given key.
   *
   * @param key The key where the hash is stored.
   * @param field_values A map containing the field-value pairs to set in the
   * hash.
   * @return A Future containing the status of the operation.
   */
  Future<absl::Status> hset(
      const std::string &key,
      const std::map<std::string, std::string> &field_values);

  /**
   * Retrieves the value associated with a field in a hash stored at the given
   * key.
   *
   * @param key The key where the hash is stored.
   * @param field The field within the hash whose value should be retrieved.
   * @return A Future containing the value associated with the specified field,
   *         or an error status if the key or field is not found or an error
   * occurs.
   */
  Future<absl::StatusOr<std::string>> hget(const std::string &key,
                                           const std::string &field);

  /**
   * Destructor for the Client class.
   */
  ~Client();

 private:
  glide::Config config_;
  const core::ConnectionResponse *connection_;

  /**
   * Executes a command with the given request type and arguments.
   *
   * @param type The type of request to execute.
   * @param args A vector of string arguments for the command.
   * @param channel_ptr A pointer to the channel for handling the command
   * response.
   */
  void exec_command(core::RequestType type, std::vector<std::string> &args,
                    uintptr_t channel_ptr);
};

}  // namespace glide

#endif  // CLIENT_HPP_
