package com.github.ezframework.jaloquent.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers Model behaviour not already exercised by ModelFeatureTest:
 * getFillable/getGuarded, set() chaining, constructor with attrs,
 * toMap() id-exclusion, getAs on the "id" key.
 */
public class ModelExtendedTest {

    // -------------------------------------------------------------------------
    // getFillable() / getGuarded()
    // -------------------------------------------------------------------------

    @Test
    void getFillableReturnsRegisteredKeys() {
        TestModel m = new TestModel("1");
        m.setFillable("name", "email");
        Set<String> fillable = m.getFillable();
        assertTrue(fillable.contains("name"));
        assertTrue(fillable.contains("email"));
        assertEquals(2, fillable.size());
    }

    @Test
    void getFillableIsUnmodifiable() {
        TestModel m = new TestModel("1");
        m.setFillable("name");
        assertThrows(UnsupportedOperationException.class,
                () -> m.getFillable().add("extra"));
    }

    @Test
    void getGuardedReturnsRegisteredKeys() {
        TestModel m = new TestModel("1");
        m.setGuarded("password", "token");
        Set<String> guarded = m.getGuarded();
        assertTrue(guarded.contains("password"));
        assertTrue(guarded.contains("token"));
        assertEquals(2, guarded.size());
    }

    @Test
    void getGuardedIsUnmodifiable() {
        TestModel m = new TestModel("1");
        m.setGuarded("secret");
        assertThrows(UnsupportedOperationException.class,
                () -> m.getGuarded().add("extra"));
    }

    @Test
    void setFillableClearsPreviousKeys() {
        TestModel m = new TestModel("1");
        m.setFillable("old");
        m.setFillable("new1", "new2");
        assertFalse(m.getFillable().contains("old"));
        assertTrue(m.getFillable().contains("new1"));
    }

    @Test
    void setGuardedClearsPreviousKeys() {
        TestModel m = new TestModel("1");
        m.setGuarded("old");
        m.setGuarded("new1");
        assertFalse(m.getGuarded().contains("old"));
        assertTrue(m.getGuarded().contains("new1"));
    }

    @Test
    void setFillableIgnoresNullEntries() {
        TestModel m = new TestModel("1");
        m.setFillable("name", null, "email");
        assertFalse(m.getFillable().contains(null));
        assertEquals(2, m.getFillable().size());
    }

    @Test
    void setGuardedIgnoresNullEntries() {
        TestModel m = new TestModel("1");
        m.setGuarded("secret", null);
        assertFalse(m.getGuarded().contains(null));
        assertEquals(1, m.getGuarded().size());
    }

    // -------------------------------------------------------------------------
    // set() chaining
    // -------------------------------------------------------------------------

    @Test
    void setReturnsSameModelForChaining() {
        TestModel m = new TestModel("1");
        assertSame(m, m.set("a", 1));
    }

    @Test
    void setChainedCallsAllApplied() {
        TestModel m = new TestModel("1");
        m.set("a", 1).set("b", 2).set("c", 3);
        assertEquals(1, m.get("a"));
        assertEquals(2, m.get("b"));
        assertEquals(3, m.get("c"));
    }

    @Test
    void setNullValueStoresNull() {
        TestModel m = new TestModel("1");
        m.set("key", "value");
        m.set("key", null);
        assertNull(m.get("key"));
        assertTrue(m.attributes().containsKey("key"));
    }

    // -------------------------------------------------------------------------
    // Constructor with attrs
    // -------------------------------------------------------------------------

    @Test
    void constructorWithAttrsPopulatesAttributes() {
        Map<String, Object> attrs = Map.of("name", "Eve", "level", 5);
        TestModel m = new TestModel("u1", attrs);
        assertEquals("Eve", m.get("name"));
        assertEquals(5, m.get("level"));
    }

    @Test
    void constructorWithNullAttrsDoesNotThrow() {
        assertDoesNotThrow(() -> new TestModel("u2", null));
    }

    @Test
    void constructorWithAttrsDoesNotSetId() {
        Map<String, Object> attrs = Map.of("name", "Eve");
        TestModel m = new TestModel("u3", attrs);
        assertEquals("u3", m.getId());
    }

    // -------------------------------------------------------------------------
    // toMap() id exclusion
    // -------------------------------------------------------------------------

    @Test
    void toMapDoesNotIncludeIdKey() {
        TestModel m = new TestModel("should-not-appear");
        m.set("name", "Alice");
        assertFalse(m.toMap().containsKey("id"),
                "toMap() must not include the 'id' key");
    }

    @Test
    void toMapContainsOnlyNonIdAttributes() {
        TestModel m = new TestModel("x");
        m.set("a", 1).set("b", 2);
        Map<String, Object> map = m.toMap();
        assertEquals(2, map.size());
        assertTrue(map.containsKey("a"));
        assertTrue(map.containsKey("b"));
    }

    // -------------------------------------------------------------------------
    // getAs applied to the "id" key
    // -------------------------------------------------------------------------

    @Test
    void getAsOnIdKeyReturnsId() {
        TestModel m = new TestModel("my-id");
        assertEquals("my-id", m.getAs("id", String.class));
    }

    // -------------------------------------------------------------------------
    // Test fixture
    // -------------------------------------------------------------------------

    static class TestModel extends Model {
        TestModel(String id) { super(id); }
        TestModel(String id, Map<String, Object> attrs) { super(id, attrs); }
    }
}
