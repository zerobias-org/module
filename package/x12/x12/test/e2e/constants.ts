/**
 * Environment for the X12 receiver e2e suite — the only file in this module that reads
 * process.env. Gradle's testDocker task sets TEST_MODE / MODULE_DIR (and CONTAINER_URL,
 * which @zerobias-org/module-test-client reads itself).
 */

/** direct | docker | hub. java-http modules have no direct mode; testDocker sets `docker`. */
export const TEST_MODE = process.env.TEST_MODE ?? 'docker';

/** The module package root (fixtures, build/module-container.json). */
export const MODULE_DIR = process.env.MODULE_DIR ?? process.cwd();

/**
 * Docker container whose inbox the suite drops fixture files into. Empty = in docker mode
 * the container gradle's startModuleExec started (build/module-container.json); in hub mode
 * there is no local container to feed and the data-dependent tests skip.
 */
export const X12_CONTAINER = process.env.X12_CONTAINER ?? '';
