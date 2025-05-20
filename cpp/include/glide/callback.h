#ifndef CALLBACK_HPP_
#define CALLBACK_HPP_

#include "glide_base.h"

namespace glide {

/**
 * Callback function called when a command is successfully executed.
 * The callback pointer should be relased by the caller and not inside the
 * function!
 * @param ptr The pointer to the CommandResponseData object.
 * @param message The command response message.
 */
void on_success(uintptr_t ptr, const core::CommandResponse* message);

/**
 * Callback function called when a command fails to execute.
 * The callback pointer should be relased by the caller and not inside the
 * function!
 * @param ptr The pointer to the CommandResponseData object.
 * @param message The error message.
 * @param type The type of the error.
 */
void on_failure(uintptr_t ptr, const char* message,
                core::RequestErrorType type);

}  // namespace glide

#endif
