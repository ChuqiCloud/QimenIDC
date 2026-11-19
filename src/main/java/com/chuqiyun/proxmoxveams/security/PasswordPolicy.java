package com.chuqiyun.proxmoxveams.security;

/**
 * Shared password policy for administrator and virtual-machine credentials.
 * A password must be at least eight characters and contain at least two of
 * uppercase letters, lowercase letters, digits, and special characters.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;
    private static final String MESSAGE =
            "密码至少8位，并至少包含大写、小写、数字、特殊字符中的两类";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return validate(password) == null;
    }

    /** Returns null when valid, otherwise a safe user-facing validation message. */
    public static String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return MESSAGE;
        }
        int categories = 0;
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isUpperCase(ch)) {
                upper = true;
            } else if (Character.isLowerCase(ch)) {
                lower = true;
            } else if (Character.isDigit(ch)) {
                digit = true;
            } else if (!Character.isWhitespace(ch)) {
                special = true;
            }
        }
        if (upper) categories++;
        if (lower) categories++;
        if (digit) categories++;
        if (special) categories++;
        return categories >= 2 ? null : MESSAGE;
    }

    public static String message() {
        return MESSAGE;
    }
}
