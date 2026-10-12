package com.example.dhapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link HealthCheckController} の単体テスト。
 *
 * GET /api/healthcheck および GET /healthcheck 等の呼び出しに対し、
 * 処理を基本実行せずに 200 ステータスコードを返却することを検証する。
 */
class HealthCheckControllerTest {

    private HealthCheckController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new HealthCheckController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void directCallReturns200OkWithBody() {
        ResponseEntity<String> response = controller.healthCheck();
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getStatusCode().value());
        assertEquals("OK", response.getBody());
    }

    @Test
    void getApiHealthCheckReturns200() throws Exception {
        mockMvc.perform(get("/api/healthcheck"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void getHealthCheckReturns200() throws Exception {
        mockMvc.perform(get("/healthcheck"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void getApiHealthReturns200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void getHealthReturns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void getHealthCheckHtmlReturns200() throws Exception {
        mockMvc.perform(get("/healthcheck.html"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }
}
