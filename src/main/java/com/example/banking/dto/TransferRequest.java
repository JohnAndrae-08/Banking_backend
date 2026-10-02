package com.example.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class TransferRequest {
    @NotBlank
    private String sourceAccountNumber;

    @NotBlank
    private String destinationAccountNumber;

    @NotNull
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    @Size(max = 500)
    private String description;

    public String getSourceAccountNumber() { return sourceAccountNumber; }
    public void setSourceAccountNumber(String sourceAccountNumber) { this.sourceAccountNumber = sourceAccountNumber; }
    public String getDestinationAccountNumber() { return destinationAccountNumber; }
    public void setDestinationAccountNumber(String destinationAccountNumber) { this.destinationAccountNumber = destinationAccountNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
