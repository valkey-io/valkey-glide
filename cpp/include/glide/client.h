#ifndef CLIENT_HPP
#define CLIENT_HPP

#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <vector>

#include "callback.h"
#include "config.h"
#include "glide/command.h"
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
  explicit Client(const glide::Config& config);

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
   * @return True if the operation is successful, false otherwise.
   */
  bool set(const std::string& key, const std::string& value);

  /**
   * Retrieves the value associated with the given key from the client's
   * configuration.
   *
   * @param key The key whose associated value is to be returned.
   * @return The value associated with the specified key, or an empty string if
   * the key is not found or an error occurs.
   */
  std::string get(const std::string& key);

  /**
   * Executes a command with the given request type and arguments.
   *
   * @param type The type of request to execute.
   * @param args A vector of string arguments for the command.
   * @param channel A reference to a CommandResponseData object to store the
   * result of the command execution.
   */
  void exec_command(glide::RequestType type, std::vector<std::string>& args,
                    CommandResponseData& channel);

  /**
   * Destructor for the Client class.
   */
  // TODO: virtual
  ~Client();

 private:
  glide::Config config_;
  const ConnectionResponse* connection_;
  Command command_;
};

}  // namespace glide

#endif  // CLIENT_HPP
