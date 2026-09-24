package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.PollerHandle;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.health.PollerStatus;
import com.zerobias.module.x12.health.SourceStatuses;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.zerobias.module.x12.buffer.TestRows.BASE;
import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static com.zerobias.module.x12.buffer.TestRows.FILE_B;

/**
 * A seeded buffer for the producer tests: 2 files, 2 sources, 2 transaction types, and one
 * type (837P) spanning two GS08 guides.
 *
 * <pre>
 * FILE_A  source=inbox  835   gs08=005010X221A1  sender=PAYERA  st 0001,0002,0003  (bytes on disk)
 * FILE_B  source=sftp   837P  gs08=005010X222A1  sender=CLINIC  gs 2 / st 0001
 *                       837P  gs08=005010X222    sender=CLINIC  gs 3 / st 0001    (bytes GONE from disk)
 * </pre>
 *
 * Source {@code inbox} watches {@code dir} (where FILE_A's {@code .done} lies), {@code sftp}
 * watches {@code dir/sftp}.
 */
final class ProducerFixture {

    static final byte[] FILE_A_BYTES = "ISA*00*          *00*          *ZZ*PAYERA         *ZZ*PROVIDER1      *260922*0000*^*00501*000000001*0*P*:~GS*HP*PAYERA*PROVIDER1*20260922*0000*1*X*005010X221A1~ST*835*0001~SE*2*0001~GE*1*1~IEA*1*000000001~"
        .getBytes(StandardCharsets.UTF_8);
    static final String GS08_837P_ALT = "005010X222";
    static final String SCHEMA_837P_ALT = "schema:table:x12." + GS08_837P_ALT + ".837P";

    static final String KEY_A1 = TestRows.key(FILE_A, "1", "0001");
    static final String KEY_A2 = TestRows.key(FILE_A, "1", "0002");
    static final String KEY_A3 = TestRows.key(FILE_A, "1", "0003");
    static final String KEY_B1 = TestRows.key(FILE_B, "2", "0001");
    static final String KEY_B2 = TestRows.key(FILE_B, "3", "0001");

    /** Recasts nothing: every row reproduces what it stores. */
    static final RecastHook REPRODUCES = row -> Optional.empty();

    private ProducerFixture() {
    }

    /** The two watched sources of the seeded buffer. */
    static ModuleRuntimeConfig config(Path dir) {
        return new ModuleRuntimeConfig(List.of(
                new SourceConfig("inbox", dir.toString(), "*", 1, 0),
                new SourceConfig("sftp", dir.resolve("sftp").toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
    }

    static X12ProducerFacade facade(BufferStore buffer, SchemaRegistry schemas, StubPoller poller, Path dir,
                                    RecastHook recaster) {
        return new X12ProducerFacade(buffer, new ObjectTree(buffer, schemas, poller, config(dir)), schemas,
            new X12Operations(buffer, poller, schemas, recaster));
    }

    /** Seed {@code buffer}; FILE_A's bytes are written under {@code dir}, FILE_B's point at a missing file in {@code dir/sftp}. */
    static void seed(BufferStore buffer, Path dir) throws SQLException, IOException {
        Path aDone = dir.resolve("remit-a.835.done");
        Files.write(aDone, FILE_A_BYTES);

        List<TransactionRow> a = new ArrayList<>();
        a.add(TestRows.tx("1", "0001", 0));
        a.add(TestRows.tx("1", "0002", 10));
        a.add(TestRows.tx("1", "0003", 20));
        buffer.consumeFile(file(FILE_A, "inbox", aDone.toString(), "sha-a", FileStatus.CONSUMED, 3, BASE), a);

        List<TransactionRow> b = new ArrayList<>();
        b.add(TestRows.tx(FILE_B, "sftp", "2", "0001", 30, "005010X222A1", "837P", TestRows.SCHEMA_837P, "CLINIC"));
        b.add(TestRows.tx(FILE_B, "sftp", "3", "0001", 40, GS08_837P_ALT, "837P", SCHEMA_837P_ALT, "CLINIC"));
        buffer.consumeFile(file(FILE_B, "sftp", dir.resolve("sftp").resolve("claims-b.837.done").toString(), "sha-b",
            FileStatus.CONSUMED, 2, BASE.plusSeconds(30)), b);
    }

    static FileRow file(String fileId, String source, String currentPath, String checksum, FileStatus status,
            int txCount, Instant discoveredAt) {
        return new FileRow(0, fileId, FileRow.pathOf(fileId), FileRow.fileNameOf(fileId), source, currentPath,
            FILE_A_BYTES.length, checksum, discoveredAt.minusSeconds(60), discoveredAt,
            status == FileStatus.CONSUMED ? discoveredAt : null, status, 1, txCount,
            status == FileStatus.ERROR ? "boom" : null, false, 0);
    }

    /** A poller stub: one writable source at {@code inboxDir}, records the last rescan argument. */
    static final class StubPoller implements PollerHandle {
        final Path inboxDir;
        String lastRescanSource = "<none>";
        int rescans;

        StubPoller(Path inboxDir) {
            this.inboxDir = inboxDir;
        }

        @Override
        public RescanResult rescan(String source) {
            rescans++;
            lastRescanSource = source;
            return new RescanResult(1, 2, 1, 1);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean up() {
            return true;
        }

        @Override
        public Optional<Instant> lastScan() {
            return Optional.of(BASE.plusSeconds(100));
        }

        @Override
        public Optional<Instant> lastConsumed() {
            return Optional.empty();
        }

        @Override
        public boolean backpressure() {
            return false;
        }

        @Override
        public List<PollerStatus.SourceStatus> sources() {
            return List.of(SourceStatuses.idle("inbox", inboxDir.toString(), true, 0, 0));
        }
    }
}
