package com.example.banking.dto;

import java.math.BigDecimal;

public class BalanceResponse {
    private String accountNumber;
    private BigDecimal balance;
    private String status;

    public BalanceResponse(String accountNumber, BigDecimal balance, String status) {
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.status = status;
    }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
