package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.model.TableRegistry;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TableRegistryFeatureTest {

    // Use a unique prefix per test to avoid static-state contamination.

    @Test
    void registerAndGetReturnTableMeta() {
        Map<String, String> cols = new HashMap<>();
        cols.put("name", "VARCHAR(255)");
        TableRegistry.register("tr-test-1", "users", cols);

        TableRegistry.TableMeta meta = TableRegistry.get("tr-test-1");
        assertNotNull(meta);
        assertEquals("users", meta.tableName());
    }

    @Test
    void getReturnsNullForUnknownPrefix() {
        assertNull(TableRegistry.get("tr-unknown-prefix"));
    }

    @Test
    void registeredMetaAppearsInAll() {
        Map<String, String> cols = new HashMap<>();
        cols.put("level", "INT");
        TableRegistry.register("tr-test-2", "players", cols);

        assertTrue(TableRegistry.all().containsKey("tr-test-2"));
    }

    @Test
    void allReturnsUnmodifiableMap() {
        assertThrows(UnsupportedOperationException.class,
                () -> TableRegistry.all().put("tr-x", null));
    }

    @Test
    void tableMetaColumnsReturnsCorrectValues() {
        Map<String, String> cols = new HashMap<>();
        cols.put("email", "VARCHAR(100)");
        cols.put("age", "INT");
        TableRegistry.register("tr-test-3", "accounts", cols);

        Map<String, String> returned = TableRegistry.get("tr-test-3").columns();
        assertEquals("VARCHAR(100)", returned.get("email"));
        assertEquals("INT", returned.get("age"));
    }

    @Test
    void tableMetaColumnsIsUnmodifiable() {
        Map<String, String> cols = new HashMap<>();
        cols.put("x", "TEXT");
        TableRegistry.register("tr-test-4", "things", cols);

        assertThrows(UnsupportedOperationException.class,
                () -> TableRegistry.get("tr-test-4").columns().put("y", "INT"));
    }

    @Test
    void mutatingOriginalMapDoesNotAffectRegisteredMeta() {
        Map<String, String> cols = new HashMap<>();
        cols.put("a", "TEXT");
        TableRegistry.register("tr-test-5", "stuff", cols);

        cols.put("b", "INT"); // mutate original after registration

        assertFalse(TableRegistry.get("tr-test-5").columns().containsKey("b"),
                "registered columns should be a snapshot, not the original map");
    }

    @Test
    void registerOverwritesPreviousMeta() {
        Map<String, String> cols1 = new HashMap<>();
        cols1.put("old", "TEXT");
        TableRegistry.register("tr-test-6", "tableA", cols1);

        Map<String, String> cols2 = new HashMap<>();
        cols2.put("new", "INT");
        TableRegistry.register("tr-test-6", "tableB", cols2);

        TableRegistry.TableMeta meta = TableRegistry.get("tr-test-6");
        assertEquals("tableB", meta.tableName());
        assertTrue(meta.columns().containsKey("new"));
        assertFalse(meta.columns().containsKey("old"));
    }

    @Test
    void tableMetaTableNameMatchesRegistered() {
        Map<String, String> cols = new HashMap<>();
        TableRegistry.register("tr-test-7", "exact_table_name", cols);
        assertEquals("exact_table_name", TableRegistry.get("tr-test-7").tableName());
    }
}
