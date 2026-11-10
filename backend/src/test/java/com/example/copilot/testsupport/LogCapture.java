package com.example.copilot.testsupport;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;

/** Collects the Log4j2 events of one logger while open; closing detaches it and restores the level. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level oldLevel;
    private final ListAppender appender = new ListAppender();

    private LogCapture(String loggerName, Level level) {
        logger = (Logger) LogManager.getLogger(loggerName);
        oldLevel = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        // After addAppender: it rebuilds the logger config, which would drop a level set earlier
        if (level != null) {
            Configurator.setLevel(logger.getName(), level);
        }
    }

    public static LogCapture of(Class<?> type) {
        return new LogCapture(type.getName(), null);
    }

    public static LogCapture of(Class<?> type, Level level) {
        return new LogCapture(type.getName(), level);
    }

    public static LogCapture of(String loggerName) {
        return new LogCapture(loggerName, null);
    }

    public static LogCapture of(String loggerName, Level level) {
        return new LogCapture(loggerName, level);
    }

    public List<LogEvent> events() {
        return List.copyOf(appender.events);
    }

    /** Formatted message followed by the stack trace, if any. */
    public static String render(LogEvent event) {
        StringWriter out = new StringWriter();
        out.write(event.getMessage().getFormattedMessage());
        if (event.getThrown() != null) {
            event.getThrown().printStackTrace(new PrintWriter(out));
        }
        return out.toString();
    }

    @Override
    public void close() {
        logger.removeAppender(appender);
        Configurator.setLevel(logger.getName(), oldLevel);
        appender.stop();
    }

    private static final class ListAppender extends AbstractAppender {

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        ListAppender() {
            super("LogCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
