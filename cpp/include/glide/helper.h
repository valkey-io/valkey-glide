#ifndef HELPER_HPP_
#define HELPER_HPP_
#include <absl/status/status.h>

#include <string>

#include "glide_base.h"

namespace glide {

/**
 * @brief Maps a RequestErrorType to an appropriate Abseil status with the given
 * message.
 *
 * This function converts Glide core error types to their corresponding Abseil
 * Status representation, preserving the error message.
 *
 * @param type The RequestErrorType to be mapped
 * @param message The error message to include in the Status
 * @return absl::Status The corresponding Abseil status with the provided
 * message
 */
absl::Status ConvertRequestError(core::RequestErrorType type,
                                 const std::string& message);

}  // namespace glide

#endif  // HELPER_HPP_
