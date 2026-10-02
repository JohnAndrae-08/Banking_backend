package com.example.banking.controller;

import com.example.banking.dto.AccountResponse;
import com.example.banking.dto.ApiResponse;
import com.example.banking.dto.BalanceResponse;
import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.dto.DepositRequest;
import com.example.banking.dto.TransactionResponse;
import com.example.banking.dto.WithdrawRequest;
import com.example.banking.security.CustomUserDetailsService;
import com.example.banking.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    private Long userId(UserDetails principal) {
        return ((CustomUserDetailsService.CustomUserPrincipal) principal).userId();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateAccountRequest req) {
        AccountResponse res = accountService.createAccount(userId(principal), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created", res));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> list(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Accounts retrieved",
                accountService.listMyAccounts(userId(principal))));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> get(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok("Account retrieved",
                accountService.getAccount(userId(principal), accountId)));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> balance(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok("Balance retrieved",
                accountService.getBalance(userId(principal), accountId)));
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<ApiResponse<AccountResponse>> deposit(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long accountId,
            @Valid @RequestBody DepositRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Deposit completed",
                accountService.deposit(userId(principal), accountId, req)));
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<ApiResponse<AccountResponse>> withdraw(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long accountId,
            @Valid @RequestBody WithdrawRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Withdrawal completed",
                accountService.withdraw(userId(principal), accountId, req)));
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> history(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Transaction history",
                accountService.history(userId(principal), accountId, page, size)));
    }
}
