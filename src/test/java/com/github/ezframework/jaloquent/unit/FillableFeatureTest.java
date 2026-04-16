package com.github.ezframework.jaloquent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FillableFeatureTest {

    // -------------------------------------------------------------------------
    // getFillable() / getGuarded()
    // -------------------------------------------------------------------------

    @Test
    void getFillableIsEmptyByDefault() {
        Fillable f = new Fillable();
        assertTrue(f.getFillable().isEmpty());
    }

    @Test
    void getGuardedIsEmptyByDefault() {
        Fillable f = new Fillable();
        assertTrue(f.getGuarded().isEmpty());
    }

    @Test
    void getFillableReflectsDeclaredKeys() {
        Fillable f = new Fillable();
        f.setFillable("name", "email");
        assertTrue(f.getFillable().contains("name"));
        assertTrue(f.getFillable().contains("email"));
        assertEquals(2, f.getFillable().size());
    }

    @Test
    void getGuardedReflectsDeclaredKeys() {
        Fillable f = new Fillable();
        f.setGuarded("secret", "password");
        assertTrue(f.getGuarded().contains("secret"));
        assertTrue(f.getGuarded().contains("password"));
        assertEquals(2, f.getGuarded().size());
    }

    @Test
    void getFillableIsUnmodifiable() {
        Fillable f = new Fillable();
        f.setFillable("name");
        assertThrows(UnsupportedOperationException.class, () -> f.getFillable().add("other"));
    }

    @Test
    void getGuardedIsUnmodifiable() {
        Fillable f = new Fillable();
        f.setGuarded("secret");
        assertThrows(UnsupportedOperationException.class, () -> f.getGuarded().add("other"));
    }

    // -------------------------------------------------------------------------
    // setFillable() / setGuarded() — replace behavior
    // -------------------------------------------------------------------------

    @Test
    void setFillableReplacesPreviousSet() {
        Fillable f = new Fillable();
        f.setFillable("old");
        f.setFillable("new");
        assertFalse(f.getFillable().contains("old"));
        assertTrue(f.getFillable().contains("new"));
    }

    @Test
    void setGuardedReplacesPreviousSet() {
        Fillable f = new Fillable();
        f.setGuarded("old");
        f.setGuarded("new");
        assertFalse(f.getGuarded().contains("old"));
        assertTrue(f.getGuarded().contains("new"));
    }

    @Test
    void setFillableNullArgumentClearsSet() {
        Fillable f = new Fillable();
        f.setFillable("name");
        f.setFillable((String[]) null);
        assertTrue(f.getFillable().isEmpty());
    }

    @Test
    void setFillableIgnoresNullElements() {
        Fillable f = new Fillable();
        f.setFillable("name", null, "email");
        assertEquals(2, f.getFillable().size());
        assertTrue(f.getFillable().contains("name"));
        assertTrue(f.getFillable().contains("email"));
    }

    @Test
    void setGuardedIgnoresNullElements() {
        Fillable f = new Fillable();
        f.setGuarded(null, "secret");
        assertEquals(1, f.getGuarded().size());
        assertTrue(f.getGuarded().contains("secret"));
    }

    // -------------------------------------------------------------------------
    // isFillable() — permissive mode
    // -------------------------------------------------------------------------

    @Test
    void isFillableReturnsTrueForAnyKeyWhenNoRulesDeclared() {
        Fillable f = new Fillable();
        assertTrue(f.isFillable("name"));
        assertTrue(f.isFillable("email"));
    }

    @Test
    void isFillableReturnsFalseForIdAlways() {
        Fillable f = new Fillable();
        assertFalse(f.isFillable("id"));
    }

    @Test
    void isFillableReturnsFalseForIdEvenWhenInFillableSet() {
        Fillable f = new Fillable();
        f.setFillable("id", "name");
        assertFalse(f.isFillable("id"));
    }

    @Test
    void isFillableReturnsTrueOnlyForDeclaredKeysWhenFillableSetNonEmpty() {
        Fillable f = new Fillable();
        f.setFillable("name", "email");
        assertTrue(f.isFillable("name"));
        assertTrue(f.isFillable("email"));
        assertFalse(f.isFillable("other"));
    }

    @Test
    void isFillableReturnsFalseForGuardedKeyWhenNoFillableSetDeclared() {
        Fillable f = new Fillable();
        f.setGuarded("secret");
        assertFalse(f.isFillable("secret"));
    }

    @Test
    void isFillableReturnsTrueForNonGuardedKeyWhenNoFillableSetDeclared() {
        Fillable f = new Fillable();
        f.setGuarded("secret");
        assertTrue(f.isFillable("name"));
    }

    // -------------------------------------------------------------------------
    // isExplicitlyFillable() — strict mode
    // -------------------------------------------------------------------------

    @Test
    void isExplicitlyFillableReturnsFalseWhenNoFillableSetDeclared() {
        Fillable f = new Fillable();
        assertFalse(f.isExplicitlyFillable("name"));
    }

    @Test
    void isExplicitlyFillableReturnsTrueOnlyForDeclaredKeys() {
        Fillable f = new Fillable();
        f.setFillable("name", "email");
        assertTrue(f.isExplicitlyFillable("name"));
        assertTrue(f.isExplicitlyFillable("email"));
        assertFalse(f.isExplicitlyFillable("other"));
    }

    @Test
    void isExplicitlyFillableReturnsFalseForIdAlways() {
        Fillable f = new Fillable();
        f.setFillable("id", "name");
        assertFalse(f.isExplicitlyFillable("id"));
    }

    @Test
    void isExplicitlyFillableIgnoresGuardedRules() {
        // isExplicitlyFillable only checks the fillable set, not guarded
        Fillable f = new Fillable();
        f.setFillable("name");
        f.setGuarded("name"); // guarded should not affect explicit fillable check
        assertTrue(f.isExplicitlyFillable("name"));
    }
}
