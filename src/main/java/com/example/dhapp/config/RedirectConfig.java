package com.example.dhapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * ALB 等のリバースプロキシが付与する {@code X-Forwarded-*} ヘッダを処理するための構成。
 *
 * <p>ALB（HTTPS）から JBoss EAP コンテナ（HTTP）への通信環境において、
 * アプリケーションが相対リダイレクト（{@code response.sendRedirect(...)}）を発行すると、
 * コンテナが認識しているスキーム（{@code http}）に基づき {@code Location: http://...} が
 * 生成されてしまう問題を解決する。</p>
 *
 * <p>{@link ForwardedHeaderFilter} を最高優先順位（{@link Ordered#HIGHEST_PRECEDENCE}）で
 * 登録することにより、リクエストおよびレスポンスがラップされ、
 * <ul>
 *   <li>{@code request.getScheme()} が {@code "https"} を返す</li>
 *   <li>{@code request.isSecure()} が {@code true} を返す</li>
 *   <li>{@code request.getServerPort()} が {@code 443}（または X-Forwarded-Port）を返す</li>
 *   <li>{@code response.sendRedirect(relativeUrl)} 呼び出し時に {@code https://} で始まる絶対 URL が自動生成される</li>
 * </ul>
 * 動作を実現する。</p>
 */
@Configuration
public class RedirectConfig {

    private static final Logger log = LoggerFactory.getLogger(RedirectConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.redirect.forwarded-filter.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        log.info("Registering ForwardedHeaderFilter with HIGHEST_PRECEDENCE to adapt X-Forwarded-* headers (HTTPS redirect support)");
        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
        FilterRegistrationBean<ForwardedHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
