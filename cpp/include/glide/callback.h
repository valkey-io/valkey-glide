#ifndef CALLBACK_HPP_
#define CALLBACK_HPP_

#include <future>
#include <optional>

#include "glide_base.h"

namespace glide {

/**
 * CommandResponseData is responsible for managing the response data from a
 * command. It handles both successful responses and error cases, providing
 * mechanisms to set and retrieve the response or error information in a
 * thread-safe manner.
 */
class Response {
 public:
  /**
   * Holds the response value from a command.
   */
  const core::CommandResponse* value;

  /**
   * Holds the error message if an error occurs.
   */
  const char* error_message;

  /**
   * Holds the type of error if an error occurs.
   */
  std::optional<core::RequestErrorType> error_type;

  /**
   * Sets the command response value and marks it as ready.
   *
   * @param new_value The new command response value.
   */
  void set_value(const core::CommandResponse* new_value);

  /**
   * Sets the error type and message, and marks the response as an error.
   *
   * @param type The type of the error.
   * @param message The error message.
   */
  void set_error(const core::RequestErrorType type, const char* message);

  /**
   * Default constructor for CommandResponseData.
   * Constructs an empty CommandResponseData object.
   */
  Response();

  /**
   * Copy constructor for CommandResponseData.
   * Creates a new CommandResponseData object as a copy of an existing one.
   *
   * @param other The source CommandResponseData object to copy from.
   */
  Response(const Response& other) noexcept;

  /**
   * Copy assignment operator for CommandResponseData.
   *
   * Assigns the value of an existing CommandResponseData object to this object.
   *
   * @param other The source CommandResponseData object to copy from.
   * @return A reference to this object.
   */
  Response& operator=(const Response& other) noexcept;

  /**
   * Move constructor for CommandResponseData.
   *
   * Transfers ownership of resources from the source object to the new object.
   *
   * @param other The source CommandResponseData object to move from.
   */
  Response(Response&& other) noexcept;

  /**
   * Move assignment operator for CommandResponseData.
   *
   * Transfers ownership of resources from the source object to this object.
   *
   * @param other The source CommandResponseData object to move from.
   * @return A reference to this object.
   */
  Response& operator=(Response&& other) noexcept;

  explicit Response(const core::CommandResponse* value) noexcept;

  explicit Response(core::RequestErrorType type, const char* message) noexcept;
  /**
   * Destructor for the CommandResponseData class.
   * This destructor is responsible for cleaning up and deallocating any
   * resources associated with the CommandResponseData object.
   */
  ~Response();
};

/**
 * On success callback.
 * @param ptr The pointer to the CommandResponseData object.
 * @param message The command response message.
 */
void on_success(uintptr_t ptr, const core::CommandResponse* message);

/**
 * On failure callback.
 * @param ptr The pointer to the CommandResponseData object.
 * @param message The error message.
 * @param type The type of the error.
 */
void on_failure(uintptr_t ptr, const char* message,
                core::RequestErrorType type);

}  // namespace glide

#endif
