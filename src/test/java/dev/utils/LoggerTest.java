package dev.utils;

import org.junit.jupiter.api.Test;

class LoggerTest {
    @Test
    void testLogging() {
        Logger logger = Logger.getLogger(LoggerTest.class);
        logger.info("This is an info message");
        logger.info("Argument {} is a number", 235);
        logger.warn("This is a warning message");
        logger.error("This is an error message");

        logger.critical(new RuntimeException("Critical error"), "This is a critical message with {} arguments", 1);

        logger.emergency(new RuntimeException("Emergency error"), "This is an emergency message with {} arguments", 2);
        logger.emergency(new RuntimeException("Emergency error"), "This is an emergency message with 0 arguments");
    }

    @Test
    void testAllLogColors() {
        Logger logger = Logger.getLogger(LoggerTest.class);
        logger.debug("This is a debug message");
        logger.info("This is an info message");
        logger.notice("This is a notice message");
        logger.warn("This is a warning message");
        logger.error("This is an error message");
        logger.critical(null, "This is a critical message");
        logger.alert(null, "This is an alert message");
        logger.emergency(null, "This is an emergency message");
    }

    @Test
    void testUserSpaceLogging() {
        Logger logger = Logger.getLogger(LoggerTest.class);
        Logger.disableConsole();
        logger.printToConsole("""
                This is a user space message.
                It should be printed in cyan color and not be affected by the current log level.
                This will tes the {} formatting that we will be using
                """, 1);
    }
}