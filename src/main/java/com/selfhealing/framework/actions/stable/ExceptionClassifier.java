package com.selfhealing.framework.actions.stable;

import org.openqa.selenium.*;

public class ExceptionClassifier {

    public static RetryDecision classify(Throwable ex) {
        if (ex == null) return RetryDecision.FAIL_FAST;

        if (ex instanceof StaleElementReferenceException)    return RetryDecision.RETRY;
        if (ex instanceof ElementClickInterceptedException)  return RetryDecision.RETRY_WITH_RECOVERY;
        if (ex instanceof ElementNotInteractableException)   return RetryDecision.RETRY_WITH_RECOVERY;
        if (ex instanceof NoSuchElementException)            return RetryDecision.RETRY;
        if (ex instanceof TimeoutException)                  return RetryDecision.RETRY_WITH_RECOVERY;
        if (ex instanceof InvalidSelectorException)          return RetryDecision.FAIL_FAST;
        if (ex instanceof JavascriptException)               return RetryDecision.FAIL_FAST;

        return RetryDecision.RETRY;
    }
}
