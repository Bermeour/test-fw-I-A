package com.selfhealing.framework.watchdog;

import org.openqa.selenium.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.selfhealing.framework.watchdog.WatchdogDefaults.MODAL_SELECTORS_ELEMENT;
import static com.selfhealing.framework.watchdog.WatchdogDefaults.OVERLAY_SELECTORS_ELEMENT;

/**
 * Inspecciona el estado actual de la UI para detectar modales, loaders,
 * overlays, errores y alertas nativas que podrían bloquear la ejecución.
 *
 * Uso:
 *   UiWatchdog watchdog = new UiWatchdog(driver, WatchdogConfig.defaultConfig());
 *   WatchdogResult result = watchdog.inspect();
 *   if (result.isBlocking()) { ... }
 */
public class UiWatchdog {

    private final WebDriver driver;
    private final WatchdogConfig watchdogConfig;

    public UiWatchdog(WebDriver driver) {
        this(driver, WatchdogConfig.defaultConfig());
    }

    public UiWatchdog(WebDriver driver, WatchdogConfig watchdogConfig) {
        this.driver = driver;
        this.watchdogConfig = watchdogConfig != null ? watchdogConfig : WatchdogConfig.defaultConfig();
    }

    public WatchdogResult inspect() {
        if (!watchdogConfig.isEnabled()) return WatchdogResult.clean();

        WatchdogResult alertResult = detectAlert();
        if (alertResult != null) return alertResult;

        WatchdogResult errorResult = detectVisibleSelectors(
                watchdogConfig.getErrorSelectors(),
                WatchdogStatus.ERROR_DETECTED,
                "Se detectó mensaje/componente de error visible",
                true
        );
        if (errorResult != null) return errorResult;

        WatchdogResult modalResult = detectModal();
        if (modalResult != null) return modalResult;

        WatchdogResult overlayResult = detectVisibleSelectors(
                mergeSelectors(watchdogConfig.getOverlaySelectors(), OVERLAY_SELECTORS_ELEMENT),
                WatchdogStatus.OVERLAY_DETECTED,
                "Se detectó overlay visible",
                watchdogConfig.isOverlaysBlockExecution()
        );
        if (overlayResult != null) return overlayResult;

        WatchdogResult loaderResult = detectVisibleSelectors(
                watchdogConfig.getLoaderSelectors(),
                WatchdogStatus.LOADER_DETECTED,
                "Se detectó loader o spinner visible",
                watchdogConfig.isLoadersBlockExecution()
        );
        if (loaderResult != null) return loaderResult;

        return WatchdogResult.clean();
    }

    public WebElement findDetectedElement(WatchdogResult result) {
        if (result == null) return null;
        String selector = safeTrim(result.getMatchedSelector());
        if (selector == null || selector.isBlank()) return null;
        try {
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            for (WebElement element : elements) {
                if (isEffectivelyVisible(element)) return element;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private WatchdogResult detectAlert() {
        try {
            Alert alert = driver.switchTo().alert();
            String text = safeTrim(alert.getText());
            return new WatchdogResult(
                    WatchdogStatus.ALERT_DETECTED,
                    "Se detectó alerta nativa del navegador",
                    true, null, text, Instant.now()
            );
        } catch (NoAlertPresentException | Exception e) {
            return null;
        }
    }

    private WatchdogResult detectModal() {
        List<String> modalSelectors = mergeSelectors(
                watchdogConfig.getModalSelectors(), MODAL_SELECTORS_ELEMENT
        );
        WatchdogResult configuredModal = detectVisibleSelectors(
                modalSelectors,
                WatchdogStatus.MODAL_DETECTED,
                "Se detectó modal o diálogo visible",
                true
        );
        if (configuredModal != null) return configuredModal;
        return detectBlockingElementAtViewportCenter();
    }

    private WatchdogResult detectVisibleSelectors(List<String> selectors,
                                                  WatchdogStatus status,
                                                  String defaultDetail,
                                                  boolean blocking) {
        if (selectors == null || selectors.isEmpty()) return null;

        for (String selector : selectors) {
            if (selector == null || selector.trim().isEmpty()) continue;
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    if (!isEffectivelyVisible(element)) continue;
                    if (status == WatchdogStatus.MODAL_DETECTED && !looksLikeModalContainer(element)) continue;
                    return new WatchdogResult(
                            status,
                            defaultDetail + " [" + selector + "]",
                            blocking,
                            selector,
                            safeExtractText(element),
                            Instant.now()
                    );
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private WatchdogResult detectBlockingElementAtViewportCenter() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(
                    "const x = Math.floor(window.innerWidth / 2);" +
                    "const y = Math.floor(window.innerHeight / 2);" +
                    "let el = document.elementFromPoint(x, y);" +
                    "while (el) {" +
                    "  const id = (el.id || '').toLowerCase();" +
                    "  const cls = (el.className || '').toString().toLowerCase();" +
                    "  const role = (el.getAttribute('role') || '').toLowerCase();" +
                    "  const ariaModal = (el.getAttribute('aria-modal') || '').toLowerCase();" +
                    "  if (role === 'dialog' || role === 'alertdialog' || ariaModal === 'true' || " +
                    "      id.includes('modal') || cls.includes('modal') || cls.includes('dialog') || cls.includes('popup')) {" +
                    "    return { tag: el.tagName, id: el.id || null, cls: el.className ? el.className.toString() : null, text: (el.innerText || '').trim() };" +
                    "  }" +
                    "  el = el.parentElement;" +
                    "}" +
                    "return null;"
            );

            if (result instanceof Map<?, ?> map) {
                String id  = map.get("id")  == null ? null : String.valueOf(map.get("id"));
                String cls = map.get("cls") == null ? null : String.valueOf(map.get("cls"));
                String text = map.get("text") == null ? null : String.valueOf(map.get("text"));
                String selector = buildCssSelectorFromIdOrClass(id, cls);
                if (selector == null || selector.isBlank()) return null;

                try {
                    List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                    for (WebElement element : elements) {
                        if (isEffectivelyVisible(element) && looksLikeModalContainer(element)) {
                            return new WatchdogResult(
                                    WatchdogStatus.MODAL_DETECTED,
                                    "Se detectó contenedor modal/bloqueante en el centro del viewport",
                                    true, selector, safeTrim(text), Instant.now()
                            );
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean looksLikeModalContainer(WebElement element) {
        try {
            if (element == null || !isEffectivelyVisible(element)) return false;
            String tag = safeLower(element.getTagName());
            if (tag == null || tag.isBlank()) return false;
            if (List.of("input", "label", "button", "a", "option", "svg").contains(tag)) return false;

            String role      = safeLower(element.getAttribute("role"));
            String ariaModal = safeLower(element.getAttribute("aria-modal"));
            String className = safeLower(element.getAttribute("class"));
            String id        = safeLower(element.getAttribute("id"));

            if ("dialog".equals(role) || "alertdialog".equals(role) || "true".equals(ariaModal)) return true;

            boolean modalHint = containsAny(className, "modal", "dialog", "popup")
                    || containsAny(id, "modal", "dialog", "popup");
            if (!modalHint) return false;

            Dimension size = element.getSize();
            if (size == null || size.getWidth() < 200 || size.getHeight() < 100) return false;

            int childControls = element.findElements(By.cssSelector(
                    "button, input, textarea, select, form, [role='button'], " +
                    ".modal-header, .modal-body, .modal-footer"
            )).size();
            return childControls > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isEffectivelyVisible(WebElement element) {
        try {
            if (element == null || !element.isDisplayed()) return false;
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object visible = js.executeScript(
                    "const el = arguments[0];" +
                    "if (!el) return false;" +
                    "const rect = el.getBoundingClientRect();" +
                    "const style = window.getComputedStyle(el);" +
                    "return (rect.width > 0 && rect.height > 0 && " +
                    "style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0');",
                    element
            );
            return Boolean.TRUE.equals(visible);
        } catch (Exception e) {
            return false;
        }
    }

    private String buildCssSelectorFromIdOrClass(String id, String cls) {
        String safeId = safeTrim(id);
        if (safeId != null && !safeId.isBlank()) return "#" + cssEscape(safeId);
        String firstClass = extractFirstClassToken(cls);
        if (firstClass != null && !firstClass.isBlank()) return "." + cssEscape(firstClass);
        return null;
    }

    private String extractFirstClassToken(String classAttr) {
        String value = safeTrim(classAttr);
        if (value == null || value.isBlank()) return null;
        String[] tokens = value.split("\\s+");
        return tokens.length == 0 ? null : safeTrim(tokens[0]);
    }

    private List<String> mergeSelectors(List<String> base, List<String> extra) {
        List<String> result = new ArrayList<>();
        if (base != null) result.addAll(base);
        if (extra != null) {
            for (String s : extra) {
                if (s != null && !s.isBlank() && !result.contains(s)) result.add(s);
            }
        }
        return result;
    }

    private String safeExtractText(WebElement element) {
        try {
            String text = safeTrim(element.getText());
            if (text != null && !text.isBlank()) return text;
            String aria = safeTrim(element.getAttribute("aria-label"));
            if (aria != null && !aria.isBlank()) return aria;
            String title = safeTrim(element.getAttribute("title"));
            if (title != null && !title.isBlank()) return title;
            return safeTrim(element.getAttribute("id"));
        } catch (Exception e) {
            return null;
        }
    }

    private String cssEscape(String value) {
        return value.replace(":", "\\:").replace(".", "\\.").replace("[", "\\[")
                    .replace("]", "\\]").replace(" ", "\\ ");
    }

    private String safeLower(String value)  { return value == null ? null : value.trim().toLowerCase(); }
    private String safeTrim(String value)   { return value == null ? null : value.trim(); }

    private boolean containsAny(String source, String... tokens) {
        if (source == null) return false;
        for (String token : tokens) {
            if (token != null && source.contains(token.toLowerCase())) return true;
        }
        return false;
    }
}
