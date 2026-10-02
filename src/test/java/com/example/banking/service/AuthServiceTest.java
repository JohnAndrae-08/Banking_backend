package com.example.banking.service;

import com.example.banking.dto.LoginRequest;
import com.example.banking.dto.RegisterRequest;
import com.example.banking.exception.AuthenticationFailedException;
import com.example.banking.exception.DuplicateResourceException;
import com.example.banking.model.User;
import com.example.banking.repository.CustomerRepository;
import com.example.banking.repository.UserRepository;
import com.example.banking.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository users;
    @Mock
    CustomerRepository customers;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtUtil jwtUtil;
    @Mock
    com.example.banking.monitor.FlowPublisher flow;

    @InjectMocks
    AuthService authService;

    private RegisterRequest registerRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setUsername("alice");
        r.setEmail("alice@example.com");
        r.setPassword("password123");
        r.setFirstName("Alice");
        r.setLastName("Smith");
        return r;
    }

    @Test
    void register_success() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtUtil.generateToken(any(), eq("alice"), eq("USER"))).thenReturn("token123");

        var res = authService.register(registerRequest());

        assertEquals("token123", res.getToken());
        assertEquals("alice", res.getUsername());
        verify(users).save(any(User.class));
        verify(customers).save(any());
    }

    @Test
    void register_duplicateUsername() {
        when(users.existsByUsername("alice")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest()));
    }

    @Test
    void register_duplicateEmail() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest()));
    }

    @Test
    void login_success() {
        User u = new User();
        u.setId(1L);
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setPasswordHash("hashed");
        u.setRole("USER");
        u.setStatus("ACTIVE");
        when(users.findByUsernameOrEmail("alice")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "alice", "USER")).thenReturn("token123");

        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("alice");
        req.setPassword("password123");

        var res = authService.login(req);
        assertEquals("token123", res.getToken());
    }

    @Test
    void login_wrongPassword() {
        User u = new User();
        u.setUsername("alice");
        u.setPasswordHash("hashed");
        u.setStatus("ACTIVE");
        when(users.findByUsernameOrEmail("alice")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("alice");
        req.setPassword("wrong");

        assertThrows(AuthenticationFailedException.class, () -> authService.login(req));
    }

    @Test
    void login_disabledAccount() {
        User u = new User();
        u.setUsername("alice");
        u.setStatus("DISABLED");
        when(users.findByUsernameOrEmail("alice")).thenReturn(Optional.of(u));

        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("alice");
        req.setPassword("password123");

        assertThrows(AuthenticationFailedException.class, () -> authService.login(req));
    }
}
