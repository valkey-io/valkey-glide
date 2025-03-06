using Valkey.Glide.InterOp.Exceptions;

namespace Valkey.Glide.Commands;

public class OperationFailedException(string message) : GlideException(message);
