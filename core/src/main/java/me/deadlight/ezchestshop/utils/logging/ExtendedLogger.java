package me.deadlight.ezchestshop.utils.logging;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.util.Objects;
import java.util.function.Predicate;

public final class ExtendedLogger extends DelegateLogger {
    private final @NotNull Predicate<Level> filter;

    public ExtendedLogger(@NotNull Logger delegate, @NotNull Predicate<Level> filter) {
        super(delegate);
        this.filter = Objects.requireNonNull(filter, "Filter must not be null");
    }

    // Implementation Note:
    //
    // This class delegates trace and debug calls to info-level when logging is enabled for that type.
    // The reasoning behind this is to enable use of SLF4J in the plugin, without having to wrap debug logging at callsite.

    @Override
    public void trace(String s) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(s);
        }
    }

    @Override
    public void trace(String s, Object o) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(s, o);
        }
    }

    @Override
    public void trace(String s, Object o, Object o1) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(s, o, o1);
        }
    }

    @Override
    public void trace(String s, Object... objects) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(s, objects);
        }
    }

    @Override
    public void trace(String s, Throwable throwable) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(s, throwable);
        }
    }

    @Override
    public void trace(Marker marker, String s) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(marker, s);
        }
    }

    @Override
    public void trace(Marker marker, String s, Object o) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(marker, s, o);
        }
    }

    @Override
    public void trace(Marker marker, String s, Object o, Object o1) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(marker, s, o, o1);
        }
    }

    @Override
    public void trace(Marker marker, String s, Object... objects) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(marker, s, objects);
        }
    }

    @Override
    public void trace(Marker marker, String s, Throwable throwable) {
        if (filter.test(Level.TRACE)) {
            getDelegate().info(marker, s, throwable);
        }
    }

    @Override
    public void debug(String s) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(s);
        }
    }

    @Override
    public void debug(String s, Object o) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(s, o);
        }
    }

    @Override
    public void debug(String s, Object o, Object o1) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(s, o, o1);
        }
    }

    @Override
    public void debug(String s, Object... objects) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(s, objects);
        }
    }

    @Override
    public void debug(String s, Throwable throwable) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(s, throwable);
        }
    }

    @Override
    public void debug(Marker marker, String s) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(marker, s);
        }
    }

    @Override
    public void debug(Marker marker, String s, Object o) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(marker, s, o);
        }
    }

    @Override
    public void debug(Marker marker, String s, Object o, Object o1) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(marker, s, o, o1);
        }
    }

    @Override
    public void debug(Marker marker, String s, Object... objects) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(marker, s, objects);
        }
    }

    @Override
    public void debug(Marker marker, String s, Throwable throwable) {
        if (filter.test(Level.DEBUG)) {
            getDelegate().info(marker, s, throwable);
        }
    }
}
