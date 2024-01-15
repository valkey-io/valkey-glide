use redis::{cluster_routing::Routable, from_redis_value, Cmd, ErrorKind, RedisResult, Value};

pub(crate) enum ExpectedReturnType {
    Map,
    Double,
    Boolean,
    Set,
}

pub(crate) fn convert_to_expected_type(
    value: Value,
    expected: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    let Some(expected) = expected else {
        return Ok(value);
    };

    match expected {
        ExpectedReturnType::Map => match value {
            Value::Nil => Ok(value),
            Value::Map(_) => Ok(value),
            Value::Array(array) => {
                let mut map = Vec::with_capacity(array.len() / 2);
                let mut iterator = array.into_iter();
                while let Some(key) = iterator.next() {
                    let Some(value) = iterator.next() else {
                        return Err((
                            ErrorKind::TypeError,
                            "Response has odd number of items, and cannot be entered into a map",
                        )
                            .into());
                    };
                    map.push((key, value));
                }

                Ok(Value::Map(map))
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to map",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::Set => match value {
            Value::Nil => Ok(value),
            Value::Set(_) => Ok(value),
            Value::Array(array) => Ok(Value::Set(array)),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to set",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::Double => Ok(Value::Double(from_redis_value::<f64>(&value)?.into())),
        ExpectedReturnType::Boolean => Ok(Value::Boolean(from_redis_value::<bool>(&value)?)),
    }
}

pub(crate) fn expected_type_for_cmd(cmd: &Cmd) -> Option<ExpectedReturnType> {
    let command = cmd.command()?;

    match command.as_slice() {
        b"HGETALL" | b"XREAD" | b"CONFIG GET" | b"FT.CONFIG GET" | b"HELLO" => {
            Some(ExpectedReturnType::Map)
        }
        b"INCRBYFLOAT" | b"HINCRBYFLOAT" => Some(ExpectedReturnType::Double),
        b"HEXISTS" | b"EXPIRE" | b"EXPIREAT" | b"PEXPIRE" | b"PEXPIREAT" => {
            Some(ExpectedReturnType::Boolean)
        }
        b"SMEMBERS" => Some(ExpectedReturnType::Set),
        _ => None,
    }
}
