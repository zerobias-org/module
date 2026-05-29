/**
 * HL7 v2 Receiver Module - Java HTTP Implementation
 *
 * This module is implemented as a native Java HTTP server running in a Docker
 * container. It is a DAEMON-mode DataProducer: an MLLP listener receives HL7 v2
 * messages pushed by EHR/lab/ADT systems, persists them to a durable SQLite
 * buffer, and exposes the buffer through the standard DataProducer surface
 * (browse + take/ack drain functions).
 *
 * The TypeScript exports below are generated from api.yml and used by the Hub
 * platform for type checking and API discovery; the actual implementation is in
 * Java (see java/src/main/java/com/zerobias/module/hl7/).
 *
 * Implementation Type: java-http
 * Runtime: Docker container with Java 17 + Javalin (operations) + HAPI (MLLP).
 * Entry Point: java/target/hl7-listener-1.0.0.jar  (mainClass Hl7ApiServer)
 *
 * See DESIGN.md for the full design and PLAN.md for the build plan.
 */

// Export generated TypeScript API and model types
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

// Note: like other java-http modules, this does NOT export a factory function —
// the Hub Node manages the lifecycle of the Java HTTP container directly.
// Unlike demand-driven modules, this one runs in daemon mode (runtimeConfig.daemonMode).
