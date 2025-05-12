#include <absl/status/status.h>
#include <glide/glide_base.h>
#include <glide/helper.h>

#include <string>

namespace glide {

/**
 * @brief Maps a RequestErrorType to an appropriate Abseil status with the given
 * message.
 *
 * This function converts Glide core error types to their corresponding Abseil
 * Status representation, preserving the error message.
 */
absl::Status ConvertRequestError(core::RequestErrorType type,
                                 const std::string& message) {
  switch (type) {
    case core::RequestErrorType::ExecAbort:
      return absl::AbortedError(message);
    case core::RequestErrorType::Timeout:
      return absl::DeadlineExceededError(message);
    case core::RequestErrorType::Disconnect:
      return absl::UnavailableError(message);
    case core::RequestErrorType::Unspecified:
    default:
      return absl::UnknownError(message);
  }
}

}  // namespace glide
