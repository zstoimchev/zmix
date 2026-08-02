package dev.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class Logger {
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public enum LogLevel {DEBUG, INFO, NOTICE, WARNING, ERROR, CRITICAL, ALERT, EMERGENCY, USER_SPACE}
    private static LogLevel MIN_LOG_LEVEL = LogLevel.INFO;
    private final String contextName;

    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private Logger(Class<?> clazz) {
        this.contextName = clazz.getSimpleName();
    }
    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz);
    }

    private static final AtomicBoolean CONSOLE_ENABLED = new AtomicBoolean(true);
    public static void enableConsole() {
        CONSOLE_ENABLED.set(true);
    }
    public static void disableConsole() {
        CONSOLE_ENABLED.set(false);
    }
    public static boolean isConsoleEnabled() {
        return CONSOLE_ENABLED.get();
    }

    private static final PrintStream FILE_OUT;

    // invoked only once when the application starts
    static {
        PrintStream ps = null;

        try {
            Properties properties = new Properties();
            try (InputStream in = Logger.class.getResourceAsStream("/logger.properties")) {
                if (in != null) properties.load(in);
            }

            String logDirectory = properties.getProperty("logger.directory", "logs");
            Files.createDirectories(Paths.get(logDirectory));

            String prefix = properties.getProperty("logger.prefix", "zmix");
            String fileName = prefix + "-" + LocalDate.now() + ".log";

            Path logFile = Paths.get(logDirectory, fileName);
            ps = new PrintStream(new FileOutputStream(logFile.toFile(), true),true);

            getMinLogLevel(properties);
        } catch (IOException e) {
            System.err.println("[Logger] Could not open log file: " + e.getMessage());
        }

        FILE_OUT = ps;
    }

    private static void getMinLogLevel(Properties properties) {
        try {
            String level = properties.getProperty("logger.level.min", "DEBUG");
            MIN_LOG_LEVEL = LogLevel.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("[Logger] Invalid log level in configuration: " + e.getMessage() + ". Defaulting to DEBUG.");
            MIN_LOG_LEVEL = LogLevel.DEBUG;
        }
    }

    private static void log(Throwable t, String contextName, String message, LogLevel level) {
        if (level == LogLevel.USER_SPACE) {
            System.out.println(message);
            return;
        }

        String date = dateFormat.format(LocalDateTime.now());
        String threadName = Thread.currentThread().getName();

        String context = contextName != null ? "[" + contextName + "]" : "";
        String prefix = "[" + date + "][" + threadName + "]" + context + " " + level + ": ";
        String plain = colorize(prefix, level) + message;

        if (FILE_OUT != null) {
            FILE_OUT.println(prefix + message);
            if (t != null) t.printStackTrace(FILE_OUT);
        }

        if (!isConsoleEnabled() && isLowPriority(level)) return;
        if (level.ordinal() < MIN_LOG_LEVEL.ordinal()) return;
        System.out.println(plain);
        if (t != null) t.printStackTrace(System.err);
    }

    private static String colorize(String line, LogLevel level) {
        return switch (level) {
            case USER_SPACE -> line;
            case DEBUG -> CYAN + line + RESET;
            case INFO -> GREEN + line + RESET;
            case NOTICE -> BLUE + line + RESET;
            case WARNING -> YELLOW + line + RESET;
            case ERROR -> RED + line + RESET;
            case CRITICAL -> PURPLE + line + RESET;
            case ALERT, EMERGENCY -> RED + PURPLE + line + RESET;
        };
    }

    private static boolean isLowPriority(LogLevel level) {
        return switch (level) {
            case USER_SPACE, DEBUG, INFO, NOTICE, WARNING -> true;
            default -> false;
        };
    }

    /* ********************************************************* *
     * INSTANCE METHODS FOR LOGGING WITH LOGGER INSTANCE         *
     * ********************************************************* */

    public void info(String message, Object... args) {
        log(null, contextName, format(message, args), LogLevel.INFO);
    }

    public void debug(String message, Object... args) {
        log(null, contextName, format(message, args), LogLevel.DEBUG);
    }

    public void notice(String message, Object... args) {
        log(null, contextName, format(message, args), LogLevel.NOTICE);
    }

    public void warn(Throwable t, String message, Object... args) {
        log(t, contextName, format(message, args), LogLevel.WARNING);
    }

    public void warn(String message, Object... args) {
        warn(null, message, args);
    }

    public void error(Throwable t, String message, Object... args) {
        log(t, contextName, format(message, args), LogLevel.ERROR);
    }

    public void error(String message, Object... args) {
        error(null, message, args);
    }

    public void critical(Throwable t, String message, Object... args) {
        log(t, contextName, format(message, args), LogLevel.CRITICAL);
    }

    public void alert(Throwable t, String message, Object... args) {
        log(t, contextName, format(message, args), LogLevel.ALERT);
    }

    public void emergency(Throwable t, String message, Object... args) {
        log(t, contextName, format(message, args), LogLevel.EMERGENCY);
    }

    public void printToConsole(String message, Object... args) {
        log(null,  contextName, format(message, args), LogLevel.USER_SPACE);
    }

    private static String format(String template, Object... args) {
        if (args == null || args.length == 0) return template;

        StringBuilder stringBuilder = new StringBuilder();
        int argIdx = 0, start = 0, pos;
        while ((pos = template.indexOf("{}", start)) != -1 && argIdx < args.length) {
            stringBuilder.append(template, start, pos);
            stringBuilder.append(args[argIdx++]);
            start = pos + 2;
        }
        stringBuilder.append(template, start, template.length());
        return stringBuilder.toString();
    }
}