// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections.Concurrent;

namespace Valkey.Glide.Internals;

internal class MessageContainer(BaseClient client)
{
    internal Message GetMessage(int index) => _messages[index];

    internal Message GetMessageForCall()
    {
        Message message = GetFreeMessage();
        message.SetupTask(_client);
        return message;
    }

    private Message GetFreeMessage()
    {
        if (!_availableMessages.TryDequeue(out Message? message))
        {
            lock (_messages)
            {
                int index = _messages.Count;
                message = new Message(index, this);
                _messages.Add(message);
            }
        }
        return message;
    }

    public void ReturnFreeMessage(Message message)
        => _availableMessages.Enqueue((Message)(object)message);

    internal void DisposeWithError(Exception? error)
    {
        int countIncompleted = _messages.Count(message => !message.IsCompleted);
        if (countIncompleted > 0)
        {
            Logger.Log(Level.Error, GetType().Name, $"Client is closing, but there are {countIncompleted} ongoing requests");
        }
        lock (_messages)
        {
            foreach (Message? message in _messages.Where(message => !message.IsCompleted))
            {
                try
                {
                    message.SetException(new TaskCanceledException($"Client {_client} closed", error));
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
    private readonly List<Message> _messages = [];

    /// This queue contains the messages that were created and are currently unused by any task,
    /// so they can be reused y new tasks instead of allocating new messages.
    private readonly ConcurrentQueue<Message> _availableMessages = new();

    // Holding the client prevents it from being GC'd until all operations complete.
#pragma warning disable IDE0052 // Remove unread private members
    private readonly BaseClient _client = client;
#pragma warning restore IDE0052 // Remove unread private members
}
