package com.github.ezframework.jaloquent.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages fillable and guarded attribute rules for mass-assignment.
 *
 * <p>Two modes of fillability are supported:
 * <ul>
 *   <li>{@link #isFillable(String)} — permissive: when no fillable keys are
 *       declared, all keys that are not guarded are accepted.</li>
 *   <li>{@link #isExplicitlyFillable(String)} — strict: only keys that have
 *       been explicitly declared via {@link #setFillable(String...)} are
 *       accepted, regardless of guarded rules.</li>
 * </ul>
 *
 * <p>The {@code id} key is always excluded from mass-assignment.
 */
public class Fillable {

    /** Set of fillable (mass-assignable) attribute keys. */
    private final Set<String> fillable = new HashSet<>();

    /** Set of guarded (non-assignable) attribute keys. */
    private final Set<String> guarded  = new HashSet<>();

    /**
     * Replace the fillable (mass-assignable) key set.
     *
     * @param keys attribute names to mark as fillable
     */
    public void setFillable(String... keys) {
        fillable.clear();
        if (keys != null) {
            for (String k : keys) {
                if (k != null) {
                    fillable.add(k);
                }
            }
        }
    }

    /**
     * Replace the guarded (non-assignable) key set.
     *
     * @param keys attribute names to mark as guarded
     */
    public void setGuarded(String... keys) {
        guarded.clear();
        if (keys != null) {
            for (String k : keys) {
                if (k != null) {
                    guarded.add(k);
                }
            }
        }
    }

    /**
     * Return an unmodifiable view of the fillable key set.
     *
     * @return fillable keys
     */
    public Set<String> getFillable() {
        return Collections.unmodifiableSet(fillable);
    }

    /**
     * Return an unmodifiable view of the guarded key set.
     *
     * @return guarded keys
     */
    public Set<String> getGuarded() {
        return Collections.unmodifiableSet(guarded);
    }

    /**
     * Determine whether a key may be mass-assigned (permissive mode).
     *
     * <p>The {@code id} key is never fillable. When the fillable set is
     * non-empty, only declared fillable keys are accepted. Otherwise all
     * keys not present in the guarded set are accepted.
     *
     * @param key attribute name
     * @return {@code true} if the key may be mass-assigned
     */
    public boolean isFillable(String key) {
        if ("id".equals(key)) {
            return false;
        }
        if (!fillable.isEmpty()) {
            return fillable.contains(key);
        }
        return !guarded.contains(key);
    }

    /**
     * Determine whether a key is explicitly declared as fillable (strict mode).
     *
     * <p>Unlike {@link #isFillable(String)}, this returns {@code false} when
     * the fillable set is empty, even if the key is not guarded. Only keys
     * added via {@link #setFillable(String...)} will return {@code true}.
     *
     * @param key attribute name
     * @return {@code true} if the key is explicitly in the fillable set
     */
    public boolean isExplicitlyFillable(String key) {
        if ("id".equals(key)) {
            return false;
        }
        return fillable.contains(key);
    }
}
