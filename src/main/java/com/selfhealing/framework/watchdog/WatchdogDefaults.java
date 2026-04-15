package com.selfhealing.framework.watchdog;

import org.openqa.selenium.By;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WatchdogDefaults {

    public static final List<String> LOADER_SELECTORS = new ArrayList<>(Arrays.asList(
            ".loading", ".spinner", ".loader", "[data-testid='loader']"
    ));

    public static final List<String> OVERLAY_SELECTORS = new ArrayList<>(Arrays.asList(
            ".modal-backdrop", ".overlay", ".block-ui-wrapper", ".ui-widget-overlay"
    ));

    public static final List<String> MODAL_SELECTORS = new ArrayList<>(Arrays.asList(
            ".modal", "[role='dialog']", ".ui-dialog", ".popup"
    ));

    public static final List<String> ERROR_SELECTORS = new ArrayList<>(Arrays.asList(
            ".error", ".alert", ".alert-danger", ".message-error", ".notification-error"
    ));

    public static final List<String> OVERLAY_SELECTORS_ELEMENT = List.of(
            "[class*='overlay' i]", "[id*='overlay' i]", "[class*='backdrop' i]"
    );

    public static final List<String> MODAL_SELECTORS_ELEMENT = List.of(
            "[role='dialog']", "[role='alertdialog']", "[aria-modal='true']",
            ".modal.show", ".modal[style*='display: block']",
            "[class*='dialog' i][class*='show' i]", "[class*='popup' i]",
            "[id*='modal' i]", "[class*='modal' i]", "[class*='dialog' i]"
    );

    public static final List<By> CLOSE_SELECTORS = List.of(
            By.cssSelector("[aria-label*='cerrar' i]"),
            By.cssSelector("[aria-label*='close' i]"),
            By.cssSelector("[title*='cerrar' i]"),
            By.cssSelector("[title*='close' i]"),
            By.cssSelector(".btn-close"),
            By.cssSelector(".close"),
            By.cssSelector("[data-dismiss]"),
            By.cssSelector("[data-bs-dismiss]"),
            By.cssSelector("[class*='close' i]"),
            By.cssSelector("[id*='close' i]")
    );
}
