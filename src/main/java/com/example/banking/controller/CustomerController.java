package com.example.banking.controller;

import com.example.banking.dto.ApiResponse;
import com.example.banking.dto.CustomerResponse;
import com.example.banking.dto.UpdateCustomerRequest;
import com.example.banking.security.CustomUserDetailsService;
import com.example.banking.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerResponse>> me(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = ((CustomUserDetailsService.CustomUserPrincipal) principal).userId();
        return ResponseEntity.ok(ApiResponse.ok("Customer profile", customerService.getMyProfile(userId)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateCustomerRequest req) {
        Long userId = ((CustomUserDetailsService.CustomUserPrincipal) principal).userId();
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", customerService.updateMyProfile(userId, req)));
    }
}
