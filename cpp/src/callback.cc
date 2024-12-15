#include <glide/callback.h>

#include "glide/glide_base.h"

namespace glide {

/**
 * Sets the value of the CommandResponseData object.
 */
void CommandResponseData::set_value(const CommandResponse* new_value) {
  std::lock_guard<std::mutex> lock(mtx_);
  value = new_value;
  is_ready = true;
  cv_.notify_one();
}

/**
 * Sets the error information for the CommandResponseData object.
 */
void CommandResponseData::set_error(const RequestErrorType type,
                                    const char* message) {
  std::lock_guard<std::mutex> lock(mtx_);
  error_type = type;
  error_message = message;
  is_error = true;
  is_ready = true;
  cv_.notify_one();
}

/**
 * Waits for the response to be ready and returns the command response value.
 */
const CommandResponse* CommandResponseData::wait() {
  std::unique_lock<std::mutex> lock(mtx_);
  cv_.wait(lock, [this]() { return is_ready; });
  return value;
}

/**
 * Default constructor for CommandResponseData.
 * Constructs an empty CommandResponseData object.
 */
CommandResponseData::CommandResponseData() = default;

/**
 * Copy constructor for CommandResponseData.
 * Creates a new CommandResponseData object as a copy of an existing one.
 */
CommandResponseData::CommandResponseData(
    const CommandResponseData& other) noexcept {
  *this = other;
}

/**
 * Copy assignment operator for CommandResponseData.
 * Assigns the values of another CommandResponseData object to this object.
 */
CommandResponseData& CommandResponseData::operator=(
    const CommandResponseData& other) noexcept {
  if (this == &other) {
    return *this;
  }
  value = other.value;
  error_message = other.error_message;
  error_type = other.error_type;
  is_error = other.is_error;
  is_ready = other.is_ready;
  return *this;
}

/**
 * Move constructor.
 */
CommandResponseData::CommandResponseData(CommandResponseData&& other) noexcept {
  *this = other;
}

/**
 * Move assignment operator for CommandResponseData.
 */
CommandResponseData& CommandResponseData::operator=(
    CommandResponseData&& other) noexcept {
  *this = other;
  return *this;
}

/**
 * Destructor for the CommandResponseData class.
 * This destructor is responsible for cleaning up and deallocating any
 * resources associated with the CommandResponseData object.
 */
CommandResponseData::~CommandResponseData() {
  if (value) {
    glide::free_command_response(const_cast<CommandResponse*>(value));
  }
}

/**
 * Callback function called when a command is successfully executed.
 */
void on_success(uintptr_t ptr, const CommandResponse* message) {
  CommandResponseData* db = reinterpret_cast<CommandResponseData*>(ptr);
  if (db) {
    db->set_value(message);
  }
}

/**
 * Callback function called when a command fails to execute.
 */
void on_failure(uintptr_t ptr, const char* message, RequestErrorType type) {
  CommandResponseData* db = reinterpret_cast<CommandResponseData*>(ptr);
  if (db) {
    db->set_error(type, message);
  }
}

}  // namespace glide
