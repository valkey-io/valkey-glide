/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */
use redis::{
    cluster_routing::Routable, from_owned_redis_value, Cmd, ErrorKind, RedisResult, Value,
};

#[derive(Clone, Copy)]
pub(crate) enum ExpectedReturnType {
    Map,
    MapOfStringToDouble,
    Double,
    Boolean,
    BulkString,
    Set,
    DoubleOrNull,
    ZRankReturnType,
    JsonToggleReturnType,
    ArrayOfBools,
    ArrayOfDoubleOrNull,
    Lolwut,
    ArrayOfArraysOfDoubleOrNull,
    ArrayOfPairs,
    ArrayOfMemberScorePairs,
    ZMPopReturnType,
    KeyWithMemberAndScore,
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
            Value::Array(array) => convert_array_to_map(array, None, None),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to map",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::MapOfStringToDouble => match value {
            Value::Nil => Ok(value),
            Value::Map(map) => {
                let map_clone = map.clone();
                let result = map
                    .into_iter()
                    .map(|(key, inner_value)| {
                        let key_str = match key {
                            Value::BulkString(_) => key,
                            _ => Value::BulkString(from_owned_redis_value::<String>(key)?.into()),
                        };
                        match inner_value {
                            Value::BulkString(_) => Ok((
                                key_str,
                                Value::Double(from_owned_redis_value::<f64>(inner_value)?),
                            )),
                            Value::Double(_) => Ok((key_str, inner_value)),
                            _ => Err((
                                ErrorKind::TypeError,
                                "Response couldn't be converted to map of {string: double}",
                                format!("(response was {:?})", map_clone),
                            )
                                .into()),
                        }
                    })
                    .collect::<RedisResult<_>>();

                result.map(Value::Map)
            }
            Value::Array(array) => convert_array_to_map(
                array,
                Some(ExpectedReturnType::BulkString),
                Some(ExpectedReturnType::Double),
            ),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to map of {string: double}",
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
        ExpectedReturnType::Double => Ok(Value::Double(from_owned_redis_value::<f64>(value)?)),
        ExpectedReturnType::Boolean => Ok(Value::Boolean(from_owned_redis_value::<bool>(value)?)),
        ExpectedReturnType::DoubleOrNull => match value {
            Value::Nil => Ok(value),
            _ => Ok(Value::Double(from_owned_redis_value::<f64>(value)?)),
        },
        ExpectedReturnType::ZRankReturnType => match value {
            Value::Nil => Ok(value),
            Value::Array(mut array) => {
                if array.len() != 2 {
                    return Err((
                        ErrorKind::TypeError,
                        "Array must contain exactly two elements",
                    )
                        .into());
                }

                array[1] =
                    convert_to_expected_type(array[1].clone(), Some(ExpectedReturnType::Double))?;

                Ok(Value::Array(array))
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to Array (ZRankResponseType)",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::BulkString => Ok(Value::BulkString(
            from_owned_redis_value::<String>(value)?.into(),
        )),
        ExpectedReturnType::JsonToggleReturnType => match value {
            Value::Array(array) => {
                let converted_array: RedisResult<Vec<_>> = array
                    .into_iter()
                    .map(|item| match item {
                        Value::Nil => Ok(Value::Nil),
                        _ => match from_owned_redis_value::<bool>(item.clone()) {
                            Ok(boolean_value) => Ok(Value::Boolean(boolean_value)),
                            _ => Err((
                                ErrorKind::TypeError,
                                "Could not convert value to boolean",
                                format!("(value was {:?})", item),
                            )
                                .into()),
                        },
                    })
                    .collect();

                converted_array.map(Value::Array)
            }
            Value::BulkString(bytes) => match std::str::from_utf8(&bytes) {
                Ok("true") => Ok(Value::Boolean(true)),
                Ok("false") => Ok(Value::Boolean(false)),
                _ => Err((
                    ErrorKind::TypeError,
                    "Response couldn't be converted to boolean",
                    format!("(response was {:?})", bytes),
                )
                    .into()),
            },
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to Json Toggle return type",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::ArrayOfBools => match value {
            Value::Array(array) => convert_array_elements(array, ExpectedReturnType::Boolean),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array of boolean",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::ArrayOfDoubleOrNull => match value {
            Value::Array(array) => convert_array_elements(array, ExpectedReturnType::DoubleOrNull),
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array of doubles",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        // command returns nil or an array of 2 elements, where the second element is a map represented by a 2D array
        // we convert that second element to a map as we do in `MapOfStringToDouble`
        /*
        > zmpop 1 z1 min count 10
        1) "z1"
        2) 1) 1) "2"
              2) (double) 2
           2) 1) "3"
              2) (double) 3
         */
        ExpectedReturnType::ZMPopReturnType => match value {
            Value::Nil => Ok(value),
            Value::Array(array) if array.len() == 2 && matches!(array[1], Value::Array(_)) => {
                let Value::Array(nested_array) = array[1].clone() else {
                    unreachable!("Pattern match above ensures that it is Array")
                };
                // convert the nested array to a map
                let map = convert_array_to_map(
                    nested_array,
                    Some(ExpectedReturnType::BulkString),
                    Some(ExpectedReturnType::Double),
                )?;

                Ok(Value::Array(vec![array[0].clone(), map]))
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to ZMPOP return type",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::ArrayOfArraysOfDoubleOrNull => match value {
            // This is used for GEOPOS command.
            Value::Array(array) => {
                let converted_array: RedisResult<Vec<_>> = array
                    .clone()
                    .into_iter()
                    .map(|item| match item {
                        Value::Nil => Ok(Value::Nil),
                        Value::Array(mut inner_array) => {
                            if inner_array.len() != 2 {
                                return Err((
                                    ErrorKind::TypeError,
                                    "Inner Array must contain exactly two elements",
                                )
                                    .into());
                            }
                            inner_array[0] = convert_to_expected_type(
                                inner_array[0].clone(),
                                Some(ExpectedReturnType::Double),
                            )?;
                            inner_array[1] = convert_to_expected_type(
                                inner_array[1].clone(),
                                Some(ExpectedReturnType::Double),
                            )?;

                            Ok(Value::Array(inner_array))
                        }
                        _ => Err((
                            ErrorKind::TypeError,
                            "Response couldn't be converted to an array of array of double or null. Inner value of Array must be Array or Null",
                            format!("(Inner value was {:?})", item),
                        )
                            .into()),
                    })
                    .collect();

                converted_array.map(Value::Array)
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array of array of double or null",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::Lolwut => {
            match value {
                // cluster (multi-node) response - go recursive
                Value::Map(map) => {
                    let result = map
                        .into_iter()
                        .map(|(key, inner_value)| {
                            let converted_key = convert_to_expected_type(
                                key,
                                Some(ExpectedReturnType::BulkString),
                            )?;
                            let converted_value = convert_to_expected_type(
                                inner_value,
                                Some(ExpectedReturnType::Lolwut),
                            )?;
                            Ok((converted_key, converted_value))
                        })
                        .collect::<RedisResult<_>>();

                    result.map(Value::Map)
                }
                // RESP 2 response
                Value::BulkString(bytes) => {
                    let text = std::str::from_utf8(&bytes).unwrap();
                    let res = convert_lolwut_string(text);
                    Ok(Value::BulkString(Vec::from(res)))
                }
                // RESP 3 response
                Value::VerbatimString {
                    format: _,
                    ref text,
                } => {
                    let res = convert_lolwut_string(text);
                    Ok(Value::BulkString(Vec::from(res)))
                }
                _ => Err((
                    ErrorKind::TypeError,
                    "LOLWUT response couldn't be converted to a user-friendly format",
                    (&format!("(response was {:?}...)", value)[..100]).into(),
                )
                    .into()),
            }
        }
        // Used by HRANDFIELD when the WITHVALUES arg is passed.
        // The server response can be an empty array, a flat array of key-value pairs, or a two-dimensional array of key-value pairs.
        // The conversions we do here are as follows:
        //
        // - if the server returned an empty array, return an empty array
        // - if the server returned a flat array of key-value pairs, convert to a two-dimensional array of key-value pairs
        // - if the server returned a two-dimensional array of key-value pairs, return as-is
        ExpectedReturnType::ArrayOfPairs => convert_to_array_of_pairs(value, None),
        // Used by ZRANDMEMBER when the WITHSCORES arg is passed.
        // The server response can be an empty array, a flat array of member-score pairs, or a two-dimensional array of member-score pairs.
        // The server response scores can be strings or doubles. The conversions we do here are as follows:
        //
        // - if the server returned an empty array, return an empty array
        // - if the server returned a flat array of member-score pairs, convert to a two-dimensional array of member-score pairs. The scores are converted from type string to type double.
        // - if the server returned a two-dimensional array of key-value pairs, return as-is. The scores will already be of type double since this is a RESP3 response.
        ExpectedReturnType::ArrayOfMemberScorePairs => {
            // RESP2 returns scores as strings, but we want scores as type double.
            convert_to_array_of_pairs(value, Some(ExpectedReturnType::Double))
        }
        // Used by BZPOPMIN/BZPOPMAX, which return an array consisting of the key of the sorted set that was popped, the popped member, and its score.
        // RESP2 returns the score as a string, but RESP3 returns the score as a double. Here we convert string scores into type double.
        ExpectedReturnType::KeyWithMemberAndScore => match value {
            Value::Nil => Ok(value),
            Value::Array(ref array) if array.len() == 3 && matches!(array[2], Value::Double(_)) => {
                Ok(value)
            }
            Value::Array(mut array)
                if array.len() == 3
                    && matches!(array[2], Value::BulkString(_) | Value::SimpleString(_)) =>
            {
                array[2] =
                    convert_to_expected_type(array[2].clone(), Some(ExpectedReturnType::Double))?;
                Ok(Value::Array(array))
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted to an array containing a key, member, and score",
                format!("(response was {:?})", value),
            )
                .into()),
        },
    }
}

/// Convert string returned by `LOLWUT` command.
/// The input string is shell-friendly and contains color codes and escape sequences.
/// The output string is user-friendly, colored whitespaces replaced with corresponding symbols.
fn convert_lolwut_string(data: &str) -> String {
    if data.contains("\x1b[0m") {
        data.replace("\x1b[0;97;107m \x1b[0m", "\u{2591}")
            .replace("\x1b[0;37;47m \x1b[0m", "\u{2592}")
            .replace("\x1b[0;90;100m \x1b[0m", "\u{2593}")
            .replace("\x1b[0;30;40m \x1b[0m", " ")
    } else {
        data.to_owned()
    }
}

/// Converts elements in an array to the specified type.
///
/// `array` is an array of values.
/// `element_type` is the type that the array elements should be converted to.
fn convert_array_elements(
    array: Vec<Value>,
    element_type: ExpectedReturnType,
) -> RedisResult<Value> {
    let converted_array = array
        .iter()
        .map(|v| convert_to_expected_type(v.clone(), Some(element_type)).unwrap())
        .collect();
    Ok(Value::Array(converted_array))
}

fn convert_array_to_map(
    array: Vec<Value>,
    key_expected_return_type: Option<ExpectedReturnType>,
    value_expected_return_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    let mut map = Vec::new();
    let mut iterator = array.into_iter();
    while let Some(key) = iterator.next() {
        match key {
            Value::Array(inner_array) => {
                if inner_array.len() != 2 {
                    return Err((
                        ErrorKind::TypeError,
                        "Array inside map must contain exactly two elements",
                    )
                        .into());
                }
                let mut inner_iterator = inner_array.into_iter();
                let Some(inner_key) = inner_iterator.next() else {
                    return Err((ErrorKind::TypeError, "Missing key inside array of map").into());
                };
                let Some(inner_value) = inner_iterator.next() else {
                    return Err((ErrorKind::TypeError, "Missing value inside array of map").into());
                };

                map.push((
                    convert_to_expected_type(inner_key, key_expected_return_type)?,
                    convert_to_expected_type(inner_value, value_expected_return_type)?,
                ));
            }
            _ => {
                let Some(value) = iterator.next() else {
                    return Err((
                        ErrorKind::TypeError,
                        "Response has odd number of items, and cannot be entered into a map",
                    )
                        .into());
                };
                map.push((
                    convert_to_expected_type(key, key_expected_return_type)?,
                    convert_to_expected_type(value, value_expected_return_type)?,
                ));
            }
        }
    }
    Ok(Value::Map(map))
}

/// Used by commands like ZRANDMEMBER and HRANDFIELD. Normally a map would be more suitable for these key-value responses, but these commands may return duplicate key-value pairs depending on the command arguments. These duplicated pairs cannot be represented by a map.
///
/// Converts a server response as follows:
/// - if the server returned an empty array, return an empty array.
/// - if the server returned a flat array (RESP2), convert it to a two-dimensional array, where the inner arrays are length=2 arrays representing key-value pairs.
/// - if the server returned a two-dimensional array (RESP3), return the response as is, since it is already in the correct format.
/// - otherwise, return an error.
///
/// `response` is a server response that we should attempt to convert as described above.
/// `value_expected_return_type` indicates the desired return type of the values in the key-value pairs. The values will only be converted if the response was a flat array, since RESP3 already returns an array of pairs with values already of the correct type.
fn convert_to_array_of_pairs(
    response: Value,
    value_expected_return_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    match response {
        Value::Array(ref array) if array.is_empty() || matches!(array[0], Value::Array(_)) => {
            // The server response is an empty array or a RESP3 array of pairs. In RESP3, the values in the pairs are
            // already of the correct type, so we do not need to convert them and `response` is in the correct format.
            Ok(response)
        }
        Value::Array(array)
            if array.len() % 2 == 0
                && matches!(array[0], Value::BulkString(_) | Value::SimpleString(_)) =>
        {
            // The server response is a RESP2 flat array with keys at even indices and their associated values at
            // odd indices.
            convert_flat_array_to_array_of_pairs(array, value_expected_return_type)
        }
        _ => Err((
            ErrorKind::TypeError,
            "Response couldn't be converted to an array of key-value pairs",
            format!("(response was {:?})", response),
        )
            .into()),
    }
}

/// Converts a flat array of values to a two-dimensional array, where the inner arrays are length=2 arrays representing key-value pairs. Normally a map would be more suitable for these responses, but some commands (eg HRANDFIELD) may return duplicate key-value pairs depending on the command arguments. These duplicated pairs cannot be represented by a map.
///
/// `array` is a flat array containing keys at even-positioned elements and their associated values at odd-positioned elements.
/// `value_expected_return_type` indicates the desired return type of the values in the key-value pairs.
fn convert_flat_array_to_array_of_pairs(
    array: Vec<Value>,
    value_expected_return_type: Option<ExpectedReturnType>,
) -> RedisResult<Value> {
    if array.len() % 2 != 0 {
        return Err((
            ErrorKind::TypeError,
            "Response has odd number of items, and cannot be converted to an array of key-value pairs"
        )
            .into());
    }

    let mut result = Vec::with_capacity(array.len() / 2);
    for i in (0..array.len()).step_by(2) {
        let key = array[i].clone();
        let value = convert_to_expected_type(array[i + 1].clone(), value_expected_return_type)?;
        let pair = vec![key, value];
        result.push(Value::Array(pair));
    }
    Ok(Value::Array(result))
}

pub(crate) fn expected_type_for_cmd(cmd: &Cmd) -> Option<ExpectedReturnType> {
    let command = cmd.command()?;

    // TODO use enum to avoid mistakes
    match command.as_slice() {
        b"HGETALL" | b"XREAD" | b"CONFIG GET" | b"FT.CONFIG GET" | b"HELLO" => {
            Some(ExpectedReturnType::Map)
        }
        b"INCRBYFLOAT" | b"HINCRBYFLOAT" => Some(ExpectedReturnType::Double),
        b"HEXISTS" | b"HSETNX" | b"EXPIRE" | b"EXPIREAT" | b"PEXPIRE" | b"PEXPIREAT"
        | b"SISMEMBER" | b"PERSIST" | b"SMOVE" | b"RENAMENX" => Some(ExpectedReturnType::Boolean),
        b"SMISMEMBER" => Some(ExpectedReturnType::ArrayOfBools),
        b"SMEMBERS" | b"SINTER" | b"SDIFF" => Some(ExpectedReturnType::Set),
        b"ZSCORE" | b"GEODIST" => Some(ExpectedReturnType::DoubleOrNull),
        b"ZMSCORE" => Some(ExpectedReturnType::ArrayOfDoubleOrNull),
        b"ZPOPMIN" | b"ZPOPMAX" => Some(ExpectedReturnType::MapOfStringToDouble),
        b"BZMPOP" | b"ZMPOP" => Some(ExpectedReturnType::ZMPopReturnType),
        b"JSON.TOGGLE" => Some(ExpectedReturnType::JsonToggleReturnType),
        b"GEOPOS" => Some(ExpectedReturnType::ArrayOfArraysOfDoubleOrNull),
        b"HRANDFIELD" => cmd
            .position(b"WITHVALUES")
            .map(|_| ExpectedReturnType::ArrayOfPairs),
        b"ZRANDMEMBER" => cmd
            .position(b"WITHSCORES")
            .map(|_| ExpectedReturnType::ArrayOfMemberScorePairs),
        b"ZADD" => cmd
            .position(b"INCR")
            .map(|_| ExpectedReturnType::DoubleOrNull),
        b"ZRANGE" | b"ZDIFF" | b"ZUNION" => cmd
            .position(b"WITHSCORES")
            .map(|_| ExpectedReturnType::MapOfStringToDouble),
        b"ZRANK" | b"ZREVRANK" => cmd
            .position(b"WITHSCORE")
            .map(|_| ExpectedReturnType::ZRankReturnType),
        b"BZPOPMIN" | b"BZPOPMAX" => Some(ExpectedReturnType::KeyWithMemberAndScore),
        b"SPOP" => {
            if cmd.arg_idx(2).is_some() {
                Some(ExpectedReturnType::Set)
            } else {
                None
            }
        }
        b"LOLWUT" => Some(ExpectedReturnType::Lolwut),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn convert_lolwut() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("LOLWUT").arg("version").arg("42")),
            Some(ExpectedReturnType::Lolwut)
        ));

        let redis_string : String = "\x1b[0;97;107m \x1b[0m--\x1b[0;37;47m \x1b[0m--\x1b[0;90;100m \x1b[0m--\x1b[0;30;40m \x1b[0m".into();
        let expected: String = "\u{2591}--\u{2592}--\u{2593}-- ".into();

        let converted_1 = convert_to_expected_type(
            Value::BulkString(redis_string.clone().into_bytes()),
            Some(ExpectedReturnType::Lolwut),
        );
        assert_eq!(
            Value::BulkString(expected.clone().into_bytes()),
            converted_1.unwrap()
        );

        let converted_2 = convert_to_expected_type(
            Value::VerbatimString {
                format: redis::VerbatimFormat::Text,
                text: redis_string.clone(),
            },
            Some(ExpectedReturnType::Lolwut),
        );
        assert_eq!(
            Value::BulkString(expected.clone().into_bytes()),
            converted_2.unwrap()
        );

        let converted_3 = convert_to_expected_type(
            Value::Map(vec![
                (
                    Value::SimpleString("node 1".into()),
                    Value::BulkString(redis_string.clone().into_bytes()),
                ),
                (
                    Value::SimpleString("node 2".into()),
                    Value::BulkString(redis_string.clone().into_bytes()),
                ),
            ]),
            Some(ExpectedReturnType::Lolwut),
        );
        assert_eq!(
            Value::Map(vec![
                (
                    Value::BulkString("node 1".into()),
                    Value::BulkString(expected.clone().into_bytes())
                ),
                (
                    Value::BulkString("node 2".into()),
                    Value::BulkString(expected.clone().into_bytes())
                ),
            ]),
            converted_3.unwrap()
        );

        let converted_4 = convert_to_expected_type(
            Value::SimpleString(redis_string.clone()),
            Some(ExpectedReturnType::Lolwut),
        );
        assert!(converted_4.is_err());
    }

    #[test]
    fn convert_smismember() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("SMISMEMBER").arg("key").arg("elem")),
            Some(ExpectedReturnType::ArrayOfBools)
        ));

        let redis_response = Value::Array(vec![Value::Int(0), Value::Int(1)]);
        let converted_response =
            convert_to_expected_type(redis_response, Some(ExpectedReturnType::ArrayOfBools))
                .unwrap();
        let expected_response = Value::Array(vec![Value::Boolean(false), Value::Boolean(true)]);
        assert_eq!(expected_response, converted_response);
    }

    #[test]
    fn convert_to_array_of_pairs_return_type() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("HRANDFIELD")
                    .arg("key")
                    .arg("1")
                    .arg("withvalues")
            ),
            Some(ExpectedReturnType::ArrayOfPairs)
        ));

        assert!(expected_type_for_cmd(redis::cmd("HRANDFIELD").arg("key").arg("1")).is_none());
        assert!(expected_type_for_cmd(redis::cmd("HRANDFIELD").arg("key")).is_none());

        let flat_array = Value::Array(vec![
            Value::BulkString(b"key1".to_vec()),
            Value::BulkString(b"value1".to_vec()),
            Value::BulkString(b"key2".to_vec()),
            Value::BulkString(b"value2".to_vec()),
        ]);
        let two_dimensional_array = Value::Array(vec![
            Value::Array(vec![
                Value::BulkString(b"key1".to_vec()),
                Value::BulkString(b"value1".to_vec()),
            ]),
            Value::Array(vec![
                Value::BulkString(b"key2".to_vec()),
                Value::BulkString(b"value2".to_vec()),
            ]),
        ]);
        let converted_flat_array =
            convert_to_expected_type(flat_array, Some(ExpectedReturnType::ArrayOfPairs)).unwrap();
        assert_eq!(two_dimensional_array, converted_flat_array);

        let converted_two_dimensional_array = convert_to_expected_type(
            two_dimensional_array.clone(),
            Some(ExpectedReturnType::ArrayOfPairs),
        )
        .unwrap();
        assert_eq!(two_dimensional_array, converted_two_dimensional_array);

        let empty_array = Value::Array(vec![]);
        let converted_empty_array =
            convert_to_expected_type(empty_array.clone(), Some(ExpectedReturnType::ArrayOfPairs))
                .unwrap();
        assert_eq!(empty_array, converted_empty_array);

        let flat_array_unexpected_length =
            Value::Array(vec![Value::BulkString(b"somekey".to_vec())]);
        assert!(convert_to_expected_type(
            flat_array_unexpected_length,
            Some(ExpectedReturnType::ArrayOfPairs)
        )
        .is_err());
    }

    #[test]
    fn convert_zmpop_response() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("BZMPOP").arg(1).arg(1).arg("key").arg("min")),
            Some(ExpectedReturnType::ZMPopReturnType)
        ));
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZMPOP").arg(1).arg(1).arg("key").arg("min")),
            Some(ExpectedReturnType::ZMPopReturnType)
        ));

        let redis_response = Value::Array(vec![
            Value::SimpleString("key".into()),
            Value::Array(vec![
                Value::Array(vec![Value::SimpleString("elem1".into()), Value::Double(1.)]),
                Value::Array(vec![Value::SimpleString("elem2".into()), Value::Double(2.)]),
            ]),
        ]);
        let converted_response =
            convert_to_expected_type(redis_response, Some(ExpectedReturnType::ZMPopReturnType))
                .unwrap();
        let expected_response = Value::Array(vec![
            Value::SimpleString("key".into()),
            Value::Map(vec![
                (Value::BulkString("elem1".into()), Value::Double(1.)),
                (Value::BulkString("elem2".into()), Value::Double(2.)),
            ]),
        ]);
        assert_eq!(expected_response, converted_response);

        let redis_response = Value::Nil;
        let converted_response = convert_to_expected_type(
            redis_response.clone(),
            Some(ExpectedReturnType::ZMPopReturnType),
        )
        .unwrap();
        assert_eq!(redis_response, converted_response);
    }

    #[test]
    fn convert_to_member_score_pairs_return_type() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZRANDMEMBER")
                    .arg("key")
                    .arg("1")
                    .arg("withscores")
            ),
            Some(ExpectedReturnType::ArrayOfMemberScorePairs)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZRANDMEMBER").arg("key").arg("1")).is_none());
        assert!(expected_type_for_cmd(redis::cmd("ZRANDMEMBER").arg("key")).is_none());

        // convert_to_array_of_pairs_return_type already tests most functionality since the conversion for ArrayOfPairs
        // and ArrayOfMemberScorePairs is mostly the same. Here we also test that the scores are converted to double
        // when the server response was a RESP2 flat array.
        let flat_array = Value::Array(vec![
            Value::BulkString(b"one".to_vec()),
            Value::BulkString(b"1.0".to_vec()),
            Value::BulkString(b"two".to_vec()),
            Value::BulkString(b"2.0".to_vec()),
        ]);
        let expected_response = Value::Array(vec![
            Value::Array(vec![Value::BulkString(b"one".to_vec()), Value::Double(1.0)]),
            Value::Array(vec![Value::BulkString(b"two".to_vec()), Value::Double(2.0)]),
        ]);
        let converted_flat_array = convert_to_expected_type(
            flat_array,
            Some(ExpectedReturnType::ArrayOfMemberScorePairs),
        )
        .unwrap();
        assert_eq!(expected_response, converted_flat_array);
    }

    #[test]
    fn convert_zadd_only_if_incr_is_included() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("zadd")
                    .arg("XT")
                    .arg("CH")
                    .arg("incr")
                    .arg("0.6")
                    .arg("foo")
            ),
            Some(ExpectedReturnType::DoubleOrNull)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("zadd").arg("XT").arg("CH").arg("0.6").arg("foo")
        )
        .is_none());
    }

    #[test]
    fn convert_zrange_zdiff_only_if_withsocres_is_included() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("zrange").arg("0").arg("-1").arg("withscores")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZRANGE").arg("0").arg("-1")).is_none());

        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZDIFF").arg("1").arg("withscores")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZDIFF").arg("1")).is_none());
    }

    #[test]
    fn convert_zunion_only_if_withscores_is_included() {
        // Test ZUNION without options
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZUNION")
                    .arg("2")
                    .arg("set1")
                    .arg("set2")
                    .arg("WITHSCORES")
            ),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(
            expected_type_for_cmd(redis::cmd("ZUNION").arg("2").arg("set1").arg("set2")).is_none()
        );

        // Test ZUNION with Weights
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZUNION")
                    .arg("2")
                    .arg("set1")
                    .arg("set2")
                    .arg("WEIGHTS")
                    .arg("1")
                    .arg("2")
                    .arg("WITHSCORES")
            ),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("ZUNION")
                .arg("2")
                .arg("set1")
                .arg("set2")
                .arg("WEIGHTS")
                .arg("1")
                .arg("2")
        )
        .is_none());

        // Test ZUNION with Aggregate
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZUNION")
                    .arg("2")
                    .arg("set1")
                    .arg("set2")
                    .arg("AGGREGATE")
                    .arg("MAX")
                    .arg("WITHSCORES")
            ),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("ZUNION")
                .arg("2")
                .arg("set1")
                .arg("set2")
                .arg("AGGREGATE")
                .arg("MAX")
        )
        .is_none());

        // Test ZUNION with Weights and Aggregate
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZUNION")
                    .arg("2")
                    .arg("set1")
                    .arg("set2")
                    .arg("WEIGHTS")
                    .arg("1")
                    .arg("2")
                    .arg("AGGREGATE")
                    .arg("MAX")
                    .arg("WITHSCORES")
            ),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("ZUNION")
                .arg("2")
                .arg("set1")
                .arg("set2")
                .arg("WEIGHTS")
                .arg("1")
                .arg("2")
                .arg("AGGREGATE")
                .arg("MAX")
        )
        .is_none());
    }

    #[test]
    fn zpopmin_zpopmax_return_type() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZPOPMIN").arg("1")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZPOPMAX").arg("1")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));
    }

    #[test]
    fn convert_bzpopmin_bzpopmax() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("BZPOPMIN")
                    .arg("myzset1")
                    .arg("myzset2")
                    .arg("1")
            ),
            Some(ExpectedReturnType::KeyWithMemberAndScore)
        ));

        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("BZPOPMAX")
                    .arg("myzset1")
                    .arg("myzset2")
                    .arg("1")
            ),
            Some(ExpectedReturnType::KeyWithMemberAndScore)
        ));

        let array_with_double_score = Value::Array(vec![
            Value::BulkString(b"key1".to_vec()),
            Value::BulkString(b"member1".to_vec()),
            Value::Double(2.0),
        ]);
        let result = convert_to_expected_type(
            array_with_double_score.clone(),
            Some(ExpectedReturnType::KeyWithMemberAndScore),
        )
        .unwrap();
        assert_eq!(array_with_double_score, result);

        let array_with_string_score = Value::Array(vec![
            Value::BulkString(b"key1".to_vec()),
            Value::BulkString(b"member1".to_vec()),
            Value::BulkString(b"2.0".to_vec()),
        ]);
        let result = convert_to_expected_type(
            array_with_string_score.clone(),
            Some(ExpectedReturnType::KeyWithMemberAndScore),
        )
        .unwrap();
        assert_eq!(array_with_double_score, result);

        let converted_nil_value =
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::KeyWithMemberAndScore))
                .unwrap();
        assert_eq!(Value::Nil, converted_nil_value);

        let array_with_unexpected_length = Value::Array(vec![
            Value::BulkString(b"key1".to_vec()),
            Value::BulkString(b"member1".to_vec()),
            Value::Double(2.0),
            Value::Double(2.0),
        ]);
        assert!(convert_to_expected_type(
            array_with_unexpected_length,
            Some(ExpectedReturnType::KeyWithMemberAndScore)
        )
        .is_err());
    }

    #[test]
    fn convert_zank_zrevrank_only_if_withsocres_is_included() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("zrank")
                    .arg("key")
                    .arg("member")
                    .arg("withscore")
            ),
            Some(ExpectedReturnType::ZRankReturnType)
        ));

        assert!(expected_type_for_cmd(redis::cmd("zrank").arg("key").arg("member")).is_none());

        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZREVRANK")
                    .arg("key")
                    .arg("member")
                    .arg("withscore")
            ),
            Some(ExpectedReturnType::ZRankReturnType)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZREVRANK").arg("key").arg("member")).is_none());
    }

    #[test]
    fn convert_zmscore() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZMSCORE").arg("key").arg("member")),
            Some(ExpectedReturnType::ArrayOfDoubleOrNull)
        ));

        let array_response = Value::Array(vec![
            Value::Nil,
            Value::Double(1.5),
            Value::BulkString(b"2.5".to_vec()),
        ]);
        let converted_response = convert_to_expected_type(
            array_response,
            Some(ExpectedReturnType::ArrayOfDoubleOrNull),
        )
        .unwrap();
        let expected_response =
            Value::Array(vec![Value::Nil, Value::Double(1.5), Value::Double(2.5)]);
        assert_eq!(expected_response, converted_response);

        let unexpected_response_type = Value::Double(0.5);
        assert!(convert_to_expected_type(
            unexpected_response_type,
            Some(ExpectedReturnType::ArrayOfDoubleOrNull)
        )
        .is_err());
    }

    #[test]
    fn convert_smove_to_bool() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("SMOVE").arg("key1").arg("key2").arg("elem")),
            Some(ExpectedReturnType::Boolean)
        ));
    }

    #[test]
    fn test_convert_to_map_of_string_to_double() {
        assert_eq!(
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::MapOfStringToDouble)),
            Ok(Value::Nil)
        );
        let redis_map = vec![
            (
                Value::BulkString(b"key1".to_vec()),
                Value::BulkString(b"10.5".to_vec()),
            ),
            (
                Value::BulkString(b"key2".to_vec()),
                Value::BulkString(b"20.8".to_vec()),
            ),
            (Value::Double(20.5), Value::BulkString(b"30.2".to_vec())),
        ];

        let converted_map = convert_to_expected_type(
            Value::Map(redis_map),
            Some(ExpectedReturnType::MapOfStringToDouble),
        )
        .unwrap();

        let converted_map = if let Value::Map(map) = converted_map {
            map
        } else {
            panic!("Expected a Map, but got {:?}", converted_map);
        };

        assert_eq!(converted_map.len(), 3);

        let (key, value) = &converted_map[0];
        assert_eq!(*key, Value::BulkString(b"key1".to_vec()));
        assert_eq!(*value, Value::Double(10.5));

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::BulkString(b"key2".to_vec()));
        assert_eq!(*value, Value::Double(20.8));

        let (key, value) = &converted_map[2];
        assert_eq!(*key, Value::BulkString(b"20.5".to_vec()));
        assert_eq!(*value, Value::Double(30.2));

        let array_of_arrays = vec![
            Value::Array(vec![
                Value::BulkString(b"key1".to_vec()),
                Value::BulkString(b"10.5".to_vec()),
            ]),
            Value::Array(vec![
                Value::BulkString(b"key2".to_vec()),
                Value::Double(20.5),
            ]),
        ];

        let converted_map = convert_to_expected_type(
            Value::Array(array_of_arrays),
            Some(ExpectedReturnType::MapOfStringToDouble),
        )
        .unwrap();

        let converted_map = if let Value::Map(map) = converted_map {
            map
        } else {
            panic!("Expected a Map, but got {:?}", converted_map);
        };

        assert_eq!(converted_map.len(), 2);

        let (key, value) = &converted_map[0];
        assert_eq!(*key, Value::BulkString(b"key1".to_vec()));
        assert_eq!(*value, Value::Double(10.5));

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::BulkString(b"key2".to_vec()));
        assert_eq!(*value, Value::Double(20.5));

        let array_of_arrays_err: Vec<Value> = vec![Value::Array(vec![
            Value::BulkString(b"key".to_vec()),
            Value::BulkString(b"value".to_vec()),
            Value::BulkString(b"10.5".to_vec()),
        ])];

        assert!(convert_to_expected_type(
            Value::Array(array_of_arrays_err),
            Some(ExpectedReturnType::MapOfStringToDouble)
        )
        .is_err());
    }

    #[test]
    fn test_convert_to_zrank_return_type() {
        assert_eq!(
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::ZRankReturnType)),
            Ok(Value::Nil)
        );

        let array = vec![
            Value::BulkString(b"key".to_vec()),
            Value::BulkString(b"20.5".to_vec()),
        ];

        let array_result = convert_to_expected_type(
            Value::Array(array),
            Some(ExpectedReturnType::ZRankReturnType),
        )
        .unwrap();

        let array_result = if let Value::Array(array) = array_result {
            array
        } else {
            panic!("Expected an Array, but got {:?}", array_result);
        };
        assert_eq!(array_result.len(), 2);

        assert_eq!(array_result[0], Value::BulkString(b"key".to_vec()));
        assert_eq!(array_result[1], Value::Double(20.5));

        let array_err = vec![Value::BulkString(b"key".to_vec())];
        assert!(convert_to_expected_type(
            Value::Array(array_err),
            Some(ExpectedReturnType::ZRankReturnType)
        )
        .is_err());
    }
    #[test]
    fn pass_null_value_for_double_or_null() {
        assert_eq!(
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::DoubleOrNull)),
            Ok(Value::Nil)
        );

        assert!(convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::Double)).is_err());
    }

    #[test]
    fn test_convert_to_list_of_bool_or_null() {
        let array = vec![Value::Nil, Value::Int(0), Value::Int(1)];
        let array_result = convert_to_expected_type(
            Value::Array(array),
            Some(ExpectedReturnType::JsonToggleReturnType),
        )
        .unwrap();

        let array_result = if let Value::Array(array) = array_result {
            array
        } else {
            panic!("Expected an Array, but got {:?}", array_result);
        };
        assert_eq!(array_result.len(), 3);

        assert_eq!(array_result[0], Value::Nil);
        assert_eq!(array_result[1], Value::Boolean(false));
        assert_eq!(array_result[2], Value::Boolean(true));

        assert!(convert_to_expected_type(
            Value::Nil,
            Some(ExpectedReturnType::JsonToggleReturnType)
        )
        .is_err());
    }

    #[test]
    fn test_convert_spop_to_set_for_spop_count() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("SPOP").arg("key1").arg("3")),
            Some(ExpectedReturnType::Set)
        ));
        assert!(expected_type_for_cmd(redis::cmd("SPOP").arg("key1")).is_none());
    }
}
