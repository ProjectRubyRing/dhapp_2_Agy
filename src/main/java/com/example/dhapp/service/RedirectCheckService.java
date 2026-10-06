package com.example.dhapp.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.dhapp.dto.RedirectCheckResponse;
import com.example.dhapp.dto.RedirectComparisonResponse;

/**
 * ALB 経由リダイレクト時の Location ヘッダが {@code https://} に修正されていることを検証するサービス。
 *
 * <h2>背景と問題</h2>
 * <p>クライアント 〜 ALB 間は HTTPS（ポート 443）で暗号化通信を行い、
 * ALB 〜 JBoss EAP（Undertow）間は平文 HTTP（ポート 8080/8180）で通信している環境において、
 * アプリケーションが相対リダイレクト（例: {@code sendRedirect("/api/...")}）を発行すると、
 * コンテナが着信ソケットの平文 HTTP スキームに基づき絶対 URL を組み立てるため、
 * {@code Location: http://...} がレスポンスヘッダに設定されてしまう問題が発生する。</p>
 *
 * <h2>検証機能</h2>
 * <ul>
 *   <li>{@code target=alb}: 偽装 ALB コンテナ（HTTPS）経由でリダイレクト指示 API を叩き、戻ってきた Location ヘッダを検証</li>
 *   <li>{@code target=simulated}: ALB が付与する {@code X-Forwarded-Proto: https} などのヘッダを付加してローカル検証</li>
 *   <li>{@code target=plain}: ヘッダ無しの平文アクセスを行い、未修正時の baseline（{@code http://...}）を再現</li>
 *   <li>{@code target=comparison}: 上記の {@code simulated} と {@code plain} を同時実行し、修正の効果を並べて比較・証明</li>
 *   <li>{@code target=custom}: 任意の指定 URL を検証</li>
 * </ul>
 */
@Service
public class RedirectCheckService {

    private static final Logger log = LoggerFactory.getLogger(RedirectCheckService.class);

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");

    public static final String TARGET_ALB = "alb";
    public static final String TARGET_SIMULATED = "simulated";
    public static final String TARGET_PLAIN = "plain";
    public static final String TARGET_CUSTOM = "custom";
    public static final String TARGET_COMPARISON = "comparison";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ERROR = "ERROR";

    public static final String VERDICT_LOCATION_HTTPS_OK = "LOCATION_HTTPS_OK";
    public static final String VERDICT_LOCATION_HTTP_FAILED = "LOCATION_HTTP_FAILED";
    public static final String VERDICT_LOCATION_RELATIVE = "LOCATION_RELATIVE";
    public static final String VERDICT_NO_LOCATION_HEADER = "NO_LOCATION_HEADER";
    public static final String VERDICT_NOT_A_REDIRECT = "NOT_A_REDIRECT";
    public static final String VERDICT_CONNECTION_ERROR = "CONNECTION_ERROR";

    private final String albUrl;
    private final String localUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public RedirectCheckService(
            @Value("${app.redirect.alb-url:https://alb/iwinmichl/api/redirect/trigger}") String albUrl,
            @Value("${app.redirect.local-url:http://localhost:8080/iwinmichl/api/redirect/trigger}") String localUrl,
            @Value("${app.redirect.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.redirect.read-timeout-ms:5000}") int readTimeoutMs) {
        this.albUrl = albUrl;
        this.localUrl = localUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getAlbUrl() {
        return albUrl;
    }

    public String getLocalUrl() {
        return localUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * 単一ターゲットに対するリダイレクト Location ヘッダ検証を実行する。
     *
     * @param target   検証対象（alb / simulated / plain / custom）
     * @param url      カスタム URL（target=custom 時）
     * @param mode     リダイレクト発行方式（sendRedirect / redirectView / responseEntity）
     * @param insecure HTTPS 時に証明書検証をスキップするか（モック環境用）
     * @param reqId    リクエスト ID
     * @return 検証結果レスポンス
     */
    public RedirectCheckResponse check(String target, String url, String mode, boolean insecure, String reqId) {
        long startedAt = System.currentTimeMillis();
        String requestId = StringUtils.hasText(reqId) ? reqId : UUID.randomUUID().toString();
        String resolvedTarget = normalizeTarget(target);
        String resolvedMode = StringUtils.hasText(mode) ? mode : "sendRedirect";

        String targetUrl = resolveTargetUrl(resolvedTarget, url, resolvedMode);
        Map<String, String> requestHeaders = buildRequestHeaders(resolvedTarget);

        log.info("Redirect check starting. requestId={}, target={}, url={}, mode={}, insecure={}",
                requestId, resolvedTarget, targetUrl, resolvedMode, insecure);

        RedirectCheckResponse response = new RedirectCheckResponse();
        response.setRequestId(requestId);
        response.setTimestamp(OffsetDateTime.now().format(TIMESTAMP));
        response.setTarget(resolvedTarget);
        response.setUrl(targetUrl);
        response.setRedirectMode(resolvedMode);
        response.setRequestHeadersSent(requestHeaders);

        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(targetUrl);
            connection = openConnection(uri, insecure);
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false); // ★重要: 302 を自動追跡せず捕捉する

            for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            int httpStatus = connection.getResponseCode();
            response.setHttpStatus(httpStatus);

            Map<String, String> responseHeaders = extractResponseHeaders(connection);
            response.setResponseHeaders(responseHeaders);

            String locationHeader = connection.getHeaderField("Location");
            response.setLocationHeader(locationHeader);

            analyzeResult(response, httpStatus, locationHeader);

        } catch (IOException e) {
            log.error("Redirect check connection failed. requestId={}, url={}", requestId, targetUrl, e);
            response.setStatus(STATUS_ERROR);
            response.setVerdict(VERDICT_CONNECTION_ERROR);
            response.setSummary("接続エラー: " + e.getMessage());
            response.setTroubleshooting("対象 URL (" + targetUrl + ") にアクセスできませんでした。"
                    + " ALB コンテナが起動しているか、またはネットワーク疎通を確認してください。");
        } catch (Exception e) {
            log.error("Redirect check unexpected error. requestId={}, url={}", requestId, targetUrl, e);
            response.setStatus(STATUS_ERROR);
            response.setVerdict(VERDICT_CONNECTION_ERROR);
            response.setSummary("予期しないエラー: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        response.setElapsedMs(System.currentTimeMillis() - startedAt);
        String report = buildTextReport(response);
        response.setReport(report);
        ConsoleWriter.println(report);

        log.info("Redirect check finished. requestId={}, target={}, status={}, verdict={}, location={}, isHttps={}, elapsedMs={}",
                requestId, response.getTarget(), response.getStatus(), response.getVerdict(),
                response.getLocationHeader(), response.isHttps(), response.getElapsedMs());

        return response;
    }

    /**
     * ALB 偽装ヘッダあり（{@code simulated}）と平文（{@code plain}）の両方を実行し、
     * Location ヘッダが https に修正されたかを比較検証する。
     */
    public RedirectComparisonResponse checkComparison(String mode, String reqId) {
        String requestId = StringUtils.hasText(reqId) ? reqId : UUID.randomUUID().toString();
        log.info("Redirect comparison check starting. requestId={}, mode={}", requestId, mode);

        RedirectCheckResponse withProto = check(TARGET_SIMULATED, null, mode, false, requestId + "-with-proto");
        RedirectCheckResponse withoutProto = check(TARGET_PLAIN, null, mode, false, requestId + "-without-proto");

        RedirectComparisonResponse comp = new RedirectComparisonResponse();
        comp.setRequestId(requestId);
        comp.setTimestamp(OffsetDateTime.now().format(TIMESTAMP));
        comp.setWithForwardedProto(withProto);
        comp.setWithoutForwardedProto(withoutProto);

        boolean verified = withProto.isHttps() && !withoutProto.isHttps();
        comp.setVerifiedFix(verified);

        if (verified) {
            comp.setStatus(STATUS_SUCCESS);
            comp.setSummary("【検証成功】X-Forwarded-Proto: https 付与時は Location ヘッダが正常に https:// に修正され、"
                    + "ヘッダ未付与（平文アクセス）時は http:// となることを確認しました。修正が意図通り動作しています。");
        } else if (withProto.isHttps() && withoutProto.isHttps()) {
            comp.setStatus(STATUS_SUCCESS);
            comp.setSummary("両方の呼び出しで Location ヘッダが https:// となりました。");
        } else {
            comp.setStatus(STATUS_FAILED);
            comp.setSummary("【検証失敗】X-Forwarded-Proto: https を付与したにもかかわらず、Location ヘッダが https になりませんでした。"
                    + " (Location: " + withProto.getLocationHeader() + ")");
        }

        String report = buildComparisonReport(comp);
        comp.setReport(report);
        ConsoleWriter.println(report);

        log.info("Redirect comparison check finished. requestId={}, status={}, verifiedFix={}",
                requestId, comp.getStatus(), comp.isVerifiedFix());
        return comp;
    }

    private String normalizeTarget(String target) {
        if (!StringUtils.hasText(target)) {
            return TARGET_ALB;
        }
        String lower = target.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case TARGET_ALB, "https", "alb-container" -> TARGET_ALB;
            case TARGET_SIMULATED, "sim", "mock-alb" -> TARGET_SIMULATED;
            case TARGET_PLAIN, "direct", "http" -> TARGET_PLAIN;
            case TARGET_CUSTOM, "custom-url" -> TARGET_CUSTOM;
            default -> TARGET_ALB;
        };
    }

    private String resolveTargetUrl(String target, String customUrl, String mode) {
        String base;
        if (TARGET_CUSTOM.equalsIgnoreCase(target) && StringUtils.hasText(customUrl)) {
            base = customUrl.trim();
        } else if (TARGET_ALB.equalsIgnoreCase(target)) {
            base = StringUtils.hasText(customUrl) ? customUrl.trim() : albUrl;
        } else {
            base = StringUtils.hasText(customUrl) ? customUrl.trim() : localUrl;
        }

        if (StringUtils.hasText(mode) && !base.contains("mode=")) {
            String separator = base.contains("?") ? "&" : "?";
            return base + separator + "mode=" + mode;
        }
        return base;
    }

    private Map<String, String> buildRequestHeaders(String target) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "*/*");
        headers.put("User-Agent", "dhapp-redirect-verifier/1.0");

        if (TARGET_SIMULATED.equalsIgnoreCase(target)) {
            // ALB から JBoss EAP へ送られる標準的なヘッダを偽装付与
            headers.put("X-Forwarded-Proto", "https");
            headers.put("X-Forwarded-Port", "443");
            headers.put("X-Forwarded-Host", "alb");
            headers.put("Host", "alb");
        }
        return headers;
    }

    private HttpURLConnection openConnection(URI uri, boolean insecure) throws IOException, GeneralSecurityException {
        URL urlObj = uri.toURL();
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            HttpsURLConnection httpsConn = (HttpsURLConnection) urlObj.openConnection();
            if (insecure) {
                // テスト用の自己署名証明書でも検証できるよう、全信頼 SSLContext を適用
                SSLContext sslContext = createInsecureSslContext();
                httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }
            return httpsConn;
        }
        return (HttpURLConnection) urlObj.openConnection();
    }

    private static SSLContext createInsecureSslContext() throws GeneralSecurityException {
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());
        return sc;
    }

    private Map<String, String> extractResponseHeaders(HttpURLConnection connection) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                headers.put(entry.getKey(), String.join(", ", entry.getValue()));
            }
        }
        return headers;
    }

    private void analyzeResult(RedirectCheckResponse response, int httpStatus, String locationHeader) {
        if (httpStatus < 300 || httpStatus >= 400) {
            response.setStatus(STATUS_FAILED);
            response.setVerdict(VERDICT_NOT_A_REDIRECT);
            response.setSummary("HTTP ステータスがリダイレクト (3xx) ではありません: " + httpStatus);
            response.setTroubleshooting("リダイレクト指示エンドポイント (/api/redirect/trigger) が 3xx を返しているか確認してください。");
            return;
        }

        if (!StringUtils.hasText(locationHeader)) {
            response.setStatus(STATUS_FAILED);
            response.setVerdict(VERDICT_NO_LOCATION_HEADER);
            response.setSummary("HTTP 3xx レスポンスに Location ヘッダが存在しません。");
            response.setTroubleshooting("レスポンスに Location ヘッダが付与されているか確認してください。");
            return;
        }

        String location = locationHeader.trim();
        parseLocationUri(response, location);

        if (location.toLowerCase(Locale.ROOT).startsWith("https://")) {
            response.setHttps(true);
            response.setStatus(STATUS_SUCCESS);
            response.setVerdict(VERDICT_LOCATION_HTTPS_OK);
            response.setSummary("【検証成功】Location ヘッダが正常に https (https://...) として設定されていることを確認しました。"
                    + " ALB (HTTPS) 〜 JBoss EAP (HTTP) 間におけるリダイレクト URL の HTTPS 化が正常に機能しています。");
        } else if (location.toLowerCase(Locale.ROOT).startsWith("http://")) {
            response.setHttps(false);
            response.setStatus(STATUS_FAILED);
            response.setVerdict(VERDICT_LOCATION_HTTP_FAILED);
            response.setSummary("【問題検知】Location ヘッダが平文 http (http://...) として設定されています。"
                    + " JBoss EAP / Undertow が ALB の X-Forwarded-Proto: https を認識していないため、内部の平文 HTTP スキームで Location を組み立てています。");
            response.setTroubleshooting("修正方法:\n"
                    + "1. Spring 側: ForwardedHeaderFilter を登録する (RedirectConfig / server.forward-headers-strategy=framework)\n"
                    + "2. JBoss EAP 側: Undertow の http-listener で proxy-address-forwarding=true を設定する (wildfly/enable-proxy-address-forwarding.cli)");
        } else {
            response.setHttps(false);
            response.setStatus(STATUS_SUCCESS);
            response.setVerdict(VERDICT_LOCATION_RELATIVE);
            response.setSummary("Location ヘッダに相対パスが設定されています: " + location);
        }
    }

    private void parseLocationUri(RedirectCheckResponse response, String location) {
        try {
            URI uri = URI.create(location);
            response.setLocationScheme(uri.getScheme());
            response.setLocationHost(uri.getHost());
            response.setLocationPort(uri.getPort());
            response.setLocationPath(uri.getPath());
        } catch (Exception e) {
            response.setLocationScheme("unknown");
        }
    }

    /** テキスト形式レポートを生成する。 */
    public String buildTextReport(RedirectCheckResponse response) {
        String nl = System.lineSeparator();
        String line = "================================================================================";
        String subLine = "--------------------------------------------------------------------------------";

        StringBuilder sb = new StringBuilder(2048);
        sb.append(nl).append(line).append(nl);
        sb.append("ALB 経由リダイレクト Location ヘッダ HTTPS 修正検証レポート").append(nl);
        sb.append("requestId=").append(response.getRequestId())
                .append(", timestamp=").append(response.getTimestamp()).append(nl);
        sb.append(line).append(nl);

        sb.append("■ 総合判定").append(nl);
        sb.append("  ステータス : ").append(response.getStatus()).append(nl);
        sb.append("  判定コード : ").append(response.getVerdict()).append(nl);
        sb.append("  HTTPS 化   : ").append(response.isHttps() ? "OK (https://)" : "NG (非 https)").append(nl);
        sb.append("  要約       : ").append(response.getSummary()).append(nl);
        sb.append(nl);

        sb.append("■ 検証詳細").append(nl);
        sb.append("  検証対象 (target) : ").append(response.getTarget()).append(nl);
        sb.append("  接続先 URL        : ").append(response.getUrl()).append(nl);
        sb.append("  リダイレクト方式  : ").append(response.getRedirectMode()).append(nl);
        sb.append("  HTTP ステータス   : ").append(response.getHttpStatus()).append(nl);
        sb.append("  Location ヘッダ   : ").append(response.getLocationHeader() != null ? response.getLocationHeader() : "(なし)").append(nl);
        if (response.getLocationScheme() != null) {
            sb.append("  Location スキーム : ").append(response.getLocationScheme()).append(nl);
            sb.append("  Location ホスト   : ").append(response.getLocationHost()).append(nl);
            sb.append("  Location ポート   : ").append(response.getLocationPort() != null ? response.getLocationPort() : "(既定)").append(nl);
        }
        sb.append("  所要時間          : ").append(response.getElapsedMs()).append(" ms").append(nl);
        sb.append(nl);

        if (response.getRequestHeadersSent() != null && !response.getRequestHeadersSent().isEmpty()) {
            sb.append("■ 送信リクエストヘッダ").append(nl);
            response.getRequestHeadersSent().forEach((k, v) ->
                    sb.append("  ").append(k).append(": ").append(v).append(nl));
            sb.append(nl);
        }

        if (response.getResponseHeaders() != null && !response.getResponseHeaders().isEmpty()) {
            sb.append("■ 受信レスポンスヘッダ (抜粋)").append(nl);
            response.getResponseHeaders().forEach((k, v) -> {
                if (k.toLowerCase(Locale.ROOT).contains("location")
                        || k.toLowerCase(Locale.ROOT).contains("x-redirect")
                        || k.toLowerCase(Locale.ROOT).contains("server")) {
                    sb.append("  ").append(k).append(": ").append(v).append(nl);
                }
            });
            sb.append(nl);
        }

        if (StringUtils.hasText(response.getTroubleshooting())) {
            sb.append("■ トラブルシューティング").append(nl);
            sb.append(response.getTroubleshooting()).append(nl);
            sb.append(nl);
        }

        sb.append(line).append(nl);
        return sb.toString();
    }

    /** 比較検証用テキストレポートを生成する。 */
    public String buildComparisonReport(RedirectComparisonResponse comp) {
        String nl = System.lineSeparator();
        String line = "================================================================================";

        StringBuilder sb = new StringBuilder(2048);
        sb.append(nl).append(line).append(nl);
        sb.append("ALB 経由リダイレクト Location ヘッダ HTTPS 修正 比較検証レポート").append(nl);
        sb.append("requestId=").append(comp.getRequestId())
                .append(", timestamp=").append(comp.getTimestamp()).append(nl);
        sb.append(line).append(nl);

        sb.append("■ 比較判定").append(nl);
        sb.append("  ステータス   : ").append(comp.getStatus()).append(nl);
        sb.append("  修正効果証明 : ").append(comp.isVerifiedFix() ? "PROVED (成功)" : "UNCONFIRMED").append(nl);
        sb.append("  総括         : ").append(comp.getSummary()).append(nl);
        sb.append(nl);

        RedirectCheckResponse with = comp.getWithForwardedProto();
        RedirectCheckResponse without = comp.getWithoutForwardedProto();

        sb.append("■ 比較一覧").append(nl);
        sb.append(String.format("  %-32s | %-20s | %s%n", "パターン", "Location スキーム", "Location ヘッダ"));
        sb.append("  ---------------------------------+----------------------+----------------------------------------").append(nl);
        sb.append(String.format("  %-32s | %-20s | %s%n",
                "[1] X-Forwarded-Proto: https あり",
                with.getLocationScheme() != null ? with.getLocationScheme() : "none",
                with.getLocationHeader() != null ? with.getLocationHeader() : "(なし)"));
        sb.append(String.format("  %-32s | %-20s | %s%n",
                "[2] 平文 HTTP (ヘッダなし)",
                without.getLocationScheme() != null ? without.getLocationScheme() : "none",
                without.getLocationHeader() != null ? without.getLocationHeader() : "(なし)"));
        sb.append(nl);

        sb.append(line).append(nl);
        return sb.toString();
    }
}
