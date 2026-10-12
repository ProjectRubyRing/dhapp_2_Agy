package com.example.dhapp.logging;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

import ch.qos.logback.core.PropertyDefinerBase;

/**
 * ログファイル名のプレフィックスに使う、このホストの IPv4 アドレスを返す Logback の PropertyDefiner。
 *
 * <p>logback-spring.xml の {@code <define>} から呼ばれ、区切り文字をハイフンに置き換えた形
 * （例: {@code 10.0.1.23} → {@code 10-0-1-23}）で返す。</p>
 *
 * <p>アドレスは次の順で決める。
 * <ol>
 *   <li>{@link InetAddress#getLocalHost()}（ホスト名から引いたアドレス）がループバック以外の IPv4 ならそれ</li>
 *   <li>稼働中・非ループバック・非仮想の NIC に付いた IPv4（サイトローカルを優先）</li>
 *   <li>どれも取れなければ {@code 127.0.0.1}</li>
 * </ol>
 * Logback の初期化中に呼ばれるため、例外は外へ投げずフォールバックする
 * （投げるとプロパティが未定義になり、ファイル名が {@code HOST_IP_IS_UNDEFINED} になる）。</p>
 */
public class HostIpPropertyDefiner extends PropertyDefinerBase {

    static final String FALLBACK_ADDRESS = HostAddressResolver.FALLBACK_ADDRESS;

    @Override
    public String getPropertyValue() {
        return HostAddressResolver.resolveHostIpToken();
    }

    /** IPv4 アドレスの区切り文字（.）をハイフンに置き換える。 */
    static String toFileNameToken(String address) {
        return HostAddressResolver.toFileNameToken(address);
    }
}
