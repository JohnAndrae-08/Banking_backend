package com.example.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateAccountRequest {
    @NotBlank
    @Pattern(regexp = "SAVINGS|CHECKING", message = "accountType must be SAVINGS or CHECKING")
    private String accountType;

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
}
