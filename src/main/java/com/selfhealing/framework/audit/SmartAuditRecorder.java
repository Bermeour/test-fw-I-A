package com.selfhealing.framework.audit;

/**
 * Registra eventos del ciclo de vida del self-healing para auditoría y análisis.
 *
 * Uso:
 *   SmartAuditRecorder recorder = new SmartAuditRecorder("my-app", "AUTO", "//button", driver.getCurrentUrl());
 *   recorder.record(SmartAuditEventType.DIRECT_FAIL, SmartAuditStatus.FAIL, "Locator no encontrado");
 *   recorder.record(SmartAuditEventType.CACHE_APPLIED, "El repair cache funcionó");
 *   recorder.finishSuccess("css=#btn-login", "CACHE");
 *   SmartAuditReport report = recorder.getReport();
 */
public class SmartAuditRecorder {

    private final SmartAuditReport report;

    public SmartAuditRecorder(String appName,
                              String mode,
                              String originalLocator,
                              String pageUrl) {
        this.report = new SmartAuditReport();
        report.setAppName(appName);
        report.setMode(mode);
        report.setOriginalLocator(originalLocator);
        report.setPageUrl(pageUrl);
        report.setStartTime(System.currentTimeMillis());
    }

    /** Registro simple (status PASS por defecto). */
    public void record(SmartAuditEventType type, String message) {
        record(type, "runtime", SmartAuditStatus.PASS, message, null, null);
    }

    /** Registro con status explícito. */
    public void record(SmartAuditEventType type, SmartAuditStatus status, String message) {
        record(type, "runtime", status, message, null, null);
    }

    /** Registro completo con phase, score y locator resuelto. */
    public void record(SmartAuditEventType type,
                       String phase,
                       SmartAuditStatus status,
                       String message,
                       Integer score,
                       String resolvedLocator) {

        SmartAuditEvent event = new SmartAuditEvent(type, phase, status, message)
                .locator(resolvedLocator)
                .score(score)
                .mode(report.getMode())
                .pageUrl(report.getPageUrl());

        report.addEvent(event);
    }

    public void finishSuccess(String finalLocator, String source) {
        report.finishSuccess(finalLocator, source);
    }

    public void finishFailure() {
        report.finishFailure();
    }

    public SmartAuditReport getReport() {
        return report;
    }
}
