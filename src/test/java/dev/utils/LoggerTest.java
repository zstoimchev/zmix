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
}