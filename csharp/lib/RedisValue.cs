using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using static babushka.AsyncSocketClient;
using static babushka.AsyncSocketClientBase;

namespace babushka
{
    internal enum ValueType : long {
        /// A nil response from the server.
        Nil,
        /// An integer response.  Note that there are a few situations
        /// in which redis actually returns a string for an integer which
        /// is why this library generally treats integers and strings
        /// the same for all numeric responses.
        Int,
        /// An arbitary binary data.
        Data,
        /// A bulk response of more data.  This is generally used by redis
        /// to express nested structures.
        Bulk,
        /// A status response.
        Status,
        /// A status response which represents the string "OK".
        Okay,
    }
 

    public abstract class RedisValueBase {
        internal static RedisValueBase FromCoreValue(IntPtr respPointer)
        {
                RustRedisValue fromValue = Marshal.PtrToStructure<RustRedisValue>(respPointer);
                RedisValueBase result = new RedisNilValue();
                switch (fromValue.Discriminator) {
                    case ValueType.Nil: 
                        result = new RedisNilValue();
                        break;
                    case ValueType.Int: 
                        result = new RedisIntValue() { IntValue = fromValue.IntValue};
                        break;
                    case ValueType.Data: 
                        result = new RedisBinaryValue() { Data = Marshal.PtrToStringAuto(fromValue.DataPointer, (int)fromValue.DataSize) };
                        break;
                    case ValueType.Bulk: 
                        result = new RedisBulkValue();
                        RustRedisBulkValue bulkValue = Marshal.PtrToStructure<RustRedisBulkValue>(respPointer);
                        // result.Values = Array.ConvertAll(bulkValue.Values, new Converter<RustRedisValue, RedisValueBase>(FromCoreValue));
                        break;
                    case ValueType.Status: 
                        result = new RedisStatusValue() { Data = Marshal.PtrToStringAuto(fromValue.DataPointer, (int)fromValue.DataSize) };
                        break;
                    case ValueType.Okay: 
                        result = new RedisOkValue();
                        break;
                }

                return result;
        }
        
        internal static RedisValueBase FromCoreValue(RustRedisValue fromValue)
        {
            switch (fromValue.Discriminator) {
                case ValueType.Nil: return new RedisNilValue();
                case ValueType.Int: return new RedisIntValue() { IntValue = fromValue.IntValue};
                case ValueType.Data: return new RedisBinaryValue() { Data = Marshal.PtrToStringAuto(fromValue.DataPointer, (int)fromValue.DataSize) };
                case ValueType.Bulk: 
                    var result = new RedisBulkValue();
                    // RustRedisBulkValue bulkValue = Marshal.PtrToStructure<RustRedisBulkValue>(respPointer);
                    // result.Values = Array.ConvertAll(bulkValue.Values, new Converter<RustRedisValue, RedisValueBase>(FromCoreValue));
                    return result;
                case ValueType.Status: return new RedisStatusValue() { Data = Marshal.PtrToStringAuto(fromValue.DataPointer, (int)fromValue.DataSize) };
                case ValueType.Okay: return new RedisOkValue();
            }

            return new RedisNilValue();
        }
    }

    public class RedisNilValue : RedisValueBase 
    {
        public override string ToString()
        {
            return "(null)";
        }
    }

    public class RedisOkValue : RedisValueBase
    {
        public override string ToString()
        {
            return "OK";
        }
    }
    
    public class RedisIntValue : RedisValueBase {
        public long IntValue { get; set; } = 0;

        public override string ToString()
        {
            return IntValue.ToString();
        }
    }

    public class RedisBinaryValue : RedisValueBase {
        public string Data { get; set; } = "";

        public override string ToString()
        {
            return $"{Data}";
        }
    }

    public class RedisStatusValue : RedisValueBase {
        public string Data { get; set; } = "";

        public override string ToString()
        {
            return $"{Data}";
        }
    }

    public class RedisBulkValue : RedisValueBase {
        public RedisValueBase[] Values { get; set; } = {};

        public override string ToString()
        {
            return string.Join(",", Array.ConvertAll<RedisValueBase, string?>((RedisValueBase[])Values, Convert.ToString));
        }
    }

}
