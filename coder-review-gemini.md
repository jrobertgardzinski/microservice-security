# Code Review - Project Portfolio (microservice-security)

## Overview
The project demonstrates a high level of technical maturity, following modern software engineering practices such as Domain-Driven Design (DDD), Hexagonal Architecture, and the use of the latest Java features (Java 25). The focus on security and conscious architectural decisions (e.g., YAGNI with non-JWT tokens) is commendable.

---

## 1. Architecture & Design
### Domain-Driven Design (DDD)
- **Entities & Value Objects:** Strong use of `records` for Value Objects (e.g., `Email`, `Iterations`, `IpAddress`) with validation in the compact constructor. This ensures immutability and valid state.
- **Aggregates:** `User` is currently a simple record. As behavior grows (e.g., password change history, MFA status), it might need to transition to a class to manage internal state more effectively.
- **Repositories:** Clean port interfaces in `security-domain`, following the dependency inversion principle.

### Hexagonal Architecture
- **Separation of Concerns:** The project is well-partitioned into `domain`, `application`, `infrastructure`, and `system` modules.
- **Port/Adapter Pattern:** Correctly implemented, especially visible in the `password` module where `HashAlgorithmPort` decouples the domain from the specific implementation (Argon2).
- **Use Case Orchestration:** The "Feature" and "UseCase" pattern (implementing `Function` or `Consumer`) is a clean, functional approach to service design, making each component highly testable and focused.

---

## 2. Security
### Password Hashing (Argon2)
- **Choice of Algorithm:** Excellent choice of Argon2, providing superior protection against GPU/ASIC cracking compared to BCrypt.
- **Verification:** Verification correctly leverages the encoded parameters in the hash string, allowing for future-proof password verification even if global defaults change.
- **Risk-Based Hashing:** While mentioned as a concept, the current `Argon2HashAlgorithm` uses a static `Argon2Config`. To fully realize "risk-based hashing" (e.g., different costs for admins vs. users), the `hash` method should be extended to accept intensity parameters or a strategy pattern should be employed.

### Brute-Force Protection
- **Implementation:** The `BruteForceGuard` is well-designed, using a window-based failure count and randomized blockade durations (jitter).
- **Decoupling:** Use of `Clock` in the guard allows for deterministic testing of time-based logic.

### Token Strategy
- **YAGNI Implementation:** The decision to use random string tokens instead of JWT for a single-region system is a great example of pragmatic architecture. It reduces complexity and avoids the "token revocation problem" inherent in stateless JWTs.

---

## 3. Code Quality & Modern Java
- **Java 25 Features:** Proactive adoption of the latest LTS features like `records`, `sealed types`, and `switch pattern matching` makes the code concise and expressive.
- **Readability:** High readability due to small, single-purpose classes and clear naming conventions.
- **Lombok Usage:** Used sparingly where records aren't suitable, keeping the codebase clean.

---

## 4. Testing
- **Multi-Layered Strategy:**
    - **Unit Tests:** Solid coverage of domain logic.
    - **Property-Based Testing (jqwik):** Excellent use of PBT for validation rules (e.g., `Argon2ConfigRulesTest`), uncovering edge cases that traditional unit tests might miss.
    - **BDD (Cucumber):** Feature files provide clear living documentation. The use of `Stubs` (e.g., `StubUserRepository`) instead of mocks results in more robust, readable, and faster tests.
- **Architecture Testing:** Consider adding **ArchUnit** to programmatically enforce hexagonal boundaries and package structure.

---

## 5. Build & Lifecycle
### Maven Configuration
- **Modularization:** Effective use of a parent POM and module-specific POMs.
- **BOM Management:** Correct usage of Maven BOMs for JUnit, Mockito, and AssertJ ensures version consistency.

### Observations:
- **Version Inconsistencies:** There is a mismatch in versions across modules (e.g., `security-application` at `0.1-SNAPSHOT` vs `security-domain` at `1.0.0-SNAPSHOT`). These should be aligned (preferably using `${project.version}`).

---

## 6. Gaps & Recommendations

### 1. Observability (Critical Gap)
The project currently lacks structured logging and metrics.
- **Recommendation:** Integrate **Micrometer** for metrics (Prometheus) and **SLF4J/Logback** for structured logging. Add a `MeterRegistry` to `BruteForceGuard` to track blocked IPs and failed attempts.
- **Tracing:** For a microservice environment, consider **OpenTelemetry** to trace requests across boundaries (especially important with Kafka).

### 2. Risk-Based Hashing Selection
- **Recommendation:** Update `HashAlgorithmPort` to allow passing a `HashingIntensity` or context.
  ```java
  HashedPassword hash(PlaintextPassword password, HashingIntensity intensity);
  ```

### 3. Version Alignment
- **Recommendation:** Use a single version property in the root `pom.xml` to keep all modules in sync.

### 4. Email as VO vs PK
- **Recommendation:** As noted in your briefing, ensure the transition to UUID as Primary Key is completed in the persistence layer to avoid the pitfalls of using mutable/GDPR-sensitive data as identity.

---

## Summary
The project is a strong demonstration of **Senior/Architect** level thinking. It prioritizes correctness, security, and simplicity over "hyped" solutions. Addressing the observability gap will be the final step to make it truly production-ready at a Lead level.
