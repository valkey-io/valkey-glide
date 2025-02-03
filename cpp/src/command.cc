#include <glide/client.h>
#include <glide/command.h>
#include <glide/glide_base.h>

namespace glide {

/**
 * Sets a key-value pair in the client's configuration.
 */
bool Command::set(Client &client, const std::string &key,
                  const std::string &value) {
  std::vector<std::string> args = {key, value};
  CommandResponseData channel;
  client.exec_command(glide::RequestType::Set, args, channel);

  // Wait for the response to be received.
  channel.wait();
  return !channel.is_error;
}

/**
 * Sets multiple field-value pairs in a hash stored at the given key.
 */
bool Command::hset(Client &client, const std::string &key,
                   const std::map<std::string, std::string> &field_values) {
  std::vector<std::string> args;
  args.push_back(key);
  for (const auto &pair : field_values) {
    args.push_back(pair.first);
    args.push_back(pair.second);
  }
  CommandResponseData channel;
  client.exec_command(RequestType::HSet, args, channel);
  channel.wait();
  return !channel.is_error;
}
/**
 * Retrieves the value associated with a field in a hash stored at the given
 * key.
 */
std::string Command::hget(Client &client, const std::string &key,
                          const std::string &field) {
  std::vector<std::string> args = {key, field};
  CommandResponseData channel;
  client.exec_command(RequestType::HGet, args, channel);
  channel.wait();
  if (channel.is_error) {
    return "";
  }
  return std::string(channel.value->string_value,
                     channel.value->string_value_len);
}

/**
 * Retrieves the value associated with the given key from the client's
 * configuration.
 */
// TODO: 1: abseil status, 2: zero-copy result.
std::string Command::get(Client &client, const std::string &key) {
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

std::string Command::getdel(Client &client, const std::string &key) {
  std::vector<std::string> args = {key};
  CommandResponseData channel;
  client.exec_command(glide::RequestType::GetDel, args, channel);

  // Wait for the response to be received.
  channel.wait();
  if (channel.is_error) {
    return "";
  }
  return std::string(channel.value->string_value,
                     channel.value->string_value_len);
}

}  // namespace glide
