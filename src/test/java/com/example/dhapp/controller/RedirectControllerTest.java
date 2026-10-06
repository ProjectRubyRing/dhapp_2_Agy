package com.example.dhapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import com.example.dhapp.dto.RedirectCheckResponse;
import com.example.dhapp.dto.RedirectInfoResponse;
import com.example.dhapp.dto.RedirectTargetResponse;
import com.example.dhapp.service.RedirectCheckService;

/**
 * {@link RedirectController} の単体テスト。
 */
class RedirectControllerTest {

    private RedirectCheckService mockCheckService;
    private RedirectController controller;

    @BeforeEach
    void setUp() {
        mockCheckService = new RedirectCheckService("https://alb/trigger", "http://localhost:8080/trigger", 1000, 1000);
        controller = new RedirectController(mockCheckService);
    }

    @Test
    void triggerWithSendRedirectSets302AndDebugHeaders() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/redirect/trigger");
        request.setContextPath("/iwinmichl");
        request.setServerName("backend");
        request.setServerPort(8180);
        request.setScheme("http");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = controller.triggerGet(null, "sendRedirect", 302, request, response);

        assertEquals(HttpStatus.FOUND.value(), response.getStatus());
        assertEquals("http", response.getHeader("X-Redirect-Request-Scheme"));
        assertEquals("sendRedirect", response.getHeader("X-Redirect-Mode"));
        assertEquals("/iwinmichl/api/redirect/target", response.getHeader("X-Redirect-Target"));
        assertEquals("/iwinmichl/api/redirect/target", response.getRedirectedUrl());
    }

    @Test
    void triggerWithRedirectViewReturnsModelAndView() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/redirect/trigger");
        request.setContextPath("/iwinmichl");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = controller.triggerGet("/iwinmichl/custom/path", "redirectView", 302, request, response);

        assertTrue(result instanceof ModelAndView);
        ModelAndView mav = (ModelAndView) result;
        assertTrue(mav.getView() instanceof RedirectView);
        RedirectView rv = (RedirectView) mav.getView();
        assertEquals("/iwinmichl/custom/path", rv.getUrl());
    }

    @Test
    void triggerWithResponseEntityReturns302WithLocation() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/redirect/trigger");
        request.setContextPath("/iwinmichl");
        request.setServerName("alb");
        request.setServerPort(443);
        request.setScheme("https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = controller.triggerGet("/iwinmichl/api/redirect/target", "responseEntity", 302, request, response);

        assertTrue(result instanceof ResponseEntity<?>);
        ResponseEntity<?> entity = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.FOUND, entity.getStatusCode());
        assertNotNull(entity.getHeaders().getLocation());
        assertTrue(entity.getHeaders().getLocation().toString().contains("https://alb"));
    }

    @Test
    void forwardedHeaderFilterRewritesSendRedirectToHttps() throws ServletException, IOException {
        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();

        MockHttpServletRequest rawRequest = new MockHttpServletRequest("GET", "/api/redirect/trigger");
        rawRequest.setContextPath("/iwinmichl");
        rawRequest.setServerName("backend");
        rawRequest.setServerPort(8180);
        rawRequest.setScheme("http");
        rawRequest.addHeader("X-Forwarded-Proto", "https");
        rawRequest.addHeader("X-Forwarded-Port", "443");
        rawRequest.addHeader("X-Forwarded-Host", "alb");
        rawRequest.addHeader("Host", "alb");

        MockHttpServletResponse rawResponse = new MockHttpServletResponse();

        // ForwardedHeaderFilter を通すことで、リクエストとレスポンスがラップされる
        filter.doFilter(rawRequest, rawResponse, new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) throws IOException {
                HttpServletRequest wrappedReq = (HttpServletRequest) req;
                HttpServletResponse wrappedRes = (HttpServletResponse) res;

                assertEquals("https", wrappedReq.getScheme());
                assertTrue(wrappedReq.isSecure());
                assertEquals("alb", wrappedReq.getServerName());
                assertEquals(443, wrappedReq.getServerPort());

                // sendRedirect 実行
                try {
                    controller.triggerGet(null, "sendRedirect", 302, wrappedReq, wrappedRes);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        assertEquals(HttpStatus.FOUND.value(), rawResponse.getStatus());
        String redirectedUrl = rawResponse.getRedirectedUrl();
        assertNotNull(redirectedUrl);
        // ForwardedHeaderExtractingResponse により Location が https://alb/iwinmichl/api/redirect/target に変換される
        assertTrue(redirectedUrl.startsWith("https://alb"),
                "Redirected URL should start with https://alb, but was: " + redirectedUrl);
    }

    @Test
    void targetReturns200WithRequestDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/redirect/target");
        request.setScheme("https");
        request.setServerName("alb");
        request.setServerPort(443);
        request.addHeader("X-Forwarded-Proto", "https");

        ResponseEntity<RedirectTargetResponse> response = controller.targetGet(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().getStatus());
        assertEquals("https", response.getBody().getScheme());
        assertEquals("alb", response.getBody().getServerName());
    }

    @Test
    void infoReturnsDiagnostics() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/redirect/info");
        request.setContextPath("/iwinmichl");
        request.setScheme("https");
        request.setServerName("alb");
        request.setServerPort(443);
        request.addHeader("X-Forwarded-Proto", "https");

        ResponseEntity<RedirectInfoResponse> response = controller.info(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("https", response.getBody().getRequestScheme());
        assertEquals("alb", response.getBody().getServerName());
        assertEquals(443, response.getBody().getServerPort());
        assertTrue(response.getBody().getPredictedSendRedirectUrl().startsWith("https://alb/iwinmichl"));
    }
}
