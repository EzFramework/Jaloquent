package com.github.ezframework.jaloquent.unit;

import com.github.ezframework.jaloquent.model.BaseModel;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BaseModelTest {

    // -------------------------------------------------------------------------
    // getId() / setId()
    // -------------------------------------------------------------------------

    @Test
    void getIdReturnsIdFromConstructor() {
        Concrete m = new Concrete("abc");
        assertEquals("abc", m.getId());
    }

    @Test
    void setIdUpdatesId() {
        Concrete m = new Concrete("old");
        m.setId("new");
        assertEquals("new", m.getId());
    }

    @Test
    void setIdNullSetsNull() {
        Concrete m = new Concrete("x");
        m.setId(null);
        assertNull(m.getId());
    }

    // -------------------------------------------------------------------------
    // getStoragePath()
    // -------------------------------------------------------------------------

    @Test
    void getStoragePathWithNullPrefixReturnsId() {
        Concrete m = new Concrete("id-1");
        assertEquals("id-1", m.getStoragePath(null));
    }

    @Test
    void getStoragePathWithEmptyPrefixReturnsId() {
        Concrete m = new Concrete("id-2");
        assertEquals("id-2", m.getStoragePath(""));
    }

    @Test
    void getStoragePathWithPrefixReturnsPrefixSlashId() {
        Concrete m = new Concrete("id-3");
        assertEquals("players/id-3", m.getStoragePath("players"));
    }

    @Test
    void getStoragePathCombinesPrefixAndId() {
        Concrete m = new Concrete("uuid-999");
        assertEquals("ns/sub/uuid-999", m.getStoragePath("ns/sub"));
    }

    // -------------------------------------------------------------------------
    // toMap() / fromMap() contract enforced on subclass
    // -------------------------------------------------------------------------

    @Test
    void toMapReturnsMapFromSubclass() {
        Concrete m = new Concrete("1");
        m.value = "hello";
        assertEquals("hello", m.toMap().get("value"));
    }

    @Test
    void fromMapPopulatesSubclassState() {
        Concrete m = new Concrete("1");
        m.fromMap(Map.of("value", "world"));
        assertEquals("world", m.value);
    }

    // -------------------------------------------------------------------------
    // Minimal concrete subclass
    // -------------------------------------------------------------------------

    static class Concrete extends BaseModel {
        String value;

        Concrete(String id) { super(id); }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("value", value == null ? "" : value);
        }

        @Override
        public void fromMap(Map<String, Object> map) {
            if (map != null) value = (String) map.get("value");
        }
    }
}
