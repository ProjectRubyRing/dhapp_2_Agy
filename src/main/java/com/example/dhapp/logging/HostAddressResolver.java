package com.example.dhapp.logging;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * ホストの IPv4 アドレス解決ユーティリティ。
 *
 * <p>ログファイル名のプレフィックス（{@code <IP>_tracelog} 等）に使用する
 * 区切り文字をハイフンに置き換えた形式（例: {@code 10.0.1.23} → {@code 10-0-1-23}）を提供する。</p>
 */
public final class HostAddressResolver {

    public static final String FALLBACK_ADDRESS = "127.0.0.1";

    static {
        // Log4j2 の初期化前にシステムプロパティ HOST_IP が未定義の場合、
        // 解決した値をセットして設定ファイル (${sys:HOST_IP}) から直接参照可能にする。
        if (System.getProperty("HOST_IP") == null) {
            try {
                System.setProperty("HOST_IP", resolveHostIpToken());
            } catch (Exception ignored) {
                System.setProperty("HOST_IP", toFileNameToken(FALLBACK_ADDRESS));
            }
        }
    }

    private HostAddressResolver() {
    }

    /**
     * IPv4 アドレスの区切り文字（.）をハイフンに置き換える。
     * 例: "10.0.1.23" -> "10-0-1-23"
     */
    public static String toFileNameToken(String address) {
        if (address == null || address.isEmpty()) {
            return toFileNameToken(FALLBACK_ADDRESS);
        }
        return address.replace('.', '-');
    }

    /**
     * ホストの IPv4 アドレスを解決し、ハイフン区切りのトークンとして返す。
     */
    public static String resolveHostIpToken() {
        return toFileNameToken(resolveAddress());
    }

    /**
     * ホストの IPv4 アドレスを解決する。
     * 1. InetAddress.getLocalHost()（ループバック以外の IPv4）
     * 2. 稼働中 NIC の IPv4（サイトローカル優先）
     * 3. 取得不可の場合は 127.0.0.1
     */
    public static String resolveAddress() {
        try {
            InetAddress local = InetAddress.getLocalHost();
            if (local instanceof Inet4Address && !local.isLoopbackAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {
        }

        try {
            String siteLocal = null;
            String other = null;
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    if (address.isSiteLocalAddress()) {
                        if (siteLocal == null) {
                            siteLocal = address.getHostAddress();
                        }
                    } else if (other == null) {
                        other = address.getHostAddress();
                    }
                }
            }
            if (siteLocal != null) {
                return siteLocal;
            }
            if (other != null) {
                return other;
            }
        } catch (Exception ignored) {
        }

        return FALLBACK_ADDRESS;
    }
}
