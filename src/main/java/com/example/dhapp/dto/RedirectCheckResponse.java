package com.example.dhapp.dto;

import java.util.Map;

/**
 * リダイレクト Location ヘッダ検証 API（{@code GET /api/redirect/check}）の単一検証結果レスポンス。
 */
public class RedirectCheckResponse {

    private String requestId;
    private String timestamp;
    private String target;
    private String url;
    private String redirectMode;
    private String status;
    private String verdict;
    private String summary;
    private boolean isHttps;
    private Integer httpStatus;
    private String locationHeader;
    private String locationScheme;
    private String locationHost;
    private Integer locationPort;
    private String locationPath;
    private Map<String, String> requestHeadersSent;
    private Map<String, String> responseHeaders;
    private long elapsedMs;
    private String troubleshooting;
    private String report;

    public RedirectCheckResponse() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRedirectMode() {
        return redirectMode;
    }

    public void setRedirectMode(String redirectMode) {
        this.redirectMode = redirectMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isHttps() {
        return isHttps;
    }

    public void setHttps(boolean isHttps) {
        this.isHttps = isHttps;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getLocationHeader() {
        return locationHeader;
    }

    public void setLocationHeader(String locationHeader) {
        this.locationHeader = locationHeader;
    }

    public String getLocationScheme() {
        return locationScheme;
    }

    public void setLocationScheme(String locationScheme) {
        this.locationScheme = locationScheme;
    }

    public String getLocationHost() {
        return locationHost;
    }

    public void setLocationHost(String locationHost) {
        this.locationHost = locationHost;
    }

    public Integer getLocationPort() {
        return locationPort;
    }

    public void setLocationPort(Integer locationPort) {
        this.locationPort = locationPort;
    }

    public String getLocationPath() {
        return locationPath;
    }

    public void setLocationPath(String locationPath) {
        this.locationPath = locationPath;
    }

    public Map<String, String> getRequestHeadersSent() {
        return requestHeadersSent;
    }

    public void setRequestHeadersSent(Map<String, String> requestHeadersSent) {
        this.requestHeadersSent = requestHeadersSent;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getTroubleshooting() {
        return troubleshooting;
    }

    public void setTroubleshooting(String troubleshooting) {
        this.troubleshooting = troubleshooting;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }
}
