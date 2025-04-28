#include <glide/callback.h>
#include <glide/glide_base.h>

namespace glide {

/**
 * Sets the value of the Response object.
 */
void Response::set_value(const core::CommandResponse *new_value) {
  value = new_value;
}

/**
 * Sets the error information for the Response object.
 */
void Response::set_error(const core::RequestErrorType type,
                         const char *message) {
  error_type = type;
  error_message = message;
}

/**
 * Default constructor for Response.
 * Constructs an empty Response object.
 */
Response::Response() = default;

/**
 * Copy constructor for Response.
 * Creates a new Response object as a copy of an existing one.
 */
Response::Response(const Response &other) noexcept { *this = other; }

/**
 * Copy assignment operator for Response.
 * Assigns the values of another Response object to this object.
 */
Response &Response::operator=(const Response &other) noexcept {
  if (this != &other) {
    value = other.value;
    error_message = other.error_message;
    error_type = other.error_type;
  }
  return *this;
}

/**
 * Move constructor.
 */
Response::Response(Response &&other) noexcept {
  value = other.value;
  error_message = other.error_message;
  error_type = other.error_type;
  other.value = nullptr;
}

/**
 * Move assignment operator for Response.
 */
Response &Response::operator=(Response &&other) noexcept {
  if (this != &other) {
    value = other.value;
    error_message = other.error_message;
    error_type = other.error_type;
    other.value = nullptr;
  }
  return *this;
}

Response::Response(const core::CommandResponse *value) noexcept
    : value(value) {}
Response::Response(core::RequestErrorType type, const char *message) noexcept
    : error_message(message), error_type(type) {}

/**
 * Destructor for the Response class.
 * This destructor is responsible for cleaning up and deallocating any
 * resources associated with the Response object.
 */
Response::~Response() {
  if (value) {
    core::free_command_response(const_cast<core::CommandResponse *>(value));
  }
}

/**
 * Callback function called when a command is successfully executed.
 * The callback pointer should be relased by the caller and not inside the
 * function!
 */
void on_success(uintptr_t ptr, const core::CommandResponse *message) {
  std::promise<Response> *cb_ptr =
      reinterpret_cast<std::promise<Response> *>(ptr);
  if (cb_ptr) {
    cb_ptr->set_value(Response(message));
  }
}

/**
 * Callback function called when a command fails to execute.
 * The callback pointer should be relased by the caller and not inside the
 * function!
 */
void on_failure(uintptr_t ptr, const char *message,
                core::RequestErrorType type) {
  std::promise<Response> *cb_ptr =
      reinterpret_cast<std::promise<Response> *>(ptr);
  if (cb_ptr) {
    cb_ptr->set_value(Response(type, message));
  }
}

}  // namespace glide
