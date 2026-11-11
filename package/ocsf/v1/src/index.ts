/**
 * OCSF Receiver Module - Java HTTP Implementation
 *
 * A DAEMON-mode module implemented as a native Java HTTP server in a Docker container. A poller
 * watches mounted volume directories for OCSF event files (NDJSON / JSONL / JSON, optionally
 * gzipped), stores each event verbatim in a durable SQLite buffer, renames the file `.done`, and
 * serves the events through the OCSF interface (@zerobias-org/module-interface-ocsf:
 * listClasses, list<Class>/get<Class>). The files themselves are browsable and downloadable
 * through the DataProducer interface (/inbox, /files, /stats).
 *
 * The TypeScript exports below are generated from api.yml and used by the Hub platform for type
 * checking and API discovery; the implementation is Java (java/src/main/java/com/zerobias/module/ocsf/).
 *
 * Implementation Type: java-http
 * Runtime: Docker container with Java 17 + Javalin (operations) behind nginx.
 * Entry Point: java/target/ocsf-receiver-1.0.0.jar  (mainClass OcsfApiServer)
 *
 * See DESIGN.md. Structural relatives: package/x12/x12 (same daemon shell), package/hl7/v2.
 */

// Export generated TypeScript API and model types
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

// Note: like other java-http modules, this does NOT export a factory function —
// the Hub Node manages the lifecycle of the Java HTTP container directly.
// This one runs in daemon mode (runtimeConfig.daemonMode).
