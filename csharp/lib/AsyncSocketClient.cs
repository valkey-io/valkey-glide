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
    /// Simple async client based on protobuf protocol using NetworkStream
    /// </summary>
    public class AsyncSocketClient : AsyncSocketClientBase
    {
        #region public methods

        public static async ValueTask<IAsyncSocketClient?> CreateSocketClient(string host, uint port, bool useTLS)
        {
            var socketName = await GetSocketNameAsync();
            var socket = await GetSocketAsync(socketName, host, port, useTLS);

            // if logger has been initialized by the external-user on info level this log will be shown
            Logger.Log(Level.Info, "connection info", "new connection established");
            return socket == null ? null : new AsyncSocketClient(socket);
        }

        #endregion public methods

        #region Protecte Members
        /// Returns a new ready to use socket.
        protected static async ValueTask<Socket?> GetSocketAsync(string socketName, string host, uint port, bool useTLS)
        {
            var socket = ConnectToSocket(socketName);
            var request = new ConnectionRequest.ConnectionRequest { UseTls = useTLS, ResponseTimeout = 60, ConnectionTimeout = 600 };
            request.Addresses.Add(new ConnectionRequest.AddressInfo() { Host = host, Port = port });
            request.ConnectionRetryStrategy = new ConnectionRequest.ConnectionRetryStrategy { NumberOfRetries = 3, Factor = 10, ExponentBase = 2 };
            using (var stream = new NetworkStream(socket, FileAccess.Write, false))
            {
                WriteToSocket(socket, stream, new[] { request });
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


        #endregion

        #region Private Memebrs

        protected NetworkStream writeStream;

        #endregion

        #region Private Methods

        private AsyncSocketClient(Socket socket)
        {
            this.socket = socket;
            this.writeStream = new NetworkStream(socket, FileAccess.Write, false);
            StartListeningOnReadSocket();
        }

        ~AsyncSocketClient()
        {
            Dispose();
        }

        private void StartListeningOnReadSocket()
        {
            Task.Run(() =>
            {
                using (var stream = new NetworkStream(socket!, FileAccess.Read, false))
                {
                    while (socket!.Connected && !IsDisposed)
                    {
                        try
                        {
                            var responseFromSocket = Response.Response.Parser.ParseDelimitedFrom(stream);
                            Logger.Log(Level.Trace, "StartListeningOnReadSocket", $"read {responseFromSocket} message");
                            if (responseFromSocket != null && responseFromSocket.ValueCase != Response.Response.ValueOneofCase.ClosingError)
                            {
                                uint callbackIndex = responseFromSocket.CallbackIdx;
                                var message = messageContainer.GetMessage((int)callbackIndex);
                                ResolveMessage(message, responseFromSocket);
                            }
                        }
                        catch (Exception exc)
                        {
                            if (socket.Connected)
                            {
                                Logger.Log(Level.Error, "StartListeningOnReadSocket", $"read failed {exc}");
                            }
                            else
                            {
                                DisposeWithError(exc);
                            }
                        }
                    }
                }
            });
        }



        private static Socket ConnectToSocket(string socketAddress)
        {
            var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.IP);
            var endpoint = new UnixDomainSocketEndPoint(socketAddress);
            socket.Blocking = true;
            socket.Connect(endpoint);
            return socket;
        }

        protected override void WriteToSocket(IMessage writeRequest)
        {
            WriteToSocket(socket!, writeStream, new[] { writeRequest });
        }

        private static void WriteToSocket(Socket socket, NetworkStream stream, IEnumerable<IMessage> WriteRequests)
        {
            foreach (var writeRequest in WriteRequests)
            {
                lock (socket)
                {
                    writeRequest.WriteDelimitedTo(stream);
                }
            }
        }

        private static async ValueTask<Response.Response?> ReadFromSocket(Socket socket)
        {
            try
            {
                using (var stream = new NetworkStream(socket, FileAccess.Read, false))
                {
                    var responseFromSocket = await Task.Run(() => Response.Response.Parser.ParseDelimitedFrom(stream));
                    Logger.Log(Level.Info, "ReadFromSocket", $"read {responseFromSocket} message");
                    return responseFromSocket;
                }
            }
            catch (Exception exc)
            {
                Logger.Log(Level.Error, "ReadFromSocket", $"read failed {exc}");
                return null;
            }
        }

        #endregion private methods

    }
}
