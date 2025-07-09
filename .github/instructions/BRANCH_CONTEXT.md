# BRANCH_CONTEXT.md

## Branch: UDS-alternative-java

### Objective
Test alternative approaches for the Java client in Valkey GLIDE:
- FFI with JNI using jni-rs
- Shared memory as a replacement for the current UDS-based implementation

### Key Goals
- Simplify the Java client codebase
- Maintain or improve performance (no degradation)
- Ensure secure implementations

### Implementation Strategy
1. **Proof of Concept (POC) for Each Option**
   - Minimal JNI FFI integration (using jni-rs)
   - Minimal shared memory communication
2. **Feasibility Testing**
   - Validate each approach for correctness, performance, and security
   - Compare with current UDS implementation
3. **Critical Review**
   - Challenge design and implementation choices
   - Research best practices for FFI, JNI, jni-rs, and shared memory in Java/Rust interop
   - Document findings and rationale

### Key Decisions & Rationale
- Focus on minimal, testable POCs to quickly assess feasibility
- Prioritize performance and security in all experiments
- Avoid unnecessary complexity; prefer maintainable solutions
- Narrowed focus to jni-rs and shared memory as the most promising options

### Progress Tracking

#### Implementation
- [ ] Implement minimal jni-rs FFI POC
- [ ] Implement minimal shared memory POC

#### Testing & Evaluation
- [ ] Test feasibility and correctness of each POC
- [ ] Benchmark and compare performance (vs. current UDS implementation)
- [ ] Review security for each approach

#### Documentation & Review
- [ ] Document findings, results, and recommendations
- [ ] Incorporate feedback and iterate on POCs

---

_This file should be updated regularly as work progresses. See `.github/instructions/instructions.instructions.md` for methodology and standards._
