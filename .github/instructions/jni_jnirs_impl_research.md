
# JNI & jni-rs Design Research for Java 11 (Design Phase)

## Objective
Design the most efficient, maintainable, and scalable way to pass both metadata and command payload from Java to Rust in a single JNI call, for the Glide client (Java 11 compatible).

---

## Design Decision: Metadata + Payload Passing

### Chosen Pattern: Metadata Struct as Direct ByteBuffer (Zero-Copy)
- Define a C-style struct in Rust for all metadata fields, using #[repr(C)] for predictable layout.
- In Java, allocate a Direct ByteBuffer, write the struct fields in native order, and pass it to JNI alongside the command buffer (also a Direct ByteBuffer).
- Rust reads both buffers with zero-copy, using get_direct_buffer_address.

#### Rationale
- Keeps the JNI signature stable as metadata evolves (future-proof)
- Enables zero-copy for both metadata and payload (max performance)
- Scalable and maintainable for complex clients
- Common pattern in high-performance, cross-language systems

#### Design Requirements
- Java and Rust must agree on struct layout and byte order (always use ByteOrder.nativeOrder() in Java and #[repr(C)] in Rust)
- Validate buffer sizes and field alignment on both sides
- Document the struct layout and update both sides together

#### Open Design Questions
- How to handle optional/variable-length metadata fields?
- How to version the struct for backward/forward compatibility?
- How to best map Rust errors to Java exceptions in this pattern?

---

_This document is for the design phase. Implementation details, pitfalls, and alternatives are tracked separately. Update as design decisions are made or new questions arise._
