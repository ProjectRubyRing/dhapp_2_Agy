package com.example.dhapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ヘルスチェック用 REST コントローラ。
 *
 * <p>GET /api/healthcheck</p>
 * <p>GET /healthcheck</p>
 *
 * <p>stale file handle エラー調査および外部死活監視用。
 * API の内部では DB・キャッシュ・外部サービス等の処理は基本実行せず、
 * そのまま HTTP 200 ステータスコードを返却する。</p>
 *
 * <p>呼び出し時にロガー（{@link Logger}）へ記録することで、
 * Log4j2 の {@code tracelog} 等への出力および時間単位ローテーション時の
 * ファイルハンドルの挙動検証を行う。</p>
 */
@RestController
@RequestMapping
public class HealthCheckController {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckController.class);

    @GetMapping(value = { "/api/healthcheck", "/healthcheck", "/api/health", "/health", "/healthcheck.html" })
    public ResponseEntity<String> healthCheck() {
        log.info("GET /healthcheck received. status=200");
        return ResponseEntity.ok("OK");
    }
}
