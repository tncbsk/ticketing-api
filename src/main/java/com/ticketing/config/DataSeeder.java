package com.ticketing.config;

import com.ticketing.entity.Role;
import com.ticketing.entity.User;
import com.ticketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seed("admin@ticketing.local", "Admin123!", Role.ADMIN);
        seed("organizer@ticketing.local", "Organizer123!", Role.ORGANIZER);
        seed("customer@ticketing.local", "Customer123!", Role.CUSTOMER);
    }

    private void seed(String email, String rawPassword, Role role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.getRoles().add(role);
        userRepository.save(user);
        log.info("Seed user created: {}", email);
    }
}
