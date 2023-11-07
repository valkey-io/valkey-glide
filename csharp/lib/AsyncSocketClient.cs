using System;
using System.Buffers;
using System.Collections.Concurrent;
using System.Linq;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Channels;

namespace babushka
{
    public class AsyncSocketClient : IDisposable
    {
        #region public methods

        public static async Task<AsyncSocketClient> CreateSocketClient(string address)
        {
            var socketName = await GetSocketNameAsync();
            var socket = await GetSocketAsync(socketName, address);

            // if logger has been initialized by the external-user on info level this log will be shown
            Logger.Log(Level.Info, "connection info", "new connection established");
            return new AsyncSocketClient(socket);
        }

        public async Task SetAsync(string key, string value)
        {
            var (message, task) = messageContainer.GetMessageForCall(null, null);
            WriteToSocket(new WriteRequest { callbackIndex = message.Index, type = RequestType.SetString, args = new() { key, value } });
            await task;
        }

        public async Task<string?> GetAsync(string key)
        {
            var (message, task) = messageContainer.GetMessageForCall(null, null);
            WriteToSocket(new WriteRequest { callbackIndex = message.Index, type = RequestType.GetString, args = new() { key } });
            return await task;
        }

        private void DisposeWithError(Exception error)
        {
            if (Interlocked.CompareExchange(ref this.disposedFlag, 1, 0) == 1)
            {
                return;
            }
            this.socket.Close();
            messageContainer.DisposeWithError(error);
        }

        public void Dispose()
        {
            DisposeWithError(new ObjectDisposedException(null));
        }

        #endregion public methods

        #region private types

        // TODO - this repetition will become unmaintainable. We need to do this in macros.
        private enum RequestType
        {
            /// Type of a set server address request. This request should happen once, when the socket connection is initialized.
            SetNodeAddress = 1,
            /// Type of a get string request.
            GetString = 2,
            /// Type of a set string request.
            SetString = 3,
        }

        // TODO - this repetition will become unmaintainable. We need to do this in macros.
        private enum ResponseType
        {
            /// Type of a response that returns a null.
            Null = 0,
            /// Type of a response that returns a string.
            String = 1,
            /// Type of response containing an error that impacts a single request.
            RequestError = 2,
            /// Type of response containing an error causes the connection to close.
            ClosingError = 3,
        }

        private struct WriteRequest
        {
            internal List<string> args;
            internal int callbackIndex;
            internal RequestType type;
        }

        private struct Header
        {
            internal UInt32 length;
            internal UInt32 callbackIndex;
            internal ResponseType responseType;
        }

        // TODO - this repetition will become unmaintainable. We need to do this in macros.
        private const int HEADER_LENGTH_IN_BYTES = 12;

        #endregion private types

        #region private methods

        /// Triggers the creation of a Rust-side socket server if one isn't running, and returns the name of the socket the server is listening on.
        private static Task<string> GetSocketNameAsync()
        {
            var completionSource = new TaskCompletionSource<string>();
            InitCallback initCallback = (IntPtr successPointer, IntPtr errorPointer) =>
            {
                if (successPointer != IntPtr.Zero)
                {
                    var address = Marshal.PtrToStringAnsi(successPointer);
                    if (address is not null)
                    {
                        completionSource.SetResult(address);
                    }
                    else
                    {
                        completionSource.SetException(new Exception("Received address that couldn't be converted to string"));
                    }
                }
                else if (errorPointer != IntPtr.Zero)
                {
                    var errorMessage = Marshal.PtrToStringAnsi(errorPointer);
                    completionSource.SetException(new Exception(errorMessage));
                }
                else
                {
                    completionSource.SetException(new Exception("Did not receive results from init callback"));
                }
            };
            var callbackPointer = Marshal.GetFunctionPointerForDelegate(initCallback);
            StartSocketListener(callbackPointer);
            return completionSource.Task;
        }

        /// Returns a new ready to use socket.
        private static async Task<Socket> GetSocketAsync(string socketName, string address)
        {
            var socket = CreateSocket(socketName);
            await WriteToSocket(socket, new[] { new WriteRequest { args = new() { address }, type = RequestType.SetNodeAddress, callbackIndex = 0 } }, new());
            var buffer = new byte[HEADER_LENGTH_IN_BYTES];
            await socket.ReceiveAsync(buffer, SocketFlags.None);
            var header = GetHeader(buffer, 0);
            if (header.responseType == ResponseType.ClosingError || header.responseType == ResponseType.RequestError)
            {
                var messageLength = header.length - buffer.Length;
                var messageBuffer = new byte[messageLength];
                var bytes = await socket.ReceiveAsync(messageBuffer, SocketFlags.None);
                var message = Encoding.UTF8.GetString(new Span<byte>(messageBuffer, 0, (int)messageLength));
                throw new Exception(message);
            }
            return socket;
        }

        private AsyncSocketClient(Socket socket)
        {
            this.socket = socket;
            StartListeningOnReadSocket();
            StartListeningOnWriteChannel();
        }

        ~AsyncSocketClient()
        {
            Dispose();
        }

        private static Header GetHeader(byte[] buffer, int position)
        {
            var span = MemoryMarshal.Cast<byte, UInt32>(new ReadOnlySpan<byte>(buffer, position, HEADER_LENGTH_IN_BYTES));
            return new Header
            {
                length = span[0],
                callbackIndex = span[1],
                responseType = (ResponseType)span[2]
            };
        }

        private void StartListeningOnReadSocket()
        {
            Task.Run(async () =>
            {
                var previousSegment = new ArraySegment<byte>();
                while (socket.Connected && !IsDisposed)
                {
                    try
                    {
                        var buffer = GetBuffer(previousSegment);
                        var segmentAfterPreviousData = new ArraySegment<byte>(buffer, previousSegment.Count, buffer.Length - previousSegment.Count);
                        var receivedLength = await socket.ReceiveAsync(segmentAfterPreviousData, SocketFlags.None);
                        if (receivedLength == 0)
                        {
                            continue;
                        }
                        var newBuffer = ParseReadResults(buffer, receivedLength + previousSegment.Count, messageContainer);
                        if (previousSegment.Array is not null && previousSegment.Array != newBuffer.Array)
                        {
                            ArrayPool<byte>.Shared.Return(previousSegment.Array);
                        }
                        previousSegment = newBuffer;
                    }
                    catch (Exception exc)
                    {
                        DisposeWithError(exc);
                    }
                }
            });
        }

        private void StartListeningOnWriteChannel()
        {
            Task.Run(async () =>
            {
                var writeRequests = new List<WriteRequest>();
                var argLengths = new List<int>();
                while (socket.Connected)
                {
                    await this.writeRequestsChannel.Reader.WaitToReadAsync();
                    while (this.writeRequestsChannel.Reader.TryRead(out var writeRequest))
                    {
                        writeRequests.Add(writeRequest);
                    }
                    await WriteToSocket(this.socket, writeRequests, argLengths);
                    writeRequests.Clear();
                }
            });
        }

        private void ResolveMessage(Message<string?> message, Header header, byte[] buffer, int counter)
        {
            // Work needs to be offloaded from the calling thread, because otherwise we might starve the reader task.
            Task.Run(() =>
            {
                if (header.responseType == ResponseType.Null)
                {
                    message.SetResult(null);
                    return;
                }

                var stringLength = header.length - HEADER_LENGTH_IN_BYTES;
                var result = Encoding.UTF8.GetString(new Span<byte>(buffer, counter + HEADER_LENGTH_IN_BYTES, (int)stringLength));
                if (header.responseType == ResponseType.String)
                {
                    message.SetResult(result);
                }
                if (header.responseType == ResponseType.RequestError)
                {
                    message.SetException(new Exception(result));
                }
                if (header.responseType == ResponseType.ClosingError)
                {
                    DisposeWithError(new Exception(result));
                    message.SetException(new Exception(result));
                }
            });
        }

        private ArraySegment<byte> ParseReadResults(byte[] buffer, int messageLength, MessageContainer messageContainer)
        {
            var counter = 0;
            while (counter + HEADER_LENGTH_IN_BYTES <= messageLength)
            {
                var header = GetHeader(buffer, counter);
                if (header.length == 0)
                {
                    throw new InvalidOperationException("Received 0-length header from socket");
                }
                if (counter + header.length > messageLength)
                {
                    return new ArraySegment<byte>(buffer, counter, messageLength - counter);
                }

                if (header.responseType == ResponseType.ClosingError)
                {
                    var stringLength = header.length - HEADER_LENGTH_IN_BYTES;
                    var result = Encoding.UTF8.GetString(new Span<byte>(buffer, counter + HEADER_LENGTH_IN_BYTES, (int)stringLength));
                    DisposeWithError(new Exception(result));
                }
                else
                {
                    var message = messageContainer.GetMessage((int)header.callbackIndex);
                    ResolveMessage(message, header, buffer, counter);
                }

                counter += (int)header.length;
            }

            return new ArraySegment<byte>(buffer, counter, messageLength - counter);
        }

        private static byte[] GetBuffer(ArraySegment<byte> previousBuffer)
        {
            var newBufferLength = 4096;
            if (previousBuffer.Count >= 4)
            {
                newBufferLength = MemoryMarshal.Read<int>(previousBuffer);
            }
            var newBuffer = ArrayPool<byte>.Shared.Rent(newBufferLength);
            if (previousBuffer.Array is not null)
            {
                Buffer.BlockCopy(previousBuffer.Array, previousBuffer.Offset, newBuffer, 0, previousBuffer.Count);
            }
            return newBuffer;
        }

        private static Socket CreateSocket(string socketAddress)
        {
            var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.IP);
            var endpoint = new UnixDomainSocketEndPoint(socketAddress);
            socket.Blocking = false;
            socket.Connect(endpoint);
            return socket;
        }

        private static void WriteUint32ToBuffer(UInt32 value, byte[] target, int offset)
        {
            var encodedVal = BitConverter.GetBytes(value);
            Buffer.BlockCopy(encodedVal, 0, target, offset, encodedVal.Length);
        }

        private void WriteToSocket(WriteRequest writeRequest)
        {
            if (IsDisposed)
            {
                throw new ObjectDisposedException(null);
            }
            if (!this.writeRequestsChannel.Writer.TryWrite(writeRequest))
            {
                throw new ObjectDisposedException("Writing after channel is closed");
            }
        }

        private static int getHeaderLength(WriteRequest writeRequest)
        {
            return HEADER_LENGTH_IN_BYTES + 4 * (writeRequest.args.Count - 1);
        }

        private static int lengthOfStrings(WriteRequest writeRequest)
        {
            return writeRequest.args.Aggregate<string, int>(0, (sum, arg) => sum + arg.Length);
        }

        private static int getRequiredBufferLength(IEnumerable<WriteRequest> writeRequests)
        {
            return writeRequests.Aggregate<WriteRequest, int>(0, (sum, request) =>
            {
                return (
                    sum +
                    getHeaderLength(request) +
                    // length * 3 is the maximum ratio between UTF16 byte count to UTF8 byte count.
                    // TODO - in practice we used a small part of our arrays, and this will be very expensive on
                    // large inputs. We can use the slightly slower Buffer.byteLength on longer strings.
                    lengthOfStrings(request) * 3
                );
            });
        }

        private static int WriteRequestToBuffer(byte[] buffer, int offset, WriteRequest writeRequest, List<int> argLengths)
        {
            var encoding = Encoding.UTF8;
            var headerLength = getHeaderLength(writeRequest);
            var length = headerLength;
            argLengths.Clear();
            for (var i = 0; i < writeRequest.args.Count; i++)
            {
                var arg = writeRequest.args[i];
                var currentLength = encoding.GetBytes(arg, 0, arg.Length, buffer, length + offset);
                argLengths.Add(currentLength);
                length += currentLength;
            }
            WriteUint32ToBuffer((UInt32)length, buffer, offset);
            WriteUint32ToBuffer((UInt32)writeRequest.callbackIndex, buffer, offset + 4);
            WriteUint32ToBuffer((UInt32)writeRequest.type, buffer, offset + 8);
            for (var i = 0; i < argLengths.Count - 1; i++)
            {
                WriteUint32ToBuffer((UInt32)argLengths[i], buffer, offset + HEADER_LENGTH_IN_BYTES + i * 4);
            }
            return length;
        }

        private static async Task WriteToSocket(Socket socket, IEnumerable<WriteRequest> WriteRequests, List<int> argLengths)
        {
            var buffer = ArrayPool<byte>.Shared.Rent(getRequiredBufferLength(WriteRequests));
            var bytesAdded = 0;
            foreach (var writeRequest in WriteRequests)
            {
                bytesAdded += WriteRequestToBuffer(buffer, bytesAdded, writeRequest, argLengths);
            }


            var bytesWritten = 0;
            while (bytesWritten < bytesAdded)
            {
                bytesWritten += await socket.SendAsync(new ArraySegment<byte>(buffer, bytesWritten, bytesAdded - bytesWritten), SocketFlags.None);
            }
            ArrayPool<byte>.Shared.Return(buffer);
        }

        #endregion private methods

        #region private fields

        private readonly Socket socket;
        private readonly MessageContainer messageContainer = new();
        /// 1 when disposed, 0 before
        private int disposedFlag = 0;
        private bool IsDisposed => disposedFlag == 1;
        private readonly Channel<WriteRequest> writeRequestsChannel = Channel.CreateUnbounded<WriteRequest>(new UnboundedChannelOptions
        {
            SingleReader = true,
            SingleWriter = false,
            AllowSynchronousContinuations = false
        });

        #endregion private types

        #region rust bindings

        private delegate void InitCallback(IntPtr addressPointer, IntPtr errorPointer);
        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "start_socket_listener_wrapper")]
        private static extern void StartSocketListener(IntPtr initCallback);

        #endregion
    }
}
