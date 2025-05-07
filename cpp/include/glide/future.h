#ifndef FUTURE_HPP_
#define FUTURE_HPP_

#include <absl/status/status.h>
#include <absl/status/statusor.h>

#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>
#include <type_traits>

#include "glide/glide_base.h"
#include "helper.h"

namespace glide {

/**
 * @brief Internal utility class to enable set_value methods for internal
 * components.
 *
 * This class is not intended for use by end users and exists solely to support
 * internal implementation details.
 */
class MethodAccess;

/**
 * @brief Base interface for future objects that can be waited on.
 *
 * Provides core functionality for waiting on asynchronous results.
 */
class IFuture {
 protected:
  std::unique_ptr<std::condition_variable> cv_;
  std::unique_ptr<std::mutex> mtx_;
  bool ready_;

  /**
   * @brief Marks the future as ready and notifies waiting threads.
   */
  void ready();

  /**
   * @brief Sets the value from a command response.
   * @param new_value The command response to set.
   */
  virtual void set_value(const core::CommandResponse* new_value) = 0;

  /**
   * @brief Sets an error value.
   * @param type The type of error.
   * @param message The error message.
   */
  virtual void set_value(const core::RequestErrorType type,
                         const char* message) = 0;

  friend class MethodAccess;

 public:
  /**
   * @brief Constructs a new IFuture object.
   */
  IFuture();

  /**
   * @brief Waits until the future is ready.
   */
  void wait();

  /**
   * @brief Waits for the future to be ready or until the timeout duration has
   * elapsed.
   *
   * This function blocks until the condition is met or the specified timeout
   * expires. The timeout duration must be specified in either milliseconds,
   * microseconds, or seconds.
   *
   * @tparam Rep The representation type of the duration.
   * @tparam Period The period type of the duration.
   * @param timeout The duration to wait (must be in us, ms, or second).
   */
  template <typename Rep, typename Period>
  void wait_for(const std::chrono::duration<Rep, Period>& timeout) {
    std::unique_lock<std::mutex> lock(*mtx_);
    cv_->wait_for(lock, timeout, [this] { return ready_; });
  }

  /**
   * @brief Waits for the future to be ready or until the specified time point
   * has been reached.
   *
   * This function blocks until the condition is met or the specified absolute
   * time point is reached. The time point should be constructed using a
   * duration in seconds (`s`), milliseconds (`ms`), or microseconds (`us`).
   *
   * @tparam Clock The clock type (e.g., std::chrono::steady_clock).
   * @tparam Duration The duration type used to define the time point.
   * @param timeout_time The absolute time point to wait until.
   */
  template <typename Clock, typename Duration>
  void wait_until(
      const std::chrono::time_point<Clock, Duration>& timeout_time) {
    std::unique_lock<std::mutex> lock(*mtx_);
    cv_->wait_until(lock, timeout_time, [this] { return ready_; });
  }
};

/**
 * @brief Helper class to access protected methods of IFuture.
 */
class MethodAccess {
 public:
  /**
   * @brief Sets the value of a future from a command response.
   * @param resp The future to set.
   * @param message The command response to set.
   */
  static void set_value(IFuture* resp, const core::CommandResponse* message);

  /**
   * @brief Sets an error value for a future.
   * @param resp The future to set.
   * @param type The type of error.
   * @param message The error message.
   */
  static void set_value(IFuture* resp, core::RequestErrorType type,
                        const char* message);
};

/**
 * @brief Templated future class for specific result types.
 * @tparam T The type of the result.
 */
template <typename T>
class Future : public IFuture {
 private:
  T result_;

 protected:
  /**
   * @brief Sets the value from a command response.
   * @param resp The command response to set.
   */
  void set_value(const core::CommandResponse* resp) override {
    if constexpr (std::is_same_v<T, absl::Status>)
      result_ = absl::OkStatus();
    else if constexpr (std::is_same_v<T, absl::StatusOr<std::string>>)
      result_ = std::string(resp->string_value, resp->string_value_len);
    else if constexpr (std::is_same_v<T, absl::StatusOr<bool>>)
      result_ = resp->bool_value;
    else
      static_assert(false, "unsupported data type");

    // Release the respoonse.
    // TODO: needs to be managed in a better way.
    core::free_command_response(const_cast<core::CommandResponse*>(resp));

    ready();
  }

  /**
   * @brief Sets an error value.
   * @param type The type of error.
   * @param message The error message.
   */
  void set_value(const core::RequestErrorType type,
                 const char* message) override {
    result_ = ConvertRequestError(type, message);

    // Release the respoonse.
    // TODO: needs to be managed in a better way.
    // core::free_error_message(message);

    ready();
  }

 public:
  using IFuture::IFuture;

  /**
   * @brief Gets the result, waiting if necessary.
   * @return The result of type T.
   */
  T get() {
    if (!ready_) wait();
    return result_;
  }
};

}  // namespace glide

#endif  // FUTURE_HPP_
