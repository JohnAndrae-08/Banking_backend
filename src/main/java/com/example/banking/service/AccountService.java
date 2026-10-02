package com.example.banking.service;

import com.example.banking.dto.AccountResponse;
import com.example.banking.dto.BalanceResponse;
import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.dto.DepositRequest;
import com.example.banking.dto.TransactionResponse;
import com.example.banking.dto.WithdrawRequest;
import com.example.banking.exception.AccountNotActiveException;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.exception.InvalidTransactionException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.exception.UnauthorizedAccountAccessException;
import com.example.banking.model.Account;
import com.example.banking.model.BankTransaction;
import com.example.banking.model.Customer;
import com.example.banking.monitor.FlowPublisher;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import com.example.banking.util.BankingIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CustomerService customerService;
    private final FlowPublisher flow;

    public AccountService(AccountRepository accounts, TransactionRepository transactions,
                          CustomerService customerService, FlowPublisher flow) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.customerService = customerService;
        this.flow = flow;
    }

    @Transactional
    public AccountResponse createAccount(Long userId, CreateAccountRequest req) {
        flow.service("AccountService.createAccount started (" + req.getAccountType() + ")");
        flow.transactionBegin();        Customer customer = customerService.requireCustomer(userId);
        String accountNumber = generateUniqueAccountNumber();
        Account account = new Account();
        account.setCustomerId(customer.getId());
        account.setAccountNumber(accountNumber);
        account.setAccountType(req.getAccountType());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus("ACTIVE");
        accounts.save(account);
        Account saved = accounts.findById(account.getId()).orElse(account);
        flow.database("Account row inserted");
        return toResponse(saved);
    }

    public List<AccountResponse> listMyAccounts(Long userId) {
        flow.service("AccountService.listMyAccounts started");        Customer customer = customerService.requireCustomer(userId);
        return accounts.findByCustomerId(customer.getId()).stream().map(this::toResponse).toList();
    }

    public AccountResponse getAccount(Long userId, Long accountId) {
        Account account = requireOwnedAccount(userId, accountId);
        return toResponse(account);
    }

    public AccountResponse getAccountByNumber(Long userId, String accountNumber) {
        Account account = accounts.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        assertOwnership(userId, account);
        return toResponse(account);
    }

    public BalanceResponse getBalance(Long userId, Long accountId) {
        flow.service("AccountService.getBalance started (READ)");
        flow.auth("Ownership check", true);        Account account = requireOwnedAccount(userId, accountId);
        return new BalanceResponse(account.getAccountNumber(), account.getBalance(), account.getStatus());
    }

    /**
     * Deposit flow: validate -> atomic UPDATE balance = balance + ? -> insert
     * transaction record. Both steps share one DB transaction (atomicity).
     */
    @Transactional
    public AccountResponse deposit(Long userId, Long accountId, DepositRequest req) {
        flow.service("AccountService.deposit started (WRITE, atomic)");
        flow.transactionBegin();
        flow.auth("Ownership + active validation", true);        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Deposit amount must be greater than 0");
        }
        Account account = requireOwnedAccount(userId, accountId);
        requireActive(account);

        BigDecimal before = account.getBalance();
        flow.service("Amount validated: DEPOSIT " + req.getAmount());
        int updated = accounts.depositById(accountId, req.getAmount());
        if (updated == 0) {
            throw new AccountNotActiveException("Account is not active");
        }
        BigDecimal after = before.add(req.getAmount());

        BankTransaction tx = new BankTransaction();
        tx.setReferenceNumber(BankingIds.generateReferenceNumber());
        tx.setAccountId(accountId);
        tx.setTransactionType("DEPOSIT");
        tx.setAmount(req.getAmount());
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setDescription(req.getDescription());
        transactions.save(tx);

        account.setBalance(after);
        flow.database("Deposit committed: balance " + before + " -> " + after);
        return toResponse(account);
    }

    /**
     * Withdrawal flow: atomic conditional update
     * (UPDATE ... WHERE balance >= ?) prevents negative balances under concurrency.
     * Row count of 0 means insufficient funds or inactive account.
     */
    @Transactional
    public AccountResponse withdraw(Long userId, Long accountId, WithdrawRequest req) {
        flow.service("AccountService.withdraw started (WRITE, atomic)");
        flow.transactionBegin();
        flow.auth("Ownership + active validation", true);        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Withdrawal amount must be greater than 0");
        }
        Account account = requireOwnedAccount(userId, accountId);
        requireActive(account);

        if (account.getBalance().compareTo(req.getAmount()) < 0) {
            flow.error("Withdrawal failed at BALANCE CHECK", "Insufficient balance");
            flow.transactionRollback("Insufficient balance");
            throw new InsufficientBalanceException("Insufficient balance");
        }
        BigDecimal before = account.getBalance();
        flow.service("Balance check passed: WITHDRAW " + req.getAmount());
        int updated = accounts.withdrawById(accountId, req.getAmount());
        if (updated == 0) {
            flow.error("Withdrawal failed at conditional UPDATE", "Insufficient balance or inactive");
            flow.transactionRollback("Conditional update affected 0 rows");
            // Re-read to distinguish the cause for a precise error message.
            Account fresh = accounts.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            if (!"ACTIVE".equalsIgnoreCase(fresh.getStatus())) {
                throw new AccountNotActiveException("Account is not active");
            }
            throw new InsufficientBalanceException("Insufficient balance");
        }
        BigDecimal after = before.subtract(req.getAmount());

        BankTransaction tx = new BankTransaction();
        tx.setReferenceNumber(BankingIds.generateReferenceNumber());
        tx.setAccountId(accountId);
        tx.setTransactionType("WITHDRAWAL");
        tx.setAmount(req.getAmount());
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setDescription(req.getDescription());
        transactions.save(tx);

        account.setBalance(after);
        flow.database("Withdrawal committed: balance " + before + " -> " + after);
        return toResponse(account);
    }

    public List<TransactionResponse> history(Long userId, Long accountId, int page, int size) {
        Account account = requireOwnedAccount(userId, accountId);
        int limit = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * limit;
        return transactions.findByAccountId(account.getId(), limit, offset).stream()
                .map(this::toTxResponse).toList();
    }

    // ---- helpers ----

    Account requireOwnedAccount(Long userId, Long accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        assertOwnership(userId, account);
        return account;
    }

    private void assertOwnership(Long userId, Account account) {
        Long ownerUserId = accounts.findOwnerUserId(account.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!ownerUserId.equals(userId)) {
            throw new UnauthorizedAccountAccessException("You do not own this account");
        }
    }

    private void requireActive(Account account) {
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new AccountNotActiveException("Account is " + account.getStatus());
        }
    }

    private String generateUniqueAccountNumber() {
        for (int i = 0; i < 10; i++) {
            String candidate = BankingIds.generateAccountNumber();
            if (!accounts.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new InvalidTransactionException("Could not generate unique account number");
    }

    AccountResponse toResponse(Account a) {
        AccountResponse r = new AccountResponse();
        r.setId(a.getId());
        r.setAccountNumber(a.getAccountNumber());
        r.setAccountType(a.getAccountType());
        r.setBalance(a.getBalance());
        r.setStatus(a.getStatus());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }

    private TransactionResponse toTxResponse(BankTransaction t) {
        TransactionResponse r = new TransactionResponse();
        r.setId(t.getId());
        r.setReferenceNumber(t.getReferenceNumber());
        r.setTransactionType(t.getTransactionType());
        r.setAmount(t.getAmount());
        r.setBalanceBefore(t.getBalanceBefore());
        r.setBalanceAfter(t.getBalanceAfter());
        r.setDescription(t.getDescription());
        r.setCreatedAt(t.getCreatedAt());
        return r;
    }
}
