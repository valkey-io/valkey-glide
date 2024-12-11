#ifndef GLIDE_COMMAND_H_
#define GLIDE_COMMAND_H_
#include <string>

namespace glide {

/**
 * Forward declaration of the Client class to avoid errors in Command methods
 * signature.
 * The actual implementation is inside:
 * - @ref ./include/glide/client.h
 * - @ref ./src/client.cc
 */
class Client;

/**
 * Class that encapsulates a command executable on a client.
 * The implementation of commands is contained within this class.
 */
class Command {
 public:
  /**
   * Sets a key-value pair in the client's configuration.
   *
   * @param client The client object.
   * @param key The key to set.
   * @param value The value to associate with the key.
   * @return True if the operation is successful, false otherwise.
   */
  bool set(Client& client, const std::string& key, const std::string& value);

  /**
   * Retrieves the value associated with the given key from the client's
   * configuration.
   *
   * @param client The client object.
   * @param key The key whose associated value is to be returned.
   * @return The value associated with the specified key, or an empty string if
   * the key is not found or an error occurs.
   */
  std::string get(Client& client, const std::string& key);
};

}  // namespace glide
#endif  // GLIDE_COMMAND_H_
