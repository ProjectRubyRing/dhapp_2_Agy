package com.example.dhapp.dto;

/**
 * リダイレクト Location ヘッダ検証 API（{@code GET /api/redirect/check?target=comparison}）の
 * 比較検証結果レスポンス。
 *
 * <p>ALB 偽装ヘッダ（{@code X-Forwarded-Proto: https}）を付与した場合と、
 * 平文 HTTP（ヘッダなし）の場合の両方を実行し、並べて比較する。</p>
 */
public class RedirectComparisonResponse {

    private String requestId;
    private String timestamp;
    private String status;
    private String summary;
    private boolean verifiedFix;
    private RedirectCheckResponse withForwardedProto;
    private RedirectCheckResponse withoutForwardedProto;
    private String report;

    public RedirectComparisonResponse() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isVerifiedFix() {
        return verifiedFix;
    }

    public void setVerifiedFix(boolean verifiedFix) {
        this.verifiedFix = verifiedFix;
    }

    public RedirectCheckResponse getWithForwardedProto() {
        return withForwardedProto;
    }

    public void setWithForwardedProto(RedirectCheckResponse withForwardedProto) {
        this.withForwardedProto = withForwardedProto;
    }

    public RedirectCheckResponse getWithoutForwardedProto() {
        return withoutForwardedProto;
    }

    public void setWithoutForwardedProto(RedirectCheckResponse withoutForwardedProto) {
        this.withoutForwardedProto = withoutForwardedProto;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }
}
