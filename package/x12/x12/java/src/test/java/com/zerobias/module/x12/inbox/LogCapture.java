package com.zerobias.module.x12.inbox;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * What the module logs while {@code body} runs. slf4j-simple resolves {@code System.err} at
 * call time (its default target), so swapping it captures exactly what the container log
 * would show, at the default INFO level.
 */
final class LogCapture {

    @FunctionalInterface
    interface Body {
        void run() throws Exception;
    }

    private LogCapture() {
    }

    static String of(Body body) throws Exception {
        PrintStream original = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setErr(original);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
