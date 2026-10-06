package com.example.dhapp.dto;

import java.util.Map;

/**
 * リダイレクト環境認識確認 API（{@code GET /api/redirect/info}）のレスポンス。
 *
 * <p>AP サーバ／Spring が現在の着信リクエストをどのように認識しているか、
 * 及びその状態で相対リダイレクトを指示した場合にどのような Location ヘッダが生成されるかを可視化する。</p>
 */
public class RedirectInfoResponse {

    private String requestId;
    private String timestamp;
    private String requestScheme;
    private boolean isSecure;
    private String serverName;
    private int serverPort;
    private String contextPath;
    private String requestUri;
    private String requestUrl;
    private Map<String, String> forwardedHeaders;
    private Map<String, String> allHeaders;
    private String predictedSendRedirectUrl;
    private String predictedUriComponentsBuilderUrl;
    private boolean forwardedHeaderFilterActive;
    private String undertowProxyAddressForwardingHint;

    public RedirectInfoResponse() {
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

    public String getRequestScheme() {
        return requestScheme;
    }

    public void setRequestScheme(String requestScheme) {
        this.requestScheme = requestScheme;
    }

    public boolean isSecure() {
        return isSecure;
    }

    public void setSecure(boolean isSecure) {
        this.isSecure = isSecure;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public Map<String, String> getForwardedHeaders() {
        return forwardedHeaders;
    }

    public void setForwardedHeaders(Map<String, String> forwardedHeaders) {
        this.forwardedHeaders = forwardedHeaders;
    }

    public Map<String, String> getAllHeaders() {
        return allHeaders;
    }

    public void setAllHeaders(Map<String, String> allHeaders) {
        this.allHeaders = allHeaders;
    }

    public String getPredictedSendRedirectUrl() {
        return predictedSendRedirectUrl;
    }

    public void setPredictedSendRedirectUrl(String predictedSendRedirectUrl) {
        this.predictedSendRedirectUrl = predictedSendRedirectUrl;
    }

    public String getPredictedUriComponentsBuilderUrl() {
        return predictedUriComponentsBuilderUrl;
    }

    public void setPredictedUriComponentsBuilderUrl(String predictedUriComponentsBuilderUrl) {
        this.predictedUriComponentsBuilderUrl = predictedUriComponentsBuilderUrl;
    }

    public boolean isForwardedHeaderFilterActive() {
        return forwardedHeaderFilterActive;
    }

    public void setForwardedHeaderFilterActive(boolean forwardedHeaderFilterActive) {
        this.forwardedHeaderFilterActive = forwardedHeaderFilterActive;
    }

    public String getUndertowProxyAddressForwardingHint() {
        return undertowProxyAddressForwardingHint;
    }

    public void setUndertowProxyAddressForwardingHint(String undertowProxyAddressForwardingHint) {
        this.undertowProxyAddressForwardingHint = undertowProxyAddressForwardingHint;
    }
}
