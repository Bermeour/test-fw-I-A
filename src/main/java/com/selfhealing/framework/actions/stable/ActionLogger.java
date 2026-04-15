package com.selfhealing.framework.actions.stable;

public interface ActionLogger {
    void info(String message);
    void warn(String message);
    void error(String message);
    void error(String message, Throwable throwable);
}
