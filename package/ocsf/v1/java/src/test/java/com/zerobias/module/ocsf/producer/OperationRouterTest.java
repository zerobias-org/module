package com.zerobias.module.ocsf.producer;

import com.zerobias.module.ocsf.OcsfClasses;
import com.zerobias.module.ocsf.TestSupport;
import com.zerobias.module.ocsf.buffer.BufferStore;
import com.zerobias.module.ocsf.buffer.FileStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.zerobias.module.ocsf.TestSupport.AUTHENTICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generic OCSF branch resolves only what the interface declares, under its own API class. */
class OperationRouterTest {

    @TempDir
    Path dir;

    private BufferStore buffer;
    private ProducerFacade readOnly;
    private ProducerFacade writable;

    @BeforeEach
    void setUp() throws Exception {
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        buffer.consumeFile(TestSupport.file(TestSupport.FILE_A, "c1", FileStatus.CONSUMED),
            List.of(TestSupport.eventRow(TestSupport.FILE_A, "e1", 1000)));
        ObjectTree tree = new ObjectTree(buffer, new TestSupport.StubPoller(dir), ".done", List.of(), ".error");
        readOnly = new ProducerFacade(buffer, tree, TestSupport.classes(), false);
        writable = new ProducerFacade(buffer, tree, TestSupport.classes(), true);
    }

    @AfterEach
    void tearDown() throws Exception {
        buffer.close();
    }

    private String key(String method, Map<String, Object> args) {
        return assertThrows(ProducerException.class,
            () -> OperationRouter.executeOperation(readOnly, method, new HashMap<>(args))).key();
    }

    @Test
    void theClassTableIsTheInterfacesOperations() {
        OcsfClasses c = TestSupport.classes();
        assertEquals(79, c.all().size(), "every class with list/get operations in OCSF 1.9.0 core");
        assertEquals("IamApi", c.byUid(AUTHENTICATION).orElseThrow().apiClass());
        assertTrue(c.byUid(0).isEmpty(), "base_event is a model with no operations");
        assertEquals("UnmannedSystemsApi", c.byModel("DroneFlightsActivity").orElseThrow().apiClass(), "snake_case tag");
    }

    @Test
    void anOperationIsServedOnlyUnderItsOwnApiClass() throws Exception {
        assertTrue(OperationRouter.executeOperation(readOnly, "IamApi.listAuthentication", new HashMap<>()).contains("\"e1\""));
        assertEquals("err.unsupported.operation", key("SystemApi.listAuthentication", Map.of()));
        assertEquals("err.unsupported.operation", key("IamApi.listNoSuchClass", Map.of()));
        assertEquals("err.no.such.object", key("SystemApi.getFileActivity", Map.of("uid", "e1")),
            "a key held by another class is not found");
    }

    @Test
    void callerMistakesAre400s() {
        assertEquals("err.unsupported.operation", key("IamApi.listAuthentication", Map.of("pageToken", "abc")));
        assertEquals("err.illegal.argument", key("IamApi.listAuthentication", Map.of("startTime", 5, "endTime", 5)));
        assertEquals("err.illegal.argument", key("IamApi.listAuthentication", Map.of("startTime", "yesterday")));
        assertEquals("err.illegal.argument", key("IamApi.listAuthentication", Map.of("pageSize", 1001)));
        assertEquals("err.illegal.argument", key("IamApi.getAuthentication", Map.of()));
    }

    @Test
    void isSupportedFollowsTheRoutesAndTheFileManagementGate() {
        for (String op : List.of("IamApi.listAuthentication", "listAuthentication", "getAuthentication",
                "ClassesApi.listClasses", "listClasses", "ObjectsApi.getChildren", "BinaryApi.downloadBinary")) {
            assertTrue(OperationRouter.isSupported(op, readOnly), op);
        }
        for (String op : List.of("SystemApi.listAuthentication", "listBaseEvent", "ObjectsApi.updateObject", "nope")) {
            assertFalse(OperationRouter.isSupported(op, readOnly), op);
        }
        assertFalse(OperationRouter.isSupported("BinaryApi.uploadBinaryContent", readOnly));
        assertTrue(OperationRouter.isSupported("BinaryApi.uploadBinaryContent", writable));
    }
}
