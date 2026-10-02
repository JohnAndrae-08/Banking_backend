package com.example.banking.service;

import com.example.banking.dto.AuthResponse;
import com.example.banking.dto.LoginRequest;
import com.example.banking.dto.RegisterRequest;
import com.example.banking.exception.AuthenticationFailedException;
import com.example.banking.exception.DuplicateResourceException;
import com.example.banking.model.Customer;
import com.example.banking.model.User;
import com.example.banking.monitor.FlowPublisher;
import com.example.banking.repository.CustomerRepository;
import com.example.banking.repository.UserRepository;
import com.example.banking.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FlowPublisher flow;

    public AuthService(UserRepository users, CustomerRepository customers,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil, FlowPublisher flow) {
        this.users = users;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.flow = flow;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        flow.service("AuthService.register started");
        flow.transactionBegin();
        flow.auth("Registration validation", false);        if (users.existsByUsername(req.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (users.existsByEmail(req.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        // BCrypt-hashed; never stored as plain text.
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        users.save(user);

        Customer customer = new Customer();
        customer.setUserId(user.getId());
        customer.setFirstName(req.getFirstName());
        customer.setMiddleName(req.getMiddleName());
        customer.setLastName(req.getLastName());
        customer.setPhone(req.getPhone());
        customer.setAddress(req.getAddress());
        customers.save(customer);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        flow.auth("User registered, JWT generated (Bearer ********)", true);
        flow.database("User + customer rows committed");
        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }

    public AuthResponse login(LoginRequest req) {
        flow.service("AuthService.login started");
        flow.auth("Credential lookup", false);        User user = users.findByUsernameOrEmail(req.getUsernameOrEmail())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));
        if ("DISABLED".equalsIgnoreCase(user.getStatus())) {
            throw new AuthenticationFailedException("Account is disabled");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            flow.auth("Login failed: invalid credentials", false);
            throw new AuthenticationFailedException("Invalid credentials");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        flow.auth("Login successful, JWT generated (Bearer ********)", true);
        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }
}
