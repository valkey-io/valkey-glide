using System;

namespace Valkey.Glide.InterOp.Exceptions;

public class GlideException(string message) : Exception(message) { }
