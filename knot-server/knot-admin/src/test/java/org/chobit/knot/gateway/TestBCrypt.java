package org.chobit.knot.gateway;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestBCrypt {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "admin123";
        String hashedPassword = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        
        System.out.println("Raw password: " + rawPassword);
        System.out.println("Hashed password: " + hashedPassword);
        System.out.println("Matches: " + encoder.matches(rawPassword, hashedPassword));
        
        // 如果不匹配，生成一个新的哈希
        if (!encoder.matches(rawPassword, hashedPassword)) {
            System.out.println("\nGenerating new hash for 'admin123':");
            String newHash = encoder.encode(rawPassword);
            System.out.println(newHash);
        }
    }
}
