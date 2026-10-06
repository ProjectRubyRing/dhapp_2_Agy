package com.example.dhapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.dhapp.dto.RedirectCheckResponse;
import com.example.dhapp.dto.RedirectComparisonResponse;
import com.sun.net.httpserver.HttpServer;

/**
 * {@link RedirectCheckService} のリダイレクト Location ヘッダ検証ロジックのテスト。
 */
class RedirectCheckServiceTest {

    private HttpServer server;
    private String baseUrl;
    private RedirectCheckService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // 1. 修正後: Location が https://
        server.createContext("/redirect-https", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://alb/iwinmichl/api/redirect/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // 2. 修正前（問題発生時）: Location が http://
        server.createContext("/redirect-http", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://backend:8180/iwinmichl/api/redirect/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // 3. リダイレクトではない (200 OK)
        server.createContext("/not-redirect", exchange -> {
            byte[] body = "OK".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        // 4. Location ヘッダが無い 302
        server.createContext("/no-location", exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // 5. ヘッダに応じて分岐するエンドポイント (比較テスト用)
        server.createContext("/conditional-redirect", exchange -> {
            String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
            if ("https".equalsIgnoreCase(proto)) {
                exchange.getResponseHeaders().set("Location", "https://alb/iwinmichl/api/redirect/target");
            } else {
                exchange.getResponseHeaders().set("Location", "http://localhost:8080/iwinmichl/api/redirect/target");
            }
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        service = new RedirectCheckService(baseUrl + "/redirect-https", baseUrl + "/conditional-redirect", 2000, 2000);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifiesLocationHttpsSuccess() {
        RedirectCheckResponse resp = service.check("custom", baseUrl + "/redirect-https", "sendRedirect", false, "req-1");
        assertEquals("SUCCESS", resp.getStatus());
        assertEquals("LOCATION_HTTPS_OK", resp.getVerdict());
        assertTrue(resp.isHttps());
        assertEquals("https://alb/iwinmichl/api/redirect/target", resp.getLocationHeader());
        assertEquals("https", resp.getLocationScheme());
        assertEquals("alb", resp.getLocationHost());
        assertEquals("/iwinmichl/api/redirect/target", resp.getLocationPath());
        assertNotNull(resp.getReport());
        assertTrue(resp.getReport().contains("LOCATION_HTTPS_OK"));
    }

    @Test
    void detectsLocationHttpFailure() {
        RedirectCheckResponse resp = service.check("custom", baseUrl + "/redirect-http", "sendRedirect", false, "req-2");
        assertEquals("FAILED", resp.getStatus());
        assertEquals("LOCATION_HTTP_FAILED", resp.getVerdict());
        assertFalse(resp.isHttps());
        assertEquals("http://backend:8180/iwinmichl/api/redirect/target", resp.getLocationHeader());
        assertEquals("http", resp.getLocationScheme());
        assertEquals("backend", resp.getLocationHost());
        assertEquals(8180, resp.getLocationPort());
        assertNotNull(resp.getTroubleshooting());
    }

    @Test
    void detectsNotARedirect() {
        RedirectCheckResponse resp = service.check("custom", baseUrl + "/not-redirect", "sendRedirect", false, "req-3");
        assertEquals("FAILED", resp.getStatus());
        assertEquals("NOT_A_REDIRECT", resp.getVerdict());
        assertFalse(resp.isHttps());
    }

    @Test
    void detectsNoLocationHeader() {
        RedirectCheckResponse resp = service.check("custom", baseUrl + "/no-location", "sendRedirect", false, "req-4");
        assertEquals("FAILED", resp.getStatus());
        assertEquals("NO_LOCATION_HEADER", resp.getVerdict());
        assertFalse(resp.isHttps());
    }

    @Test
    void verifiesComparisonMode() {
        RedirectComparisonResponse comp = service.checkComparison("sendRedirect", "req-comp");
        assertEquals("SUCCESS", comp.getStatus());
        assertTrue(comp.isVerifiedFix());
        assertNotNull(comp.getWithForwardedProto());
        assertNotNull(comp.getWithoutForwardedProto());
        assertTrue(comp.getWithForwardedProto().isHttps());
        assertFalse(comp.getWithoutForwardedProto().isHttps());
        assertNotNull(comp.getReport());
        assertTrue(comp.getReport().contains("PROVED"));
    }

    @Test
    void handlesConnectionFailureGracefully() {
        RedirectCheckResponse resp = service.check("custom", "http://127.0.0.1:1/invalid", "sendRedirect", false, "req-err");
        assertEquals("ERROR", resp.getStatus());
        assertEquals("CONNECTION_ERROR", resp.getVerdict());
        assertFalse(resp.isHttps());
    }
}
