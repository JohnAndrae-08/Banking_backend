package com.example.banking.dto;

import java.math.BigDecimal;

public class TransferResponse {
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private BigDecimal sourceBalanceAfter;
    private String referenceNumber;

    public String getSourceAccountNumber() { return sourceAccountNumber; }
    public void setSourceAccountNumber(String sourceAccountNumber) { this.sourceAccountNumber = sourceAccountNumber; }
    public String getDestinationAccountNumber() { return destinationAccountNumber; }
    public void setDestinationAccountNumber(String destinationAccountNumber) { this.destinationAccountNumber = destinationAccountNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getSourceBalanceAfter() { return sourceBalanceAfter; }
    public void setSourceBalanceAfter(BigDecimal sourceBalanceAfter) { this.sourceBalanceAfter = sourceBalanceAfter; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
}
