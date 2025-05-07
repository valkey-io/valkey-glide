#include <glide/future.h>

namespace glide {

/**
 * @brief Marks the future as ready and notifies waiting threads.
 */
void IFuture::ready() {
  {
    std::unique_lock<std::mutex> lock(*mtx_);
    ready_ = true;
  }
  cv_->notify_all();
}

/**
 * @brief Constructs a new IFuture object.
 */
IFuture::IFuture()
    : cv_(std::make_unique<std::condition_variable>()),
      mtx_(std::make_unique<std::mutex>()),
      ready_(false) {}

/**
 * @brief Waits until the future is ready.
 */
void IFuture::wait() {
  std::unique_lock<std::mutex> lock(*mtx_);
  cv_->wait(lock, [this] { return ready_; });
}

/**
 * @brief Sets the value of a future from a command response.
 * @param resp The future to set.
 * @param message The command response to set.
 */
void MethodAccess::set_value(IFuture* resp,
                             const core::CommandResponse* message) {
  resp->set_value(message);
}

/**
 * @brief Sets an error value for a future.
 * @param resp The future to set.
 * @param type The type of error.
 * @param message The error message.
 */
void MethodAccess::set_value(IFuture* resp, core::RequestErrorType type,
                             const char* message) {
  resp->set_value(type, message);
}

}  // namespace glide
