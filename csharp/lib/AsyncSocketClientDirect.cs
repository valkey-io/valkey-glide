using System;
using System.Buffers;
using System.Collections.Concurrent;
using System.Linq;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Channels;
using Google.Protobuf;

namespace babushka
{
    /// <summary>
    /// Simple async client based on protobuf protocol using MemoryStream for formating the message and directly send to socket
    /// </summary>
    public class AsyncSocketClientDirect : AsyncSocketClientBase
    {
        #region public methods

        public static async ValueTask<IAsyncSocketClient?> CreateSocketClient(string host, uint port, bool useTLS)
        {
            var socketName = await GetSocketNameAsync();
            var socket = await GetSocketAsync(socketName, host, port, useTLS);

            // if logger has been initialized by the external-user on info level this log will be shown
            Logger.Log(Level.Info, "connection info", "new connection established");
            return socket == null ? null : new AsyncSocketClientDirect(socket);
        }



        #endregion 

        #region Private Methods

         /// Returns a new ready to use socket.
        private static async ValueTask<Socket?> GetSocketAsync(string socketName, string host, uint port, bool useTLS)
        {
            var socket = ConnectToSocket(socketName);
            var request = new ConnectionRequest.ConnectionRequest { UseTls = useTLS, ResponseTimeout = 60, ConnectionTimeout = 600 };
            request.Addresses.Add(new ConnectionRequest.AddressInfo() { Host = host, Port = port} );
            request.ConnectionRetryStrategy = new ConnectionRequest.ConnectionRetryStrategy { NumberOfRetries = 3, Factor = 10, ExponentBase = 2 };
            using (MemoryStream stream = new MemoryStream())
            {
                request.WriteDelimitedTo(stream);
                byte[] messageBuffer = stream.ToArray();
                if (messageBuffer.Length > 0) 
                {
                    // lock(socket) 
                    {
                        socket.Send(messageBuffer);
                    }
                }
            }            
            var response = await ReadFromSocket(socket);
            if (response == null) 
            {
                Logger.Log(Level.Error, "AsyncSocketClient.GetSocketAsync", "socket connection failed");
                socket.Dispose();
                return null;
            }
            return socket;
        }

        private AsyncSocketClientDirect(Socket socket)
        {
            base.socket = socket;
            StartListeningOnReadSocket();
        }

        ~AsyncSocketClientDirect()
        {
            Dispose();
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

        
        private ArraySegment<byte> ParseReadResults(byte[] buffer, int messageLength, MessageContainer messageContainer)
        {
            var counter = 0;
            while (counter < messageLength)
            {
                int firstMessageLength = (int)buffer[counter];
                if (counter + firstMessageLength + 1 > messageLength)
                {
                    return new ArraySegment<byte>(buffer, counter, messageLength - counter);
                }
                using (MemoryStream stream = new MemoryStream(buffer, counter, messageLength - counter, false))
                {
                    var responseFromSocket = Response.Response.Parser.ParseDelimitedFrom(stream);
                    uint callbackIndex = responseFromSocket.CallbackIdx;
                    var message = messageContainer.GetMessage((int)callbackIndex);
                    ResolveMessage(message, responseFromSocket);
                    counter += responseFromSocket.CalculateSize() + 1;
                }
            }

            return new ArraySegment<byte>(buffer, counter, messageLength - counter);
        }
        private void StartListeningOnReadSocket()
        {
            Task.Run(() =>
            {
                var previousSegment = new ArraySegment<byte>();
                while (socket!.Connected && !IsDisposed)
                {
                    try
                    {
                        var buffer = GetBuffer(previousSegment);
                        var segmentAfterPreviousData = new ArraySegment<byte>(buffer, previousSegment.Count, buffer.Length - previousSegment.Count);
                        var receivedLength = socket.Receive(segmentAfterPreviousData, SocketFlags.None);
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
                    catch (SocketException exception) 
                    {
                        if (socket.Connected && exception.SocketErrorCode != SocketError.WouldBlock && exception.SocketErrorCode != SocketError.Interrupted)
                        {
                            DisposeWithError(exception);
                            break;
                        }
                    }
                    catch (Exception exc)
                    {
                        if (socket.Connected) 
                        {
                            DisposeWithError(exc);
                            break;
                        }
                    }
                }
            });
        }

        protected static Socket ConnectToSocket(string socketAddress)
        {
            var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.IP);
            var endpoint = new UnixDomainSocketEndPoint(socketAddress);
            socket.Blocking = true;
            socket.Connect(endpoint);
            return socket;
        }

        protected override void WriteToSocket(IMessage writeRequest)
        {
            WriteToSocket(socket!, new [] { writeRequest });
        }

        private static void WriteToSocket(Socket socket, IEnumerable<IMessage> WriteRequests)
        {
            foreach (var writeRequest in WriteRequests)
            {
                
                using (MemoryStream memstream = new MemoryStream())
                {
                    writeRequest.WriteDelimitedTo(memstream);
                    byte[] messageBuffer = memstream.ToArray();
                    if (messageBuffer.Length > 0) 
                    {
                        // lock(socket) 
                        {
                            int written = socket.Send(messageBuffer);
                        }
                    }
                }    
            }
        }

        private static async ValueTask<Response.Response?> ReadFromSocket(Socket socket) 
        {
            try
            {
                var buffer = new byte[10];
                var previousSegment = new ArraySegment<byte>(buffer);
                var receivedLength = await socket.ReceiveAsync(previousSegment, SocketFlags.None);
                if (receivedLength > 0)
                {                
                    using (MemoryStream stream = new MemoryStream(buffer, 0, receivedLength, false))
                    {
                        var responseFromSocket = Response.Response.Parser.ParseDelimitedFrom(stream);
                        return responseFromSocket;
                    }
                }
                return null;               
            }
            catch (Exception exc)
            {
                Logger.Log(Level.Error, "ReadFromSocket", $"read failed {exc}");
                return null;
            }
        }

        #endregion

    }
}
