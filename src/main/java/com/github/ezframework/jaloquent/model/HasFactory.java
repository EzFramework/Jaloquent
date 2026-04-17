package com.github.ezframework.jaloquent.model;

/**
 * Marker interface for models that support the {@link Factory} pattern.
 *
 * <p>Implement this interface and add a one-liner static {@code factory()} method
 * to each model class:
 * <pre>{@code
 * public class Player extends Model implements HasFactory {
 *
 *     public static PlayerFactory factory() {
 *         return Factory.discover(Player.class);
 *     }
 * }
 * }</pre>
 *
 * <p>This is the Java equivalent of Laravel's {@code HasFactory} trait.
 * The marker signals that a discoverable {@link Factory} exists for the model,
 * and allows {@link Factory#discover(Class)} to accept the model class as input.
 */
public interface HasFactory {

}
