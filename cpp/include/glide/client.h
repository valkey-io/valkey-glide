#ifndef CLIENT_HPP_
#define CLIENT_HPP_

#include <absl/status/status.h>
#include <absl/status/statusor.h>

#include <cstddef>
#include <cstdint>
#include <map>
#include <string>
#include <type_traits>
#include <vector>

#include "config.h"
#include "glide/future.h"
#include "glide_base.h"

namespace glide {

/**
 * @brief A client for connecting to and interacting with a Redis-compatible
 * server.
 *
 * The Client class manages the connection lifecycle and provides methods for
 * executing Redis commands asynchronously. It supports both string and binary
 * data operations.
 */
class Client {
 public:
  /**
   * @brief Constructs a new Client instance.
   *
   * @param config Configuration object containing connection parameters and
   * settings.
   */
  explicit Client(const Config &config);

  /**
   * @brief Establishes a connection to the server.
   *
   * @return true if the connection was established successfully, false
   * otherwise.
   */
  bool connect();

  /**
   * @brief Sets a string value for the specified key.
   *
   * @param key The key to set.
   * @param value The string value to associate with the key.
   * @return A Future that resolves to the status of the operation.
   */
  Future<absl::Status> set(const std::string &key, const std::string &value);

  /**
   * @brief Sets a binary value for the specified key.
   *
   * @param key The key to set.
   * @param value The binary value to associate with the key.
   * @return A Future that resolves to the status of the operation.
   */
  Future<absl::Status> set(const std::string &key,
                           const std::vector<std::byte> &value);

  /**
   * @brief Retrieves the value associated with the specified key.
   *
   * @tparam T The return type, must be std::string or std::vector<std::byte>.
   * @param key The key whose value should be retrieved.
   * @return A Future that resolves to the value associated with the key,
   *         or an error if the key is not found or an error occurs.
   */
  template <typename T>
  std::enable_if_t<std::is_same_v<T, std::string> ||
                       std::is_same_v<T, std::vector<std::byte>>,
                   Future<absl::StatusOr<T>>>
  get(const std::string &key) {
    Future<absl::StatusOr<T>> future;
    auto future_ptr = reinterpret_cast<uintptr_t>(&future);
    std::vector<std::string> args = {key};
    exec_command(core::RequestType::Get, args, future_ptr);
    return future;
  }

  /**
   * @brief Retrieves and deletes the value associated with the specified key.
   *
   * @tparam T The return type, must be std::string or std::vector<std::byte>.
   * @param key The key whose value should be retrieved and deleted.
   * @return A Future that resolves to the value associated with the key,
   *         or an error if the key is not found or an error occurs.
   */
  template <typename T>
  std::enable_if_t<std::is_same_v<T, std::string> ||
                       std::is_same_v<T, std::vector<std::byte>>,
                   Future<absl::StatusOr<T>>>
  getdel(const std::string &key) {
    Future<absl::StatusOr<T>> future;
    auto future_ptr = reinterpret_cast<uintptr_t>(&future);
    std::vector<std::string> args = {key};
    exec_command(core::RequestType::GetDel, args, future_ptr);
    return future;
  }

  /**
   * @brief Sets multiple string field-value pairs in a hash.
   *
   * @param key The key where the hash is stored.
   * @param field_values A map of field names to string values to set in the
   * hash.
   * @return A Future that resolves to the status of the operation.
   */
  Future<absl::Status> hset(
      const std::string &key,
      const std::map<std::string, std::string> &field_values);

  /**
   * @brief Sets multiple binary field-value pairs in a hash.
   *
   * @param key The key where the hash is stored.
   * @param field_values A map of field names to binary values to set in the
   * hash.
   * @return A Future that resolves to the status of the operation.
   */
  Future<absl::Status> hset(
      const std::string &key,
      const std::map<std::string, std::vector<std::byte>> &field_values);

  /**
   * @brief Retrieves the value of a field in a hash.
   *
   * @tparam T The return type, must be std::string or std::vector<std::byte>.
   * @param key The key where the hash is stored.
   * @param field The field name within the hash whose value should be
   * retrieved.
   * @return A Future that resolves to the value of the specified field,
   *         or an error if the key or field is not found or an error occurs.
   */
  template <typename T>
  std::enable_if_t<std::is_same_v<T, std::string> ||
                       std::is_same_v<T, std::vector<std::byte>>,
                   Future<absl::StatusOr<T>>>
  hget(const std::string &key, const std::string &field) {
    Future<absl::StatusOr<T>> future;
    auto future_ptr = reinterpret_cast<uintptr_t>(&future);
    std::vector<std::string> args = {key, field};
    exec_command(core::RequestType::HGet, args, future_ptr);
    return future;
  }

  /**
   * @brief Destroys the Client instance and cleans up resources.
   */
  ~Client();

 private:
  glide::Config config_;
  const core::ConnectionResponse *connection_;

  /**
   * @brief Executes a command with string arguments.
   *
   * @param type The type of request to execute.
   * @param args A vector of string arguments for the command.
   * @param channel_ptr A pointer to the future object for handling the
   * response.
   */
  void exec_command(core::RequestType type, std::vector<std::string> &args,
                    uintptr_t channel_ptr);

  /**
   * @brief Executes a command with binary arguments.
   *
   * @param type The type of request to execute.
   * @param args A vector of pointers to the raw binary argument data.
   * @param args_len A vector of argument lengths corresponding to each
   * argument.
   * @param channel_ptr A pointer to the future object for handling the
   * response.
   */
  void exec_command_b(core::RequestType type, std::vector<uintptr_t> &args,
                      std::vector<unsigned long> &args_len,
                      uintptr_t channel_ptr);
};

}  // namespace glide

#endif  // CLIENT_HPP_
