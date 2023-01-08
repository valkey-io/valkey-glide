using System;
using System.Buffers;
using System.Collections.Concurrent;
using System.Linq;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Channels;
using System.Diagnostics;
using Pipelines.Sockets.Unofficial;
using System.IO.Pipelines;

namespace babushka
{
    public class AsyncSocketClient : IDisposable
    {
        private const int MINIMUM_SEGMENT_SIZE = 8 * 1024;
        [DllImport("libc.so.6", SetLastError=true)]
        private static extern int sched_setaffinity(
            int pid, 
            IntPtr cpusetsize, 
            ulong[] cpuset
        );
        public static async Task<AsyncSocketClient> CreateSocketClient(string address)
        {
            // Process Proc = Process.GetCurrentProcess();
            //ulong AffinityMask = (ulong)Proc.ProcessorAffinity;
            //AffinityMask &= 0x000000000000FFFF; // use processors 0-15
            //Proc.ProcessorAffinity = (IntPtr)AffinityMask;

            // for (int i = 0; i < Proc.Threads.Count; i++) 
            // {
            //     ProcessThread Thread = Proc.Threads[i];
            //     string affinitystr = null;
            //     ulong AffinityMask = 0x000000000000F7F7;
            //     // if (i == 0) {
            //     //     AffinityMask = 0x0000000000000001;
            //     // } else if (i == 1){
            //     //     AffinityMask = 0x0000000000000002;
            //     // }else if (i == 2){
            //     //     AffinityMask = 0x0000000000000004;
            //     // }else if (i == 3){
            //     //     AffinityMask = 0x0000000000000010;
            //     // }else if (i == 4){
            //     //     AffinityMask = 0x0000000000000020;
            //     // }else if (i == 5){
            //     //     AffinityMask = 0x0000000000000040;
            //     // }else if (i == 6){
            //     //     AffinityMask = 0x0000000000000080;
            //     // }else if (i == 7){
            //     //     AffinityMask = 0x0000000000000100;
            //     // } else {
            //     //     AffinityMask = 0x0000000000000100;
            //     // }
            //     // if (i < 9) {
            //     //     AffinityMask = 0x000000000000F7F7;
            //     // } else {
            //     //     AffinityMask = 0x00000000000001E0;
            //     // }

            //     sched_setaffinity(Thread.Id, new IntPtr(sizeof(ulong)),  new[] { AffinityMask });
            //     Console.WriteLine($"Setting affinity for thread with ID {Thread.Id} to {AffinityMask}");
            // }
            // DedicatedThreadPoolPipeScheduler Scheduler = new DedicatedThreadPoolPipeScheduler("SOCKETMANAGER:IO",
            //     workerCount: 10,
            //     priority: ThreadPriority.AboveNormal);
            PipeScheduler Scheduler = PipeScheduler.ThreadPool;

            const long Receive_PauseWriterThreshold = 4L * 1024 * 1024 * 1024; // receive: let's give it up to 4GiB of buffer for now
            const long Receive_ResumeWriterThreshold = 3L * 1024 * 1024 * 1024; // (large replies get crazy big)

            var defaultPipeOptions = PipeOptions.Default;

            long Send_PauseWriterThreshold = Math.Max(
                4L * 1024 * 1024 * 1024,// send: let's give it up to 4GiB
                defaultPipeOptions.PauseWriterThreshold); // or the default, whichever is bigger
            long Send_ResumeWriterThreshold = Math.Max(
                Send_PauseWriterThreshold,
                defaultPipeOptions.ResumeWriterThreshold);
            var SendPipeOptions = new PipeOptions(
                pool: defaultPipeOptions.Pool,
                readerScheduler: Scheduler,
                writerScheduler: Scheduler,
                pauseWriterThreshold: Send_PauseWriterThreshold,
                resumeWriterThreshold: Send_ResumeWriterThreshold,
                minimumSegmentSize: Math.Max(defaultPipeOptions.MinimumSegmentSize, MINIMUM_SEGMENT_SIZE),
                useSynchronizationContext: false);
            var ReceivePipeOptions = new PipeOptions(
                pool: defaultPipeOptions.Pool,
                readerScheduler: Scheduler,
                writerScheduler: Scheduler,
                pauseWriterThreshold: Receive_PauseWriterThreshold,
                resumeWriterThreshold: Receive_ResumeWriterThreshold,
                minimumSegmentSize: Math.Max(defaultPipeOptions.MinimumSegmentSize, MINIMUM_SEGMENT_SIZE),
                useSynchronizationContext: false);

            var socketName = await GetSocketNameAsync();
            var socket = await GetSocketAsync(socketName, address);
            IDuplexPipe pipe = SocketConnection.Create(socket, SendPipeOptions, ReceivePipeOptions, name: "babuska pipe");
            await WriteToSocket(socket, pipe, new[] { new WriteRequest { args = new() { address }, type = RequestType.SetServerAddress, callbackIndex = 0 } }, new());
            //var buffer = new byte[HEADER_LENGTH_IN_BYTES];

            Console.WriteLine($"Reading...");
            var input = pipe.Input;
            var readResult = await input.ReadAsync();
            Console.WriteLine($"ADDRESS: IsCompleted:{readResult.IsCompleted}, IsCanceled:{readResult.IsCanceled}, Length:{readResult.Buffer.Length}");
            var buffer = readResult.Buffer;
            var len = checked((int)buffer.Length);
            var arr = ArrayPool<byte>.Shared.Rent(len);
            buffer.CopyTo(arr);
            var header = GetHeader(arr, 0);
            if (header.responseType == ResponseType.ClosingError || header.responseType == ResponseType.RequestError)
            {
                throw new Exception("failed sending address");
            }
            input.AdvanceTo(readResult.Buffer.End);
            ArrayPool<byte>.Shared.Return(arr);
            return new AsyncSocketClient(socket, pipe);
        }

        public async Task SetAsync(string key, string value)
        {
            var (message, task) = messageContainer.GetMessageForCall(null, null);
            //Console.WriteLine($"SetAsync");
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


        #region private types

        // TODO - this repetition will become unmaintainable. We need to do this in macros.
        private enum RequestType
        {
            /// Type of a set server address request. This request should happen once, when the socket connection is initialized.
            SetServerAddress = 1,
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
            // Set the receive buffer size to 30k
            socket.ReceiveBufferSize = 30192;
            // Set the send buffer size to 30k.
            socket.SendBufferSize = 30192;

            return socket;
        }

        private AsyncSocketClient(Socket socket, IDuplexPipe? pipe = null)
        {
            this.socket = socket;
            this.pipe = pipe;
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

                var input = pipe.Input;
                while (socket.Connected && !IsDisposed)
                {
                    //Console.WriteLine($"Reading...");
                    var readResult = await input.ReadAsync();
                    //Console.WriteLine($"IsCompleted:{readResult.IsCompleted}, IsCanceled:{readResult.IsCanceled}, Length:{readResult.Buffer.Length}");
                    if (readResult.IsCompleted || readResult.IsCanceled) break;
                    var buffer = readResult.Buffer;
                    var len = checked((int)buffer.Length);
                    var arr = ArrayPool<byte>.Shared.Rent(len);
                    buffer.CopyTo(arr);
                    var newBuffer = ParseReadResults(arr, len, messageContainer);
                    input.AdvanceTo(readResult.Buffer.GetPosition(newBuffer.Offset), readResult.Buffer.End); // TODO: fix this, probably broken
                    ArrayPool<byte>.Shared.Return(arr);
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
                    await WriteToSocket(this.socket, this.pipe, writeRequests, argLengths);
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
                    //Console.WriteLine($"Got null response");
                    return;
                }

                var stringLength = header.length - HEADER_LENGTH_IN_BYTES;
                var result = Encoding.UTF8.GetString(new Span<byte>(buffer, counter + HEADER_LENGTH_IN_BYTES, (int)stringLength));
                if (header.responseType == ResponseType.String)
                {
                    //Console.WriteLine($"Got response {result}");
                    message.SetResult(result);
                }
                if (header.responseType == ResponseType.RequestError)
                {
                    //Console.WriteLine($"Got excetion {result}");
                    message.SetException(new Exception(result));
                }
                if (header.responseType == ResponseType.ClosingError)
                {
                    //Console.WriteLine($"Got excetion {result}");
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
                var message = messageContainer.GetMessage((int)header.callbackIndex);

                ResolveMessage(message, header, buffer, counter);

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

        private static void WriteUint32ToBuffer(UInt32 value, Memory<byte> target, int offset)
        {
            var encodedVal = BitConverter.GetBytes(value);
            //Buffer.BlockCopy(encodedVal, 0, target, offset, encodedVal.Length);
            for (var i = 0; i < encodedVal.Length; i++) {
                target.Span[offset++] = encodedVal[i];
            }
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

        private static int WriteHeaderRequestToBuffer(Memory<byte> buffer, int offset, WriteRequest writeRequest, List<int> argLengths)
        {
            var encoding = Encoding.UTF8;
            var headerLength = getHeaderLength(writeRequest);
            var length = headerLength;
            argLengths.Clear();
            for (var i = 0; i < writeRequest.args.Count; i++)
            {
                var arg = writeRequest.args[i];
                var currentLength = encoding.GetByteCount(arg);
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
            return (argLengths.Count - 1) * 4 + HEADER_LENGTH_IN_BYTES;
        }

        private static async Task WriteToSocket(Socket socket, IDuplexPipe pipe, IEnumerable<WriteRequest> WriteRequests, List<int> argLengths)
        {
            var bytesAdded = 0;
            var output = pipe.Output;
            foreach (var writeRequest in WriteRequests)
            {
                var memory = output.GetMemory(getHeaderLength(writeRequest) + lengthOfStrings(writeRequest) * 3);
                var headerLength = WriteHeaderRequestToBuffer(memory, 0, writeRequest, argLengths);
                //Console.WriteLine($"Header: Writing {Encoding.UTF8.GetString(buffer)} to socket");
                //Console.WriteLine($"C#: Writing {buffer.Length} bytes to socket");
                output.Advance(headerLength);
                for (var i = 0; i < writeRequest.args.Count; i++)
                {
                    var arg = writeRequest.args[i];
                    //Console.WriteLine($"Writing arg {arg} to socket");
                    int encodedLength = Encoding.UTF8.GetByteCount(arg);
                    WriteRaw(output, arg, encodedLength);
                    //Console.WriteLine($"Flush:IsCompleted:{flushResult.IsCompleted}, IsCanceled:{flushResult.IsCanceled}");   
                }
            }
            var flushResult = await output.FlushAsync();
        }

        internal static unsafe void WriteRaw(PipeWriter writer, string value, int expectedLength)
        {
            const int MaxQuickEncodeSize = 512;

            fixed (char* cPtr = value)
            {
                int totalBytes;
                if (expectedLength <= MaxQuickEncodeSize)
                {
                    // encode directly in one hit
                    var span = writer.GetSpan(expectedLength);
                    fixed (byte* bPtr = span)
                    {
                        totalBytes = Encoding.UTF8.GetBytes(cPtr, value.Length, bPtr, expectedLength);
                    }
                    writer.Advance(expectedLength);
                }
                else
                {
                    // use an encoder in a loop
                    var encoder = Encoding.UTF8.GetEncoder();
                    int charsRemaining = value.Length, charOffset = 0;
                    totalBytes = 0;

                    bool final = false;
                    while (true)
                    {
                        var span = writer.GetSpan(5); // get *some* memory - at least enough for 1 character (but hopefully lots more)

                        int charsUsed, bytesUsed;
                        bool completed;
                        fixed (byte* bPtr = span)
                        {
                            //Console.WriteLine($"Writing {value} to socket");
                            encoder.Convert(cPtr + charOffset, charsRemaining, bPtr, span.Length, final, out charsUsed, out bytesUsed, out completed);
                        }
                        writer.Advance(bytesUsed);
                        totalBytes += bytesUsed;
                        charOffset += charsUsed;
                        charsRemaining -= charsUsed;

                        if (charsRemaining <= 0)
                        {
                            if (charsRemaining < 0) throw new Exception("String encode went negative");
                            if (completed) break; // fine
                            if (final) throw new Exception("String encode failed to complete");
                            final = true; // flush the encoder to one more span, then exit
                        }
                    }
                }
                if (totalBytes != expectedLength) throw new Exception("String encode length check failure");
            }
        }

        #endregion private methods

        #region private fields

        private readonly Socket socket;
        private readonly IDuplexPipe pipe;
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
