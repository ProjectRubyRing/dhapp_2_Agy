package com.example.dhapp.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

/**
 * ログファイル名プレフィックス用のホスト IPv4 アドレス（ハイフン区切り）を提供する Log4j2 Lookup。
 *
 * <p>Log4j2 設定ファイル（{@code log4j2.xml} 等）内で {@code ${hostIp:}} として参照される。</p>
 */
@Plugin(name = "hostIp", category = StrLookup.CATEGORY)
public class HostIpLookup implements StrLookup {

    @Override
    public String lookup(String key) {
        return HostAddressResolver.resolveHostIpToken();
    }

    @Override
    public String lookup(LogEvent event, String key) {
        return lookup(key);
    }
}
