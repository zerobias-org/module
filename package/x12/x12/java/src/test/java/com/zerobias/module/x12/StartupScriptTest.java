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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The container entrypoint ({@code startup.sh}) and the JVM options it runs with. Runs the
 * real script under {@code /bin/sh} with {@code nginx}/{@code java} stubbed on the PATH and
 * {@code /opt/module} and the two volume paths redirected into a temp directory.
 *
 * <p>The root branch (repair volume ownership, then drop to 10001) runs without root on the
 * host: {@code id}, {@code setpriv} and {@code chown} are stubbed too. The {@code id} stub
 * reports uid 0 until the {@code setpriv} stub "drops" to its {@code --reuid}; that stub runs
 * its command as the host user, so "10001 cannot write" is a directory the host user cannot
 * write, and the repair stubs ({@code chown}, {@code chgrp}, {@code chmod}) log their
 * arguments and make their targets writable.
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

    @Test
    void theContainerStartsAsRootOnlyToDropTo10001() throws IOException {
        List<String> docker = Files.readAllLines(MODULE_DIR.resolve("Dockerfile"));
        assertTrue(docker.contains("CMD [\"/opt/module/startup.sh\"]"), "entrypoint");
        // A USER line would start the script unprivileged, and the volume repair would never run.
        for (String line : docker) {
            assertFalse(line.startsWith("USER "), "the entrypoint starts as root and drops itself: " + line);
        }
        String script = Files.readString(MODULE_DIR.resolve("startup.sh"));
        int drop = script.indexOf("exec setpriv --reuid=\"$MODULE_UID\" --regid=\"$MODULE_GID\" --init-groups -- \"$0\"");
        assertTrue(drop > 0, "startup.sh re-executes itself as the module user");
        assertTrue(script.contains("MODULE_UID=10001") && script.contains("MODULE_GID=10001"), "as 10001:10001");
        assertTrue(drop < script.indexOf("nginx -c") && drop < script.indexOf("java $JAVA_OPTS"),
            "the drop comes before nginx and java start");
        String all = String.join("\n", docker);
        // Everything the running process writes: the cert dir, nginx's logs, both volumes.
        for (String path : new String[] {"/opt/module/ssl", "/var/log/nginx", "/var/lib/module", "/var/lib/x12/inbox"}) {
            assertTrue(all.lines().anyMatch(l -> l.contains("chown") && l.contains(path)), path + " owned by 10001");
        }
        for (String conf : new String[] {"nginx.conf", "nginx-insecure.conf"}) {
            String c = Files.readString(MODULE_DIR.resolve(conf));
            assertTrue(c.contains("pid /tmp/nginx.pid;"), conf + ": pid where 10001 can write");
            for (String temp : new String[] {"client_body", "proxy", "fastcgi", "uwsgi", "scgi"}) {
                assertTrue(c.contains(temp + "_temp_path /tmp/"), conf + ": " + temp + " temp path");
            }
            assertTrue(c.contains("client_max_body_size 64m;"), conf + ": upload cap kept");
        }
    }

    @Test
    void aBufferVolumeTheUserCannotWriteStopsTheBootWithTheFix(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Path readOnly = Files.createDirectories(tmp.resolve("root-owned"));
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeFalse(Files.isWritable(readOnly), "running as root: permissions not enforced");
            Run run = start(tmp, null, Map.of("BUFFER_DB", readOnly.resolve("buffer.db").toString()));
            assertTrue(run.process.waitFor(20, TimeUnit.SECONDS), "script did not exit:\n" + run.output);
            assertEquals(1, run.process.exitValue(), run.output.toString());
            run.drained.await(5, TimeUnit.SECONDS);
            assertTrue(run.output.toString().contains("is not writable by uid")
                && run.output.toString().contains("chown the buffer volume"), run.output.toString());
            assertFalse(Files.exists(tmp.resolve("java-armed")), "java never started");
        } finally {
            Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void startedAsRootItRepairsRootOwnedVolumesAndDropsTo10001(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Path buffer = Files.createDirectories(tmp.resolve("volume"));
        Files.writeString(buffer.resolve("buffer.db"), "");
        Path inbox = Files.createDirectories(tmp.resolve("inbox"));
        Files.writeString(inbox.resolve("claim.x12"), "ISA*");
        readOnly(buffer.resolve("buffer.db"), buffer, inbox);
        try {
            assumeFalse(Files.isWritable(buffer), "running as root: permissions not enforced");
            Run run = start(tmp, null, Map.of(), As.ROOT);
            run.stop();
            String out = run.output.toString();
            // The buffer is the module's: chown -R. The inbox keeps its owner (the producer) and
            // gains group 10001 with write, on the directory alone.
            assertEquals(List.of("chown -R 10001:10001 " + buffer, "chgrp 10001 " + inbox, "chmod g+rwx " + inbox),
                Files.readAllLines(tmp.resolve("repair.log")));
            // One line per repaired path, and nothing about what is inside them.
            assertEquals(1, count(out, "Fixing ownership for uid 10001: " + buffer + "\n"), out);
            assertEquals(1, count(out, "Fixing group write for gid 10001: " + inbox + "\n"), out);
            assertFalse(out.contains("claim.x12") || out.contains("buffer.db"), out);
            assertDroppedTo10001(tmp, out);
        } finally {
            writable(buffer.resolve("buffer.db"), buffer, inbox);
        }
    }

    @Test
    void startedAsRootItLeavesWritableVolumesAlone(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Files.createDirectories(tmp.resolve("volume"));
        Files.createDirectories(tmp.resolve("inbox"));
        Run run = start(tmp, null, Map.of(), As.ROOT);
        run.stop();
        assertFalse(Files.exists(tmp.resolve("repair.log")), "nothing to repair, nothing changed");
        assertFalse(run.output.toString().contains("Fixing ownership"), run.output.toString());
        assertDroppedTo10001(tmp, run.output.toString());
    }

    @Test
    void aRepairThatDoesNotTakeStillStopsTheBootWithTheFix(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Path buffer = Files.createDirectories(tmp.resolve("volume"));
        Path inbox = Files.createDirectories(tmp.resolve("inbox"));
        readOnly(buffer, inbox);
        try {
            assumeFalse(Files.isWritable(buffer), "running as root: permissions not enforced");
            Run run = start(tmp, null, Map.of(), As.ROOT_REPAIR_FAILS);
            assertExitsWithTheChownHint(run, tmp, "uid 10001; chown the buffer volume to 10001:10001");
            String out = run.output.toString();
            assertTrue(out.contains("WARNING: could not chown " + buffer), out);
            assertTrue(out.contains("WARNING: " + inbox + " is still not writable by uid 10001; give group 10001"
                + " write on it (chgrp 10001 + chmod g+rwx)"), out);
        } finally {
            writable(buffer, inbox);
        }
    }

    @Test
    void startedAsRootItNeverChownsABufferOutsideTheVolume(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Files.createDirectories(tmp.resolve("volume"));
        Files.createDirectories(tmp.resolve("inbox"));
        Path elsewhere = Files.createDirectories(tmp.resolve("elsewhere"));
        readOnly(elsewhere);
        try {
            assumeFalse(Files.isWritable(elsewhere), "running as root: permissions not enforced");
            Run run = start(tmp, null, Map.of("BUFFER_DB", elsewhere.resolve("buffer.db").toString()), As.ROOT);
            assertExitsWithTheChownHint(run, tmp, "uid 10001; chown the buffer volume to 10001:10001");
            assertFalse(Files.exists(tmp.resolve("repair.log")), "a root chown -R only ever targets the volume");
        } finally {
            writable(elsewhere);
        }
    }

    private static void assertDroppedTo10001(Path tmp, String out) throws IOException {
        // Both stubs record `id -u` as they start: the uid the script had when it launched them.
        assertEquals("10001", Files.readString(tmp.resolve("nginx-armed")).trim(), "nginx as 10001:\n" + out);
        assertEquals("10001", Files.readString(tmp.resolve("java-armed")).trim(), "java as 10001:\n" + out);
        List<String> drops = Files.readAllLines(tmp.resolve("setpriv.log")).stream()
            .filter(l -> !l.contains(" test -w ")).toList();
        assertEquals(1, drops.size(), "one re-exec: " + drops);
        assertTrue(drops.get(0).startsWith("--reuid=10001 --regid=10001 --init-groups -- "), drops.get(0));
        assertEquals(1, count(out, "Starting X12 Receiver Module..."), out);
        assertEquals(1, count(out, "Module ready"), out);
    }

    private static void assertExitsWithTheChownHint(Run run, Path tmp, String hint) throws Exception {
        assertTrue(run.process.waitFor(20, TimeUnit.SECONDS), "script did not exit:\n" + run.output);
        assertEquals(1, run.process.exitValue(), run.output.toString());
        run.drained.await(5, TimeUnit.SECONDS);
        assertTrue(run.output.toString().contains(hint), run.output.toString());
        assertFalse(Files.exists(tmp.resolve("java-armed")), "java never started");
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static void readOnly(Path... paths) throws IOException {
        for (Path p : paths) {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(
                Files.isDirectory(p) ? "r-xr-xr-x" : "r--r--r--"));
        }
    }

    private static void writable(Path... paths) throws IOException {
        for (Path p : paths) {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(
                Files.isDirectory(p) ? "rwxr-xr-x" : "rw-r--r--"));
        }
    }

    @Test
    void httpsModeGeneratesItsCertificateAsTheInvokingUser(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        assumeTrue(onPath("openssl"), "needs openssl");
        Run run = start(tmp, null, Map.of("HUB_NODE_INSECURE", "false"));
        run.stop();
        assertTrue(run.output.toString().contains("SSL certificate generated"), run.output.toString());
        assertTrue(Files.size(tmp.resolve("opt/ssl/cert.pem")) > 0 && Files.size(tmp.resolve("opt/ssl/key.pem")) > 0);
    }

    private static boolean onPath(String tool) {
        for (String dir : System.getenv("PATH").split(":")) {
            if (Files.isExecutable(Path.of(dir, tool))) {
                return true;
            }
        }
        return false;
    }

    // ---- harness ---------------------------------------------------------------------------

    private static final class Run {
        final Process process;
        final StringBuffer output = new StringBuffer();
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch drained = new CountDownLatch(1);
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
            // SIGTERM to the shell, and nothing else. Process.destroy() also closes our end of its
            // stdout, so the script's next echo dies of SIGPIPE: a harness artifact (docker stop
            // leaves the log pipe open) that made this fail on macOS.
            process.toHandle().destroy();
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "script did not exit:\n" + output);
            return process.exitValue();
        }
    }

    /** Who the script starts as: the invoking user, or "root" (stubbed {@code id}, {@code setpriv}, repairs). */
    private enum As { USER, ROOT, ROOT_REPAIR_FAILS }

    private static Run start(Path tmp, String moduleConfig) throws IOException {
        return start(tmp, moduleConfig, Map.of());
    }

    private static Run start(Path tmp, String moduleConfig, Map<String, String> env) throws IOException {
        return start(tmp, moduleConfig, env, As.USER);
    }

    private static Run start(Path tmp, String moduleConfig, Map<String, String> env, As as) throws IOException {
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Path buffer = Files.createDirectories(tmp.resolve("buffer"));
        Path opt = Files.createDirectories(tmp.resolve("opt"));
        stub(bin.resolve("nginx"), tmp.resolve("nginx-armed"), tmp.resolve("nginx-finished"), 0);
        stub(bin.resolve("java"), tmp.resolve("java-armed"), tmp.resolve("java-finished"), 1);
        if (as != As.USER) {
            rootStubs(bin, tmp, as == As.ROOT);
            buffer = tmp.resolve("volume");
        }
        String script = Files.readString(MODULE_DIR.resolve("startup.sh"), StandardCharsets.UTF_8)
            .replace("/opt/module", opt.toString())
            .replace("/var/lib/module", tmp.resolve("volume").toString())
            .replace("/var/lib/x12/inbox", tmp.resolve("inbox").toString());
        Path copy = tmp.resolve("startup.sh");
        Files.writeString(copy, script, StandardCharsets.UTF_8);
        // The root branch re-executes "$0", as the image's CMD does.
        Files.setPosixFilePermissions(copy, PosixFilePermissions.fromString("rwxr-xr-x"));

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", copy.toString()).redirectErrorStream(true);
        pb.environment().put("PATH", bin + ":" + System.getenv("PATH"));
        pb.environment().put("HUB_NODE_INSECURE", "true");
        pb.environment().put("INTERNAL_PORT", "8889");
        pb.environment().remove("MODULE_CONFIG");
        if (moduleConfig != null) {
            pb.environment().put("MODULE_CONFIG", moduleConfig);
        }
        pb.environment().put("BUFFER_DB", buffer.resolve("buffer.db").toString());
        pb.environment().putAll(env);
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
            } finally {
                run.drained.countDown();
            }
        });
        reader.setDaemon(true);
        reader.start();
        return run;
    }

    /**
     * A long-running stub that records the uid it runs as ({@code id -u}) when armed and, on
     * SIGTERM, takes {@code delaySec} to "shut down" and then marks it.
     */
    private static void stub(Path file, Path armed, Path marker, int delaySec) throws IOException {
        Files.writeString(file, "#!/bin/sh\n"
            + "trap 'sleep " + delaySec + "; touch \"" + marker + "\"; exit 0' TERM\n"
            + "id -u > \"" + armed + "\"\n"
            + "while :; do sleep 0.1; done\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    /**
     * Root without root: {@code id} answers from FAKE_UID/FAKE_GID (0 until a drop),
     * {@code setpriv} logs its arguments, sets FAKE_UID/FAKE_GID from --reuid/--regid and runs
     * the command as the host user, and the repairs ({@code chown}, {@code chgrp},
     * {@code chmod}) log to repair.log and make their targets writable by the host user, the
     * stand-in for 10001 (or fail, when {@code repairsWork} is false).
     */
    private static void rootStubs(Path bin, Path tmp, boolean repairsWork) throws IOException {
        executable(bin.resolve("id"), "#!/bin/sh\n"
            + "case \"$1\" in\n"
            + "  -u) echo \"${FAKE_UID:-0}\" ;;\n"
            + "  -g) echo \"${FAKE_GID:-0}\" ;;\n"
            + "  *) exit 2 ;;\n"
            + "esac\n");
        executable(bin.resolve("setpriv"), "#!/bin/sh\n"
            + "echo \"$*\" >> \"" + tmp.resolve("setpriv.log") + "\"\n"
            + "while [ $# -gt 0 ]; do\n"
            + "  case \"$1\" in\n"
            + "    --reuid=*) FAKE_UID=${1#--reuid=} ;;\n"
            + "    --regid=*) FAKE_GID=${1#--regid=} ;;\n"
            + "    --) shift; break ;;\n"
            + "  esac\n"
            + "  shift\n"
            + "done\n"
            + "export FAKE_UID FAKE_GID\n"
            + "exec \"$@\"\n");
        for (String cmd : new String[] {"chown", "chgrp", "chmod"}) {
            executable(bin.resolve(cmd), "#!/bin/sh\n"
                + "echo \"" + cmd + " $*\" >> \"" + tmp.resolve("repair.log") + "\"\n"
                + (repairsWork
                    // chown [-R] OWNER PATH... / chgrp GROUP PATH... / chmod MODE PATH...
                    ? "r=; if [ \"$1\" = -R ]; then r=-R; shift; fi\nshift\nexec /bin/chmod $r u+w \"$@\"\n"
                    : "echo \"" + cmd + ": Operation not permitted\" >&2; exit 1\n"));
        }
    }

    private static void executable(Path file, String content) throws IOException {
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
}
