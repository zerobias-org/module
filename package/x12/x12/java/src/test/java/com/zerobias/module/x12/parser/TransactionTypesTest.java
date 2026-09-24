package com.zerobias.module.x12.parser;

import com.imsweb.x12.reader.X12Reader;
import com.zerobias.module.x12.materializer.StructureIndex;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The guide table (guides.txt) shared by the parser and the schema codegen. */
class TransactionTypesTest {

    @Test
    void everyAcceptedSpellingResolvesToItsCanonicalGuide() {
        assertEquals(7, TransactionTypes.guides().size());
        assertEquals(Optional.of("005010X222A1"), TransactionTypes.canonical("005010X222"));
        assertEquals(Optional.of("005010X223A2"), TransactionTypes.canonical(" 005010x223a1 "));
        assertEquals(Optional.of("005010X223A2"), TransactionTypes.canonical("005010X223"));
        assertEquals(Optional.of("005010X231A1"), TransactionTypes.canonical("005010X231"));
        assertEquals("277CA", TransactionTypes.transactionType("005010X214", "277"));
        assertEquals("837", TransactionTypes.transactionType("005010X999", "837"), "unknown guide: the bare ST01");
        assertEquals(Optional.of(X12Reader.FileType.ANSI837_5010_X231), TransactionTypes.fileTypeFor("005010X231A1"));
        assertEquals(Optional.empty(), TransactionTypes.fileTypeFor("005010X218"), "820: no imsweb 005010 map");
        assertEquals(Optional.empty(), TransactionTypes.canonical(null));
    }

    /**
     * The parse and the schemas must come from the same pyx12 map: the FileType imsweb parses a
     * guide with loads exactly the map the codegen generated that guide's structure index from.
     */
    @Test
    void everyGuideIsParsedWithTheMapItsSchemasWereGeneratedFrom() throws Exception {
        Field mapping = X12Reader.FileType.class.getDeclaredField("_mapping");
        mapping.setAccessible(true);
        for (TransactionTypes.Guide g : TransactionTypes.guides()) {
            String map = "mapping/" + g.mapFile();
            assertEquals(map, mapping.get(g.fileType()), g.canonicalGs08() + ": imsweb parses with another map");
            StructureIndex idx = StructureIndex.fromClasspath(g.canonicalGs08()).orElseThrow(
                () -> new AssertionError(g.canonicalGs08() + ": no generated structure index"));
            assertEquals(g.canonicalGs08(), idx.gs08);
            assertEquals(g.transactionType(), idx.transactionType, g.canonicalGs08());
            assertEquals(map, idx.mapFile, g.canonicalGs08() + ": schemas generated from another map");
            for (String alias : g.aliases()) {
                assertSame(g, TransactionTypes.guide(alias).orElseThrow(), alias);
            }
        }
    }
}
