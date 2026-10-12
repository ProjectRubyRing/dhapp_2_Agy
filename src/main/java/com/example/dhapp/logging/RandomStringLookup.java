package com.example.dhapp.logging;

import java.security.SecureRandom;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

/**
 * ログファイル名（{@code <IP>_<ランダム>.err} や {@code <IP>_asyncdriver_<ランダム数字>.err}）に
 * 使用する起動時生成のランダム文字列を提供する Log4j2 Lookup。
 */
@Plugin(name = "randomString", category = StrLookup.CATEGORY)
public class RandomStringLookup implements StrLookup {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String DIGITS = "0123456789";

    private static final String ERR_FILE_ID = generateRandom(20, ALPHANUMERIC);
    private static final String ASYNCDRIVER_ERR_FILE_ID = generateRandom(17, DIGITS);

    static {
        if (System.getProperty("ERR_FILE_ID") == null) {
            System.setProperty("ERR_FILE_ID", ERR_FILE_ID);
        }
        if (System.getProperty("ASYNCDRIVER_ERR_FILE_ID") == null) {
            System.setProperty("ASYNCDRIVER_ERR_FILE_ID", ASYNCDRIVER_ERR_FILE_ID);
        }
    }

    public static String getErrFileId() {
        return ERR_FILE_ID;
    }

    public static String getAsyncDriverErrFileId() {
        return ASYNCDRIVER_ERR_FILE_ID;
    }

    private static String generateRandom(int length, String chars) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public String lookup(String key) {
        if ("numeric".equalsIgnoreCase(key) || "digits".equalsIgnoreCase(key)) {
            return ASYNCDRIVER_ERR_FILE_ID;
        }
        return ERR_FILE_ID;
    }

    @Override
    public String lookup(LogEvent event, String key) {
        return lookup(key);
    }
}
