package com.example.dhapp.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

import com.example.dhapp.dto.RedirectCheckResponse;
import com.example.dhapp.dto.RedirectComparisonResponse;
import com.example.dhapp.dto.RedirectInfoResponse;
import com.example.dhapp.dto.RedirectTargetResponse;
import com.example.dhapp.service.RedirectCheckService;

/**
 * ALB 〜 JBoss EAP 間リダイレクト Location ヘッダ HTTPS 修正確認 API。
 *
 * <h2>提供エンドポイント</h2>
 * <ul>
 *   <li>{@code GET|POST /api/redirect/trigger} … リダイレクト指示を発行する API（302 等を返し、Location ヘッダを観察可能）</li>
 *   <li>{@code GET|POST /api/redirect/target}  … リダイレクト後の着地先 API（到達時のリクエスト属性を返却）</li>
 *   <li>{@code GET|POST /api/redirect/check}   … ALB 偽装コンテナやローカルへリクエストし、Location ヘッダが https かを自動検証する API</li>
 *   <li>{@code GET      /api/redirect/info}    … 現在のリクエスト・スキーム認識状況および生成されるリダイレクト URL を可視化する API</li>
 * </ul>
 *
 * <p>詳細は {@code REDIRECT_HTTPS_API.md} を参照。</p>
 */
@RestController
@RequestMapping("/api/redirect")
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");

    private final RedirectCheckService redirectCheckService;

    public RedirectController(RedirectCheckService redirectCheckService) {
        this.redirectCheckService = redirectCheckService;
    }

    /**
     * リダイレクト指示を発行するエンドポイント。
     *
     * <pre>
     * # ALB 経由でリダイレクト指示を受け取り、Location ヘッダを確認する例:
     * curl -i https://alb/iwinmichl/api/redirect/trigger
     *
     * # X-Forwarded-Proto ヘッダを付与して直接 JBoss EAP に確認する例:
     * curl -i -H "X-Forwarded-Proto: https" http://localhost:8080/iwinmichl/api/redirect/trigger
     * </pre>
     *
     * @param target リダイレクト先パス（省略時は {@code /api/redirect/target}）
     * @param mode   方式（{@code sendRedirect} / {@code redirectView} / {@code responseEntity}）
     * @param status HTTP ステータスコード（既定: 302）
     */
    @GetMapping("/trigger")
    public Object triggerGet(
            @RequestParam(name = "target", required = false) String target,
            @RequestParam(name = "mode", defaultValue = "sendRedirect") String mode,
            @RequestParam(name = "status", defaultValue = "302") int status,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        return executeRedirect(target, mode, status, request, response);
    }

    @PostMapping("/trigger")
    public Object triggerPost(
            @RequestParam(name = "target", required = false) String target,
            @RequestParam(name = "mode", defaultValue = "sendRedirect") String mode,
            @RequestParam(name = "status", defaultValue = "302") int status,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        return executeRedirect(target, mode, status, request, response);
    }

    private Object executeRedirect(String target, String mode, int status,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
        String resolvedTarget;
        if (!StringUtils.hasText(target)) {
            resolvedTarget = contextPath + "/api/redirect/target";
        } else if (target.startsWith("/") && StringUtils.hasText(contextPath) && !target.startsWith(contextPath + "/")) {
            resolvedTarget = contextPath + target;
        } else {
            resolvedTarget = target;
        }

        // デバッグ用ヘッダを付与（リダイレクトレスポンス自体にも載る）
        response.setHeader("X-Redirect-Request-Scheme", request.getScheme());
        response.setHeader("X-Redirect-Request-Secure", String.valueOf(request.isSecure()));
        response.setHeader("X-Redirect-Request-Port", String.valueOf(request.getServerPort()));
        response.setHeader("X-Redirect-Mode", mode);
        response.setHeader("X-Redirect-Target", resolvedTarget);

        log.info("Redirect instruction requested. mode={}, target={}, resolvedTarget={}, "
                        + "requestScheme={}, isSecure={}, serverPort={}, host={}",
                mode, target, resolvedTarget, request.getScheme(), request.isSecure(),
                request.getServerPort(), request.getHeader("Host"));

        if ("redirectView".equalsIgnoreCase(mode)) {
            RedirectView rv = new RedirectView(resolvedTarget, false);
            rv.setStatusCode(HttpStatus.valueOf(status));
            return new ModelAndView(rv);
        } else if ("responseEntity".equalsIgnoreCase(mode)) {
            URI locationUri;
            if (resolvedTarget.startsWith("http://") || resolvedTarget.startsWith("https://")) {
                locationUri = URI.create(resolvedTarget);
            } else {
                // 相対パスの場合は ServletUriComponentsBuilder を用いて絶対 URL を生成
                locationUri = ServletUriComponentsBuilder.fromContextPath(request)
                        .path(resolvedTarget.startsWith(contextPath) ? resolvedTarget.substring(contextPath.length()) : resolvedTarget)
                        .build().toUri();
            }
            return ResponseEntity.status(status).location(locationUri).build();
        } else {
            // 既定: sendRedirect
            if (status == 302) {
                response.sendRedirect(resolvedTarget);
            } else {
                response.setStatus(status);
                response.setHeader(HttpHeaders.LOCATION, resolvedTarget);
            }
            return null;
        }
    }

    /**
     * リダイレクト着地先エンドポイント。
     */
    @GetMapping(value = "/target", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RedirectTargetResponse> targetGet(HttpServletRequest request) {
        return buildTargetResponse(request);
    }

    @PostMapping(value = "/target", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RedirectTargetResponse> targetPost(HttpServletRequest request) {
        return buildTargetResponse(request);
    }

    private ResponseEntity<RedirectTargetResponse> buildTargetResponse(HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        RedirectTargetResponse resp = new RedirectTargetResponse();
        resp.setStatus("SUCCESS");
        resp.setMessage("Redirect destination reached successfully");
        resp.setRequestId(requestId);
        resp.setTimestamp(OffsetDateTime.now().format(TIMESTAMP));
        resp.setScheme(request.getScheme());
        resp.setSecure(request.isSecure());
        resp.setServerName(request.getServerName());
        resp.setServerPort(request.getServerPort());
        resp.setRequestUri(request.getRequestURI());
        resp.setClientIp(request.getRemoteAddr());
        resp.setHeaders(extractHeaders(request));

        log.info("Redirect target reached. requestId={}, scheme={}, isSecure={}, serverPort={}, clientIp={}",
                requestId, request.getScheme(), request.isSecure(), request.getServerPort(), request.getRemoteAddr());
        return ResponseEntity.ok(resp);
    }

    /**
     * リダイレクト Location ヘッダ検証 API。
     *
     * <pre>
     * # ALB 偽装コンテナ経由で検証（既定）
     * curl -s http://localhost:8080/iwinmichl/api/redirect/check | jq .
     *
     * # テキスト形式レポートで確認
     * curl -s "http://localhost:8080/iwinmichl/api/redirect/check?format=text"
     *
     * # ヘッダ偽装と平文アクセスの比較検証（修正効果を一発証明）
     * curl -s "http://localhost:8080/iwinmichl/api/redirect/check?target=comparison" | jq .
     * </pre>
     *
     * @param target   {@code alb}（既定） / {@code simulated} / {@code plain} / {@code comparison} / {@code custom}
     * @param url      カスタム URL
     * @param mode     {@code sendRedirect}（既定） / {@code redirectView} / {@code responseEntity}
     * @param insecure 自己署名証明書の検証をスキップするか（既定: true）
     * @param format   {@code text} でテキストレポート返却
     */
    @GetMapping(value = "/check", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> checkGet(
            @RequestParam(name = "target", defaultValue = "alb") String target,
            @RequestParam(name = "url", required = false) String url,
            @RequestParam(name = "mode", defaultValue = "sendRedirect") String mode,
            @RequestParam(name = "insecure", defaultValue = "true") boolean insecure,
            @RequestParam(name = "format", required = false) String format) {
        return executeCheck(target, url, mode, insecure, format);
    }

    @PostMapping(value = "/check", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> checkPost(
            @RequestParam(name = "target", defaultValue = "alb") String target,
            @RequestParam(name = "url", required = false) String url,
            @RequestParam(name = "mode", defaultValue = "sendRedirect") String mode,
            @RequestParam(name = "insecure", defaultValue = "true") boolean insecure,
            @RequestParam(name = "format", required = false) String format) {
        return executeCheck(target, url, mode, insecure, format);
    }

    private ResponseEntity<?> executeCheck(String target, String url, String mode, boolean insecure, String format) {
        String requestId = UUID.randomUUID().toString();
        boolean isText = "text".equalsIgnoreCase(format);

        if (RedirectCheckService.TARGET_COMPARISON.equalsIgnoreCase(target)) {
            RedirectComparisonResponse comp = redirectCheckService.checkComparison(mode, requestId);
            if (isText) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(comp.getReport());
            }
            return ResponseEntity.ok(comp);
        }

        RedirectCheckResponse result = redirectCheckService.check(target, url, mode, insecure, requestId);
        if (isText) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(result.getReport());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 現在のリクエスト認識状況および生成されるリダイレクト URL のプレビュー API。
     */
    @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RedirectInfoResponse> info(HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        RedirectInfoResponse resp = new RedirectInfoResponse();
        resp.setRequestId(requestId);
        resp.setTimestamp(OffsetDateTime.now().format(TIMESTAMP));
        resp.setRequestScheme(request.getScheme());
        resp.setSecure(request.isSecure());
        resp.setServerName(request.getServerName());
        resp.setServerPort(request.getServerPort());
        resp.setContextPath(request.getContextPath());
        resp.setRequestUri(request.getRequestURI());
        resp.setRequestUrl(request.getRequestURL() != null ? request.getRequestURL().toString() : "");

        Map<String, String> forwarded = new LinkedHashMap<>();
        forwarded.put("X-Forwarded-Proto", request.getHeader("X-Forwarded-Proto"));
        forwarded.put("X-Forwarded-Port", request.getHeader("X-Forwarded-Port"));
        forwarded.put("X-Forwarded-Host", request.getHeader("X-Forwarded-Host"));
        forwarded.put("X-Forwarded-For", request.getHeader("X-Forwarded-For"));
        forwarded.put("Host", request.getHeader("Host"));
        resp.setForwardedHeaders(forwarded);
        resp.setAllHeaders(extractHeaders(request));

        // 予測リダイレクト URL の算出
        String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
        String sampleRelativeTarget = contextPath + "/api/redirect/target";
        int port = request.getServerPort();
        String portPart = "";
        if ("http".equalsIgnoreCase(request.getScheme()) && port != 80 && port > 0) {
            portPart = ":" + port;
        } else if ("https".equalsIgnoreCase(request.getScheme()) && port != 443 && port > 0) {
            portPart = ":" + port;
        }
        String predictedSendRedirect = request.getScheme() + "://" + request.getServerName() + portPart + sampleRelativeTarget;
        resp.setPredictedSendRedirectUrl(predictedSendRedirect);

        String predictedBuilder = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/api/redirect/target").toUriString();
        resp.setPredictedUriComponentsBuilderUrl(predictedBuilder);

        // Filter のラップ状況
        boolean filterActive = request.getClass().getName().contains("Forwarded")
                || (request.getHeader("X-Forwarded-Proto") != null && request.getScheme().equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
        resp.setForwardedHeaderFilterActive(filterActive);

        resp.setUndertowProxyAddressForwardingHint(
                "Undertow 側での設定: /subsystem=undertow/server=default-server/http-listener=default:write-attribute(name=proxy-address-forwarding, value=true)");

        log.info("Redirect info requested. requestId={}, scheme={}, isSecure={}, serverPort={}, filterActive={}",
                requestId, request.getScheme(), request.isSecure(), request.getServerPort(), filterActive);
        return ResponseEntity.ok(resp);
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.put(name, request.getHeader(name));
            }
        }
        return headers;
    }
}
