#ifndef CALLBACK_HPP_
#define CALLBACK_HPP_

#include <condition_variable>
#include <mutex>

#include "glide_base.h"
namespace glide {

/**
 * CommandResponseData is responsible for managing the response data from a
 * command. It handles both successful responses and error cases, providing
 * mechanisms to set and retrieve the response or error information in a
 * thread-safe manner.
 */
class CommandResponseData {
 public:
  /**
   * Holds the response value from a command.
   */
  const CommandResponse* value;

  /**
   * Holds the error message if an error occurs.
   */
  const char* error_message;

  /**
   * Holds the type of error if an error occurs.
   */
  RequestErrorType error_type;

  /**
   * Indicates whether an error has occurred.
   */
  bool is_error = false;

  /**
   * Indicates whether the response is ready.
   */
  bool is_ready = false;

  /**
   * Sets the command response value and marks it as ready.
   *
   * @param new_value The new command response value.
   */
  void set_value(const CommandResponse* new_value);

  /**
   * Sets the error type and message, and marks the response as an error.
   *
   * @param type The type of the error.
   * @param message The error message.
   */
  void set_error(const RequestErrorType type, const char* message);

  /**
   * Waits for the response to be ready and returns the command response value.
   *
   * @return The command response value.
   */
  const CommandResponse* wait();

  /**
   * Default constructor for CommandResponseData.
   * Constructs an empty CommandResponseData object.
   */
  CommandResponseData();

  /**
   * Copy constructor for CommandResponseData.
   * Creates a new CommandResponseData object as a copy of an existing one.
   *
   * @param other The source CommandResponseData object to copy from.
   */
  CommandResponseData(const CommandResponseData& other) noexcept;

  /**
   * Copy assignment operator for CommandResponseData.
   *
   * Assigns the value of an existing CommandResponseData object to this object.
   *
   * @param other The source CommandResponseData object to copy from.
   * @return A reference to this object.
   */
  CommandResponseData& operator=(const CommandResponseData& other) noexcept;

  /**
   * Move constructor for CommandResponseData.
   *
   * Transfers ownership of resources from the source object to the new object.
   *
   * @param other The source CommandResponseData object to move from.
   */
  CommandResponseData(CommandResponseData&& other) noexcept;

  /**
   * Move assignment operator for CommandResponseData.
   *
   * Transfers ownership of resources from the source object to this object.
   *
   * @param other The source CommandResponseData object to move from.
   * @return A reference to this object.
   */
  CommandResponseData& operator=(CommandResponseData&& other) noexcept;

  /**
   * Destructor for the CommandResponseData class.
   * This destructor is responsible for cleaning up and deallocating any
   * resources associated with the CommandResponseData object.
   */
  ~CommandResponseData();

 private:
  // TODO: moving out as channel implementation.
  std::mutex mtx_;
  std::condition_variable cv_;
};

/**
 * On success callback.
 * @param ptr The pointer to the CommandResponseData object.
 * @param message The command response message.
 */
void on_success(uintptr_t ptr, const CommandResponse* message);

/**
 * On failure callback.
 * @param ptr The pointer to the CommandResponseData object.
 * @param message The error message.
 * @param type The type of the error.
 */
void on_failure(uintptr_t ptr, const char* message, RequestErrorType type);

}  // namespace glide

#endif
