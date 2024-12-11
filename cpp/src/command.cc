#include <glide/client.h>
#include <glide/command.h>
#include <glide/glide_base.h>

namespace glide {

/**
 * Sets a key-value pair in the client's configuration.
 */
bool Command::set(Client& client, const std::string& key,
                  const std::string& value) {
  std::vector<std::string> args = {key, value};
  CommandResponseData channel;
  client.exec_command(glide::RequestType::Set, args, channel);

  // Wait for the response to be received.
  channel.wait();
  return !channel.is_error;
}

/**
 * Retrieves the value associated with the given key from the client's
 * configuration.
 */
// TODO: 1: abseil status, 2: zero-copy result.
std::string Command::get(Client& client, const std::string& key) {
  std::vector<std::string> args = {key};
  CommandResponseData channel;
  client.exec_command(glide::RequestType::Get, args, channel);

  // Wait for the response to be received.
  channel.wait();
  if (channel.is_error) {
    return "";
  }
  return std::string(channel.value->string_value,
                     channel.value->string_value_len);
}

}  // namespace glide
