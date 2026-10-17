package vn.edu.fpt.contactbook.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Mật khẩu tạm 12 ký tự từ SecureRandom, bỏ ký tự dễ nhầm (0/O/o, 1/l/I), luôn có chữ và số
 * và luôn qua PasswordPolicy. Không ghi mật khẩu ra log hay audit.
 */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 12;
    static final String LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        while (true) {
            StringBuilder password = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String candidate = password.toString();
            if (PasswordPolicy.validate(null, candidate, candidate).isEmpty()) {
                return candidate;
            }
        }
    }
}
