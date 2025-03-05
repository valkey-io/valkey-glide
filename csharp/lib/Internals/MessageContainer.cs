// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections.Concurrent;

namespace Glide.Internals;


internal class MessageContainer
{
    internal Message<dynamic> GetMessage(int index) => _messages[index];

    internal Message<T> GetMessageForCall<T>(nint[] args) where T : class?
    {
        Message<T> message = GetFreeMessage<T>();
        message.SetupTask(args, this);
        return message;
    }

    private Message<T> GetFreeMessage<T>() where T : class?
    {
        Message<T> message;
        if (!_availableMessages.TryDequeue(out Message<dynamic>? msg))
        {
            lock (_messages)
            {
                int index = _messages.Count;
                message = new Message<T>(index, this);
                //_messages.Add(message);
                _messages.Add((Message<dynamic>)(object)message);
            }
        }
        else
        {
            //message = msg;f
            message = (Message<T>)(object)msg;
        }
        return message;
    }

    public void ReturnFreeMessage<T>(Message<T> message) where T : class?
        => _availableMessages.Enqueue((Message<dynamic>)(object)message);

    internal void DisposeWithError(Exception? error)
    {
        lock (_messages)
        {
            foreach (Message<dynamic>? message in _messages.Where(message => !message.IsCompleted))
            {
                try
                {
                    message.SetException(new TaskCanceledException("Client closed", error));
                }
                catch (Exception) { }
            }
            _messages.Clear();
        }
        _availableMessages.Clear();
    }

    /// This list allows us random-access to the message in each index,
    /// which means that once we receive a callback with an index, we can
    /// find the message to resolve in constant time.
    private readonly List<Message<dynamic>> _messages = [];

    /// This queue contains the messages that were created and are currently unused by any task,
    /// so they can be reused y new tasks instead of allocating new messages.
    private readonly ConcurrentQueue<Message<dynamic>> _availableMessages = new();
}
