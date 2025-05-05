#include <glide/callback.h>
#include <glide/future.h>
#include <glide/glide_base.h>
#include <glide/helper.h>

namespace glide {

/**
 * Callback function called when a command is successfully executed.
 * The callback pointer should be relased by the caller and not inside the
 * function!
 */
void on_success(uintptr_t ptr, const core::CommandResponse *message) {
  auto *cb_ptr = reinterpret_cast<IFuture *>(ptr);
  if (cb_ptr) MethodAccess::set_value(cb_ptr, message);
}

/**
 * Callback function called when a command fails to execute.
 * The callback pointer should be relased by the caller and not inside the
 * function!
 */
void on_failure(uintptr_t ptr, const char *message,
                core::RequestErrorType type) {
  auto *cb_ptr = reinterpret_cast<IFuture *>(ptr);
  if (cb_ptr) MethodAccess::set_value(cb_ptr, type, message);
}

}  // namespace glide
