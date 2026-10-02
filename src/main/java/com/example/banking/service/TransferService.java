package com.example.banking.service;

import com.example.banking.dto.TransactionResponse;
import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.exception.AccountNotActiveException;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.exception.InvalidTransactionException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.exception.UnauthorizedAccountAccessException;
import com.example.banking.model.Account;
import com.example.banking.model.BankTransaction;
import com.example.banking.monitor.FlowPublisher;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import com.example.banking.util.BankingIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final FlowPublisher flow;

    public TransferService(AccountRepository accounts, TransactionRepository transactions, FlowPublisher flow) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.flow = flow;
    }

    /**
     * Transfer flow (all inside ONE database transaction):
     * validate -> lock both rows (SELECT ... FOR UPDATE, ordered by id to avoid
     * deadlocks) -> check balance -> debit sender -> credit receiver ->
     * insert TRANSFER_OUT + TRANSFER_IN records -> COMMIT.
     * Any failure rolls back the entire transfer (atomicity).
     */
    @Transactional
    public TransferResponse transfer(Long userId, TransferRequest req) {
        flow.service("TransferService.transfer started (WRITE, atomic)");
        flow.transactionBegin();
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            flow.error("Transfer failed at VALIDATION", "Transfer amount must be greater than 0");
            flow.transactionRollback("Invalid amount");
            throw new InvalidTransactionException("Transfer amount must be greater than 0");
        }
        if (req.getSourceAccountNumber().equals(req.getDestinationAccountNumber())) {
            flow.error("Transfer failed at VALIDATION", "Sender and receiver must differ");
            flow.transactionRollback("Same account");
            throw new InvalidTransactionException("Sender and receiver must be different accounts");
        }
        flow.service("Transfer validation passed: amount " + req.getAmount());

        // Lock in deterministic id order to avoid deadlocks under concurrency.
        Account first;
        Account second;
        boolean sourceFirst;
        Account srcProbe = accounts.findByAccountNumber(req.getSourceAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        Account dstProbe = accounts.findByAccountNumber(req.getDestinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        if (srcProbe.getId().compareTo(dstProbe.getId()) <= 0) {
            first = accounts.findByIdForUpdate(srcProbe.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
            second = accounts.findByIdForUpdate(dstProbe.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
            sourceFirst = true;
        } else {
            second = accounts.findByIdForUpdate(srcProbe.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
            first = accounts.findByIdForUpdate(dstProbe.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
            sourceFirst = false;
        }
        Account source = sourceFirst ? first : second;
        Account destination = sourceFirst ? second : first;
        flow.service("Both accounts locked (SELECT ... FOR UPDATE)");

        // Sender must belong to the caller; receiver may belong to anyone.
        Long ownerUserId = accounts.findOwnerUserId(source.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        if (!ownerUserId.equals(userId)) {
            flow.error("Transfer failed at OWNERSHIP CHECK", "You do not own the source account");
            flow.transactionRollback("Unauthorized source account");
            throw new UnauthorizedAccountAccessException("You do not own the source account");
        }

        if (!"ACTIVE".equalsIgnoreCase(source.getStatus())) {
            flow.error("Transfer failed at SOURCE CHECK", "Source account is " + source.getStatus());
            flow.transactionRollback("Source not active");
            throw new AccountNotActiveException("Source account is " + source.getStatus());
        }
        if (!"ACTIVE".equalsIgnoreCase(destination.getStatus())) {
            flow.error("Transfer failed at DESTINATION CHECK", "Destination account is " + destination.getStatus());
            flow.transactionRollback("Destination not active");
            throw new AccountNotActiveException("Destination account is " + destination.getStatus());
        }
        if (source.getBalance().compareTo(req.getAmount()) < 0) {
            flow.error("Transfer failed at BALANCE CHECK", "Insufficient balance");
            flow.transactionRollback("Insufficient balance");
            throw new InsufficientBalanceException("Insufficient balance");
        }
        flow.service("Balance check passed");

        BigDecimal senderBefore = source.getBalance();
        BigDecimal receiverBefore = destination.getBalance();
        BigDecimal senderAfter = senderBefore.subtract(req.getAmount());
        BigDecimal receiverAfter = receiverBefore.add(req.getAmount());

        flow.service("Debiting source " + source.getAccountNumber());
        accounts.updateBalance(source.getId(), senderAfter);
        flow.service("Crediting destination " + destination.getAccountNumber());
        accounts.updateBalance(destination.getId(), receiverAfter);

        String reference = BankingIds.generateReferenceNumber();

        BankTransaction out = new BankTransaction();
        out.setReferenceNumber(reference + "-OUT");
        out.setAccountId(source.getId());
        out.setRelatedAccountId(destination.getId());
        out.setTransactionType("TRANSFER_OUT");
        out.setAmount(req.getAmount());
        out.setBalanceBefore(senderBefore);
        out.setBalanceAfter(senderAfter);
        out.setDescription(req.getDescription());
        transactions.save(out);

        BankTransaction in = new BankTransaction();
        in.setReferenceNumber(reference + "-IN");
        in.setAccountId(destination.getId());
        in.setRelatedAccountId(source.getId());
        in.setTransactionType("TRANSFER_IN");
        in.setAmount(req.getAmount());
        in.setBalanceBefore(receiverBefore);
        in.setBalanceAfter(receiverAfter);
        in.setDescription(req.getDescription());
        transactions.save(in);

        TransferResponse res = new TransferResponse();        res.setSourceAccountNumber(source.getAccountNumber());
        res.setDestinationAccountNumber(destination.getAccountNumber());
        res.setAmount(req.getAmount());
        res.setSourceBalanceAfter(senderAfter);
        res.setReferenceNumber(reference);
        flow.database("Transfer committed: " + senderBefore + " -> " + senderAfter
                + " / " + receiverBefore + " -> " + receiverAfter);
        return res;
    }

    public TransactionResponse getTransaction(Long userId, Long transactionId) {
        BankTransaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        Long ownerUserId = accounts.findOwnerUserId(tx.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (!ownerUserId.equals(userId)) {
            throw new UnauthorizedAccountAccessException("You do not have access to this transaction");
        }
        TransactionResponse r = new TransactionResponse();
        r.setId(tx.getId());
        r.setReferenceNumber(tx.getReferenceNumber());
        r.setTransactionType(tx.getTransactionType());
        r.setAmount(tx.getAmount());
        r.setBalanceBefore(tx.getBalanceBefore());
        r.setBalanceAfter(tx.getBalanceAfter());
        r.setDescription(tx.getDescription());
        r.setCreatedAt(tx.getCreatedAt());
        return r;
    }
}
