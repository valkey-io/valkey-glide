// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Reflection round-trip over `ConnectionRequest`: every field — including fields added
//! after this test was written — is set to a non-default value via the protobuf
//! descriptors, printed to ProtoJSON, parsed back, and compared for equality.
//!
//! This is the permanent drift guard for the ProtoJSON creation path (#7157): because the
//! field walk is reflective, a new proto field is covered automatically; the test cannot
//! go stale the way a hand-written per-field test does.

use glide_core::connection_request::ConnectionRequest;
use protobuf::MessageFull;
use protobuf::reflect::{MessageDescriptor, ReflectValueBox, RuntimeFieldType, RuntimeType};

/// Synthesizes a non-default value for a runtime type. `pick` varies the chosen enum
/// values so the two passes exercise different variants.
fn synthesize(runtime_type: &RuntimeType, pick: usize) -> ReflectValueBox {
    match runtime_type {
        RuntimeType::I32 => ReflectValueBox::I32(7 + pick as i32),
        RuntimeType::I64 => ReflectValueBox::I64(7 + pick as i64),
        RuntimeType::U32 => ReflectValueBox::U32(7 + pick as u32),
        RuntimeType::U64 => ReflectValueBox::U64(7 + pick as u64),
        RuntimeType::F32 => ReflectValueBox::F32(1.5),
        RuntimeType::F64 => ReflectValueBox::F64(1.5),
        RuntimeType::Bool => ReflectValueBox::Bool(true),
        RuntimeType::String => ReflectValueBox::String(format!("value-{pick}")),
        RuntimeType::VecU8 => ReflectValueBox::Bytes(format!("bytes-{pick}").into_bytes()),
        RuntimeType::Enum(descriptor) => {
            let values: Vec<_> = descriptor.values().collect();
            // Prefer a non-default variant so proto3 implicit presence emits the field.
            let index = (1 + pick) % values.len();
            ReflectValueBox::Enum(descriptor.clone(), values[index].value())
        }
        RuntimeType::Message(descriptor) => {
            let mut message = descriptor.new_instance();
            fill_every_field(descriptor, &mut *message, pick);
            ReflectValueBox::Message(message)
        }
    }
}

/// Sets every field of `message` to a synthesized non-default value, recursing into
/// nested messages. For oneofs, the arm set last in iteration order wins; `pick` reverses
/// the order so both arms are exercised across the two passes.
fn fill_every_field(
    descriptor: &MessageDescriptor,
    message: &mut dyn protobuf::MessageDyn,
    pick: usize,
) {
    let mut fields: Vec<_> = descriptor.fields().collect();
    if pick % 2 == 1 {
        fields.reverse();
    }
    for field in fields {
        match field.runtime_field_type() {
            RuntimeFieldType::Singular(runtime_type) => {
                field.set_singular_field(message, synthesize(&runtime_type, pick));
            }
            RuntimeFieldType::Repeated(runtime_type) => {
                field
                    .mut_repeated(message)
                    .push(synthesize(&runtime_type, pick));
            }
            RuntimeFieldType::Map(key_type, value_type) => {
                field
                    .mut_map(message)
                    .insert(synthesize(&key_type, pick), synthesize(&value_type, pick));
            }
        }
    }
}

fn round_trip_with_pick(pick: usize) {
    let descriptor = ConnectionRequest::descriptor();
    let mut original = ConnectionRequest::new();
    fill_every_field(&descriptor, &mut original, pick);

    let json = protobuf_json_mapping::print_to_string(&original)
        .expect("printing a fully-populated ConnectionRequest to ProtoJSON failed");
    let parsed: ConnectionRequest = protobuf_json_mapping::parse_from_str(&json)
        .unwrap_or_else(|err| panic!("parsing printed ProtoJSON failed: {err}\njson: {json}"));

    assert_eq!(
        original, parsed,
        "ProtoJSON round trip altered the message.\njson: {json}"
    );
}

#[test]
fn every_connection_request_field_round_trips() {
    round_trip_with_pick(0);
}

#[test]
fn every_connection_request_field_round_trips_alternate_oneof_arms() {
    round_trip_with_pick(1);
}

#[test]
fn unknown_fields_are_rejected_by_default() {
    // Strictness is the drift guard: default parse options must reject unknown fields so a
    // misspelled or not-yet-supported field fails loudly instead of being silently dropped.
    let result: Result<ConnectionRequest, _> =
        protobuf_json_mapping::parse_from_str(r#"{"notARealField": 1}"#);
    assert!(result.is_err());
}
