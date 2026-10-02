package com.example.banking.controller;

import com.example.banking.dto.ApiResponse;
import com.example.banking.dto.TransactionResponse;
import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.security.CustomUserDetailsService;
import com.example.banking.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody TransferRequest req) {
        Long userId = ((CustomUserDetailsService.CustomUserPrincipal) principal).userId();
        return ResponseEntity.ok(ApiResponse.ok("Transfer completed successfully",
                transferService.transfer(userId, req)));
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long transactionId) {
        Long userId = ((CustomUserDetailsService.CustomUserPrincipal) principal).userId();
        return ResponseEntity.ok(ApiResponse.ok("Transaction retrieved",
                transferService.getTransaction(userId, transactionId)));
    }
}
