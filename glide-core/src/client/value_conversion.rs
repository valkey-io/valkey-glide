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
    ZrankReturnType,
    Json,
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
                                Value::Double(from_owned_redis_value::<f64>(inner_value)?.into()),
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
        ExpectedReturnType::Double => {
            Ok(Value::Double(from_owned_redis_value::<f64>(value)?.into()))
        }
        ExpectedReturnType::Boolean => Ok(Value::Boolean(from_owned_redis_value::<bool>(value)?)),
        ExpectedReturnType::DoubleOrNull => match value {
            Value::Nil => Ok(value),
            _ => Ok(Value::Double(from_owned_redis_value::<f64>(value)?.into())),
        },
        ExpectedReturnType::ZrankReturnType => match value {
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
                "Response couldn't be converted to Array (ZrankResponseType)",
                format!("(response was {:?})", value),
            )
                .into()),
        },
        ExpectedReturnType::BulkString => Ok(Value::BulkString(
            from_owned_redis_value::<String>(value)?.into(),
        )),
        ExpectedReturnType::Json => match value {
            Value::Nil => Ok(value),
            Value::BulkString(json_bytes) => {
                let mut json_iterator = std::str::from_utf8(&json_bytes)?.chars().peekable();
                parse_value(&mut json_iterator)
            }
            _ => Err((
                ErrorKind::TypeError,
                "Response couldn't be converted Json response",
                format!("(response was {:?})", value),
            )
                .into()),
        },
    }
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
fn parse_value(chars: &mut std::iter::Peekable<std::str::Chars<'_>>) -> RedisResult<Value> {
    match chars.peek() {
        Some('"') => parse_string(chars),
        Some('[') => parse_array(chars),
        Some('{') => parse_map(chars),
        Some(_) => parse_number(chars),
        _ => Err((
            ErrorKind::TypeError,
            "Json response cannot be parsed",
            format!("(response has value {:?})", chars.peek()),
        )
            .into()),
    }
}
fn parse_string(chars: &mut std::iter::Peekable<std::str::Chars<'_>>) -> RedisResult<Value> {
    let mut string = String::new();
    chars.next();
    while let Some(&c) = chars.peek() {
        if c == '"' {
            chars.next();
            break;
        } else {
            string.push(c);
            chars.next();
        }
    }
    Ok(Value::BulkString(string.into()))
}
fn parse_array(chars: &mut std::iter::Peekable<std::str::Chars<'_>>) -> RedisResult<Value> {
    let mut array = Vec::new();
    chars.next(); // skip the "[" char
    loop {
        match chars.peek() {
            Some(']') => {
                chars.next();
                break;
            }
            Some(',') | Some(' ') => {
                chars.next();
                continue;
            }
            Some(_) => {
                let value = parse_value(chars)?;
                array.push(value);
            }
            _ => {
                return Err((
                    ErrorKind::TypeError,
                    "Json response cannot be entered into an array",
                    format!("(response has value {:?})", chars.peek()),
                )
                    .into())
            }
        }
    }
    Ok(Value::Array(array))
}

fn parse_number(chars: &mut std::iter::Peekable<std::str::Chars<'_>>) -> RedisResult<Value> {
    let mut number = String::new();
    while let Some(&c) = chars.peek() {
        if c.is_numeric() || c == '.' {
            number.push(c);
            chars.next();
        } else {
            break;
        }
    }
    if number.contains('.') {
        Ok(Value::Double(
            from_owned_redis_value::<f64>(Value::BulkString(number.into()))?.into(),
        ))
    } else {
        Ok(Value::Int(from_owned_redis_value::<i64>(
            Value::BulkString(number.into()),
        )?))
    }
}

fn parse_map(chars: &mut std::iter::Peekable<std::str::Chars<'_>>) -> RedisResult<Value> {
    let mut map = Vec::new();
    chars.next(); // skip the "{" char
    loop {
        match chars.peek() {
            Some('}') => {
                chars.next();
                break;
            }
            Some(',') | Some(' ') => {
                chars.next();
                continue;
            }
            Some(_) => {
                let key = parse_string(chars)?;
                if chars.peek() != Some(&':') {
                    return Err((
                        ErrorKind::TypeError,
                        "Response next value wasn't ':' , and cannot be entered into a map",
                        format!("(response next value {:?})", chars.peek()),
                    )
                        .into());
                }
                chars.next(); // skip the ":" char
                if chars.peek() == Some(&' ') {
                    chars.next();
                }
                let value = parse_value(chars)?;
                map.push((key, value));
            }
            _ => {
                return Err((
                    ErrorKind::TypeError,
                    "Json response cannot be entered into a map",
                    format!("(response has value {:?})", chars.peek()),
                )
                    .into())
            }
        }
    }
    Ok(Value::Map(map))
}

pub(crate) fn expected_type_for_cmd(cmd: &Cmd) -> Option<ExpectedReturnType> {
    let first_arg = cmd.arg_idx(0);
    match first_arg {
        Some(b"ZADD") => {
            return cmd
                .position(b"INCR")
                .map(|_| ExpectedReturnType::DoubleOrNull);
        }
        Some(b"ZRANGE") | Some(b"ZDIFF") => {
            return cmd
                .position(b"WITHSCORES")
                .map(|_| ExpectedReturnType::MapOfStringToDouble);
        }
        Some(b"ZRANK") | Some(b"ZREVRANK") => {
            return cmd
                .position(b"WITHSCORE")
                .map(|_| ExpectedReturnType::ZrankReturnType);
        }
        _ => {}
    }

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
        b"ZSCORE" => Some(ExpectedReturnType::DoubleOrNull),
        b"ZPOPMIN" | b"ZPOPMAX" => Some(ExpectedReturnType::MapOfStringToDouble),
        b"JSON.GET" => Some(ExpectedReturnType::Json),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn convert_zadd_only_if_incr_is_included() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZADD")
                    .arg("XT")
                    .arg("CH")
                    .arg("INCR")
                    .arg("0.6")
                    .arg("foo")
            ),
            Some(ExpectedReturnType::DoubleOrNull)
        ));

        assert!(expected_type_for_cmd(
            redis::cmd("ZADD").arg("XT").arg("CH").arg("0.6").arg("foo")
        )
        .is_none());
    }

    #[test]
    fn convert_zrange_zdiff_only_if_withsocres_is_included() {
        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZRANGE").arg("0").arg("-1").arg("WITHSCORES")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZRANGE").arg("0").arg("-1")).is_none());

        assert!(matches!(
            expected_type_for_cmd(redis::cmd("ZDIFF").arg("1").arg("WITHSCORES")),
            Some(ExpectedReturnType::MapOfStringToDouble)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZDIFF").arg("1")).is_none());
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
    fn convert_zank_zrevrank_only_if_withsocres_is_included() {
        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZRANK")
                    .arg("key")
                    .arg("member")
                    .arg("WITHSCORE")
            ),
            Some(ExpectedReturnType::ZrankReturnType)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZRANK").arg("key").arg("member")).is_none());

        assert!(matches!(
            expected_type_for_cmd(
                redis::cmd("ZREVRANK")
                    .arg("key")
                    .arg("member")
                    .arg("WITHSCORE")
            ),
            Some(ExpectedReturnType::ZrankReturnType)
        ));

        assert!(expected_type_for_cmd(redis::cmd("ZREVRANK").arg("key").arg("member")).is_none());
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
            (
                Value::Double(20.5.into()),
                Value::BulkString(b"30.2".to_vec()),
            ),
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
        assert_eq!(*value, Value::Double(10.5.into()));

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::BulkString(b"key2".to_vec()));
        assert_eq!(*value, Value::Double(20.8.into()));

        let (key, value) = &converted_map[2];
        assert_eq!(*key, Value::BulkString(b"20.5".to_vec()));
        assert_eq!(*value, Value::Double(30.2.into()));

        let array_of_arrays = vec![
            Value::Array(vec![
                Value::BulkString(b"key1".to_vec()),
                Value::BulkString(b"10.5".to_vec()),
            ]),
            Value::Array(vec![
                Value::BulkString(b"key2".to_vec()),
                Value::Double(20.5.into()),
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
        assert_eq!(*value, Value::Double(10.5.into()));

        let (key, value) = &converted_map[1];
        assert_eq!(*key, Value::BulkString(b"key2".to_vec()));
        assert_eq!(*value, Value::Double(20.5.into()));

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
            convert_to_expected_type(Value::Nil, Some(ExpectedReturnType::ZrankReturnType)),
            Ok(Value::Nil)
        );

        let array = vec![
            Value::BulkString(b"key".to_vec()),
            Value::BulkString(b"20.5".to_vec()),
        ];

        let array_result = convert_to_expected_type(
            Value::Array(array),
            Some(ExpectedReturnType::ZrankReturnType),
        )
        .unwrap();

        let array_result = if let Value::Array(array) = array_result {
            array
        } else {
            panic!("Expected an Array, but got {:?}", array_result);
        };
        assert_eq!(array_result.len(), 2);

        assert_eq!(array_result[0], Value::BulkString(b"key".to_vec()));
        assert_eq!(array_result[1], Value::Double(20.5.into()));

        let array_err = vec![Value::BulkString(b"key".to_vec())];
        assert!(convert_to_expected_type(
            Value::Array(array_err),
            Some(ExpectedReturnType::ZrankReturnType)
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
    fn test_json_return_type() {
        let json_str_array = r#"[{"f1":{"a":1},"f2":{"a":2}},30.5]"#;
        let result_array = convert_to_expected_type(
            Value::BulkString(json_str_array.into()),
            Some(ExpectedReturnType::Json),
        )
        .unwrap();
        assert_eq!(
            result_array,
            Value::Array(vec![
                Value::Map(vec![
                    (
                        Value::BulkString(b"f1".to_vec()),
                        Value::Map(vec![(Value::BulkString(b"a".to_vec()), Value::Int(1))])
                    ),
                    (
                        Value::BulkString(b"f2".to_vec()),
                        Value::Map(vec![(Value::BulkString(b"a".to_vec()), Value::Int(2))])
                    )
                ]),
                Value::Double(30.5.into())
            ])
        );
    }

    #[test]
    fn test_parse_string() {
        let json_str = r#""test""#;
        let result = parse_string(&mut json_str.chars().peekable()).unwrap();
        assert_eq!(result, Value::BulkString(b"test".to_vec()));
    }

    #[test]
    fn test_parse_number() {
        let json_str = "123";
        let result = parse_number(&mut json_str.chars().peekable()).unwrap();
        assert_eq!(result, Value::Int(123));
    }

    #[test]
    fn test_parse_array() {
        let json_str = r#"[1, 2, 3]"#;
        let result = parse_array(&mut json_str.chars().peekable()).unwrap();
        assert_eq!(
            result,
            Value::Array(vec![Value::Int(1), Value::Int(2), Value::Int(3),])
        );
    }

    #[test]
    fn test_parse_map() {
        let json_str = r#"{"a": 1, "b": 2}"#;
        let result = parse_map(&mut json_str.chars().peekable()).unwrap();
        assert_eq!(
            result,
            Value::Map(vec![
                (Value::BulkString(b"a".to_vec()), Value::Int(1)),
                (Value::BulkString(b"b".to_vec()), Value::Int(2))
            ])
        );
    }
}
