/**
 * Environment for the X12 receiver e2e suite — the only file in this module that reads
 * process.env. Gradle's testDocker task sets TEST_MODE / MODULE_DIR / CONTAINER_URL
 * (describeReceiver in helpers.ts connects to CONTAINER_URL; no slot secret is involved).
 */

/** direct | docker | hub. java-http modules have no direct mode; testDocker sets `docker`. */
export const TEST_MODE = process.env.TEST_MODE ?? 'docker';

/** The module package root (fixtures, package.json, build/module-container.json). */
export const MODULE_DIR = process.env.MODULE_DIR ?? process.cwd();

/** The running container's ops endpoint in docker mode (raw-wire checks only). */
export const CONTAINER_URL = process.env.CONTAINER_URL ?? '';


/**
 * Docker container whose inbox the suite drops fixture files into. Empty = in docker mode
 * the container gradle's startModuleExec started (build/module-container.json). In hub mode
 * there is no local container to feed, so this must be set to the module's container on the
 * node (the suite fails, rather than skips, without one).
 */
export const X12_CONTAINER = process.env.X12_CONTAINER ?? '';
