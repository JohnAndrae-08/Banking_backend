package com.example.banking.service;

import com.example.banking.dto.DepositRequest;
import com.example.banking.dto.WithdrawRequest;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.exception.InvalidTransactionException;
import com.example.banking.exception.UnauthorizedAccountAccessException;
import com.example.banking.model.Account;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    AccountRepository accounts;
    @Mock
    TransactionRepository transactions;
    @Mock
    CustomerService customerService;
    @Mock
    com.example.banking.monitor.FlowPublisher flow;

    @InjectMocks
    AccountService accountService;

    private Account account(Long id, String balance) {
        Account a = new Account();
        a.setId(id);
        a.setCustomerId(10L);
        a.setAccountNumber("1000000001");
        a.setAccountType("SAVINGS");
        a.setBalance(new BigDecimal(balance));
        a.setStatus("ACTIVE");
        return a;
    }

    private void stubOwnership(Long userId, Account a) {
        when(accounts.findById(a.getId())).thenReturn(Optional.of(a));
        when(accounts.findOwnerUserId(a.getId())).thenReturn(Optional.of(userId));
    }

    @Test
    void deposit_success() {
        Account a = account(new BigDecimal("1").longValue(), "500.00");
        stubOwnership(7L, a);
        when(accounts.depositById(eq(1L), any())).thenReturn(1);

        DepositRequest req = new DepositRequest();
        req.setAmount(new BigDecimal("100.00"));

        var res = accountService.deposit(7L, 1L, req);
        assertEquals(new BigDecimal("600.00"), res.getBalance());
        verify(transactions).save(any());
    }

    @Test
    void deposit_zeroRejected() {
        DepositRequest req = new DepositRequest();
        req.setAmount(BigDecimal.ZERO);
        assertThrows(InvalidTransactionException.class,
                () -> accountService.deposit(7L, 1L, req));
    }

    @Test
    void withdraw_success() {
        Account a = account(1L, "500.00");
        stubOwnership(7L, a);
        when(accounts.withdrawById(eq(1L), any())).thenReturn(1);

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("200.00"));

        var res = accountService.withdraw(7L, 1L, req);
        assertEquals(new BigDecimal("300.00"), res.getBalance());
        verify(transactions).save(any());
    }

    @Test
    void withdraw_insufficientBalance() {
        Account a = account(1L, "50.00");
        stubOwnership(7L, a);

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("200.00"));

        assertThrows(InsufficientBalanceException.class,
                () -> accountService.withdraw(7L, 1L, req));
        verify(transactions, never()).save(any());
    }

    @Test
    void withdraw_unauthorizedAccess() {
        Account a = account(1L, "500.00");
        when(accounts.findById(1L)).thenReturn(Optional.of(a));
        when(accounts.findOwnerUserId(1L)).thenReturn(Optional.of(99L));

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("10.00"));

        assertThrows(UnauthorizedAccountAccessException.class,
                () -> accountService.withdraw(7L, 1L, req));
    }
}
