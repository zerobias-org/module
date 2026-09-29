package com.zerobias.module.x12;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The container entrypoint ({@code startup.sh}) and the JVM options it runs with. Runs the
 * real script under {@code /bin/sh} with {@code nginx}/{@code java} stubbed on the PATH and
 * {@code /opt/module} redirected into a temp directory.
 */
class StartupScriptTest {

    private static final Path MODULE_DIR = Path.of("..").toAbsolutePath().normalize();
    private static final String SECRET = "s3cr3t-token-value";

    @Test
    void moduleConfigValueIsNeverPrinted(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Run run = start(tmp, "{\"sources\":[],\"token\":\"" + SECRET + "\"}");
        run.stop();
        assertTrue(run.output.toString().contains("MODULE_CONFIG:      set"), run.output.toString());
        assertFalse(run.output.toString().contains(SECRET), "MODULE_CONFIG value leaked:\n" + run.output);
    }

    @Test
    void moduleConfigAbsentIsReported(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Run run = start(tmp, null);
        run.stop();
        assertTrue(run.output.toString().contains("MODULE_CONFIG:      absent"), run.output.toString());
    }

    @Test
    void sigtermWaitsForJavaToFinishItsShutdown(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Run run = start(tmp, null);
        int exit = run.stop();
        // The java stub takes ~1s to run its "shutdown hook" after SIGTERM. A script that exits
        // right after `kill` returns before that; one that waits returns after it.
        assertTrue(Files.exists(tmp.resolve("java-finished")),
            "startup.sh exited before java finished its shutdown:\n" + run.output);
        assertTrue(Files.exists(tmp.resolve("nginx-finished")), "nginx was not stopped:\n" + run.output);
        assertEquals(0, exit, run.output.toString());
    }

    @Test
    void heapFollowsTheContainerLimit() throws IOException {
        String docker = Files.readString(MODULE_DIR.resolve("Dockerfile"));
        String opts = docker.lines().filter(l -> l.startsWith("ENV JAVA_OPTS=")).findFirst().orElseThrow();
        assertTrue(opts.contains("-XX:MaxRAMPercentage="), opts);
        assertFalse(opts.contains("-Xmx") || opts.contains("-Xms"), "fixed heap flags: " + opts);
    }

    // ---- harness ---------------------------------------------------------------------------

    private static final class Run {
        final Process process;
        final StringBuffer output = new StringBuffer();
        final CountDownLatch ready = new CountDownLatch(1);
        final Path armed;

        Run(Process process, Path armed) {
            this.process = process;
            this.armed = armed;
        }

        int stop() throws InterruptedException {
            assertTrue(ready.await(20, TimeUnit.SECONDS), "script never reported ready:\n" + output);
            // Both stubs must have installed their TERM trap, or the signal kills them outright.
            for (int i = 0; i < 200 && !(Files.exists(armed.resolve("java-armed"))
                    && Files.exists(armed.resolve("nginx-armed"))); i++) {
                Thread.sleep(50);
            }
            process.destroy();   // SIGTERM to the shell
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "script did not exit:\n" + output);
            return process.exitValue();
        }
    }

    private static Run start(Path tmp, String moduleConfig) throws IOException {
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Path opt = Files.createDirectories(tmp.resolve("opt"));
        stub(bin.resolve("nginx"), tmp.resolve("nginx-armed"), tmp.resolve("nginx-finished"), 0);
        stub(bin.resolve("java"), tmp.resolve("java-armed"), tmp.resolve("java-finished"), 1);
        String script = Files.readString(MODULE_DIR.resolve("startup.sh"), StandardCharsets.UTF_8)
            .replace("/opt/module", opt.toString());
        Path copy = tmp.resolve("startup.sh");
        Files.writeString(copy, script, StandardCharsets.UTF_8);

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", copy.toString()).redirectErrorStream(true);
        pb.environment().put("PATH", bin + ":" + System.getenv("PATH"));
        pb.environment().put("HUB_NODE_INSECURE", "true");
        pb.environment().put("INTERNAL_PORT", "8889");
        pb.environment().remove("MODULE_CONFIG");
        if (moduleConfig != null) {
            pb.environment().put("MODULE_CONFIG", moduleConfig);
        }
        Run run = new Run(pb.start(), tmp);
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(run.process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    run.output.append(line).append('\n');
                    if (line.contains("Module ready")) {
                        run.ready.countDown();
                    }
                }
            } catch (IOException ignored) {
                // process gone
            }
        });
        reader.setDaemon(true);
        reader.start();
        return run;
    }

    /** A long-running stub that, on SIGTERM, takes {@code delaySec} to "shut down" and then marks it. */
    private static void stub(Path file, Path armed, Path marker, int delaySec) throws IOException {
        Files.writeString(file, "#!/bin/sh\n"
            + "trap 'sleep " + delaySec + "; touch \"" + marker + "\"; exit 0' TERM\n"
            + "touch \"" + armed + "\"\n"
            + "while :; do sleep 0.1; done\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
}
