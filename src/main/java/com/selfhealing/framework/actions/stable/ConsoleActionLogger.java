package com.selfhealing.framework.actions.stable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConsoleActionLogger implements ActionLogger {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    @Override
    public void info(String message) {
        System.out.println(now() + " [INFO] " + message);
    }

    @Override
    public void warn(String message) {
        System.out.println(now() + " [WARN] " + message);
    }

    @Override
    public void error(String message) {
        System.out.println(now() + " [ERROR] " + message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        System.out.println(now() + " [ERROR] " + message);
        if (throwable != null) throwable.printStackTrace(System.out);
    }
}
