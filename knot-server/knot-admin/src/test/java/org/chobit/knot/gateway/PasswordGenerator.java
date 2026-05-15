package org.chobit.knot.gateway;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "admin123";
        String hash = encoder.encode(rawPassword);
        
        System.out.println("Raw password: " + rawPassword);
        System.out.println("BCrypt hash: " + hash);
        System.out.println("Hash length: " + hash.length());
        System.out.println("Verify matches: " + encoder.matches(rawPassword, hash));
    }
}
