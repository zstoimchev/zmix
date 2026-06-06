package dev.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class Logger {
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public enum LogLevel {DEBUG, INFO, NOTICE, WARNING, ERROR, CRITICAL, ALERT, EMERGENCY}
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


    private static void log(Throwable t, String contextName, String message, LogLevel level) {
        String date = dateFormat.format(LocalDateTime.now());
        String threadName = Thread.currentThread().getName();

        String context = contextName != null ? "[" + contextName + "]" : "";
        String prefix = "[" + date + "][" + threadName + "]" + context + " " + level + ": ";
        String plain = colorize(prefix, level) + message;

        // todo: write in file here

        if (!CONSOLE_ENABLED.get()) return;
        System.out.println(plain);
        if (t != null) t.printStackTrace(System.err);
    }

    private static String colorize(String line, LogLevel level) {
        return switch (level) {
            case DEBUG -> CYAN + line + RESET;
            case INFO -> GREEN + line + RESET;
            case NOTICE -> BLUE + line + RESET;
            case WARNING -> YELLOW + line + RESET;
            case ERROR -> RED + line + RESET;
            case CRITICAL -> PURPLE + line + RESET;
            case ALERT, EMERGENCY -> RED + PURPLE + line + RESET;
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