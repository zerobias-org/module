/**
 * X12 Receiver Module - Java HTTP Implementation
 *
 * This module is implemented as a native Java HTTP server running in a Docker
 * container. It is a DAEMON-mode DataProducer: a poller watches one or more
 * mounted volume directories for X12 EDI interchange files (837/835/277CA/999 …),
 * parses each stable file with imsweb x12-parser, persists every transaction set
 * to a durable SQLite buffer, renames the file `.done`, and exposes the buffer
 * through the standard DataProducer surface (browse + take/ack drain functions +
 * raw file download).
 *
 * The TypeScript exports below are generated from api.yml and used by the Hub
 * platform for type checking and API discovery; the actual implementation is in
 * Java (see java/src/main/java/com/zerobias/module/x12/).
 *
 * Implementation Type: java-http
 * Runtime: Docker container with Java 17 + Javalin (operations) + imsweb x12-parser.
 * Entry Point: java/target/x12-receiver-1.0.0.jar  (mainClass X12ApiServer)
 *
 * See DESIGN.md for the full design. Structural twin: package/hl7/v2.
 */

// Export generated TypeScript API and model types
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

// Note: like other java-http modules, this does NOT export a factory function —
// the Hub Node manages the lifecycle of the Java HTTP container directly.
// This one runs in daemon mode (runtimeConfig.daemonMode).
