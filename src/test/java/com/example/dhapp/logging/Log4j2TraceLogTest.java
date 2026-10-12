package com.example.dhapp.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.dhapp.controller.HealthCheckController;

/**
 * Log4j2 および tracelog 時間単位ローテーション設定の検証テスト。
 *
 * stale file handle エラー調査のため、Log4j2 (2.25.3) が SLF4J 実装として機能し、
 * TRACE_FILE アッペンダーが 1 時間周期の TimeBasedTriggeringPolicy を持って
 * tracelog をローテーションするように構成されていることを確認する。
 */
class Log4j2TraceLogTest {

    @Test
    void log4j2IsActiveLoggerAndConfiguresHourlyTraceLog() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        assertNotNull(context, "Log4j2 LoggerContext must be available");

        Configuration config = context.getConfiguration();
        assertNotNull(config, "Log4j2 Configuration must be available");

        Appender traceAppender = config.getAppender("TRACE_FILE");
        assertNotNull(traceAppender, "TRACE_FILE appender must be configured in log4j2.xml");
        assertTrue(traceAppender instanceof RollingFileAppender,
                "TRACE_FILE must be a RollingFileAppender");

        RollingFileAppender rollingAppender = (RollingFileAppender) traceAppender;
        assertTrue(rollingAppender.getFilePattern().endsWith("tracelog.%d{yyyy-MM-dd-HH}")
                        || rollingAppender.getFilePattern().contains("tracelog.%d{yyyy-MM-dd-HH}"),
                "filePattern must rotate with hourly pattern %d{yyyy-MM-dd-HH}");

        org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy policy =
                rollingAppender.getTriggeringPolicy();
        assertNotNull(policy, "TriggeringPolicy must not be null");

        TimeBasedTriggeringPolicy timePolicy = null;
        if (policy instanceof TimeBasedTriggeringPolicy) {
            timePolicy = (TimeBasedTriggeringPolicy) policy;
        } else if (policy instanceof org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy) {
            for (org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy p :
                    ((org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy) policy).getTriggeringPolicies()) {
                if (p instanceof TimeBasedTriggeringPolicy) {
                    timePolicy = (TimeBasedTriggeringPolicy) p;
                    break;
                }
            }
        }
        assertNotNull(timePolicy, "TriggeringPolicy must contain TimeBasedTriggeringPolicy");
        assertEquals(1, timePolicy.getInterval(),
                "TimeBasedTriggeringPolicy interval must be 1 (hourly)");

        // HealthCheckController のロガーからログ出力が正常に行えること
        Logger slf4jLogger = LoggerFactory.getLogger(HealthCheckController.class);
        slf4jLogger.info("Log4j2TraceLogTest: verifying tracelog routing for health check");
    }
}
