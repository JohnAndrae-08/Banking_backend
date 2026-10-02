package com.example.banking.service;

import com.example.banking.dto.TransferRequest;
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
class TransferServiceTest {

    @Mock
    AccountRepository accounts;
    @Mock
    TransactionRepository transactions;
    @Mock
    com.example.banking.monitor.FlowPublisher flow;

    @InjectMocks
    TransferService transferService;

    private Account account(Long id, String number, String balance) {
        Account a = new Account();
        a.setId(id);
        a.setCustomerId(10L);
        a.setAccountNumber(number);
        a.setAccountType("SAVINGS");
        a.setBalance(new BigDecimal(balance));
        a.setStatus("ACTIVE");
        return a;
    }

    private TransferRequest request(String src, String dst, String amount) {
        TransferRequest r = new TransferRequest();
        r.setSourceAccountNumber(src);
        r.setDestinationAccountNumber(dst);
        r.setAmount(new BigDecimal(amount));
        r.setDescription("Transfer");
        return r;
    }

    private void stubTransfer(Account src, Account dst, Long caller) {
        when(accounts.findByAccountNumber(src.getAccountNumber())).thenReturn(Optional.of(src));
        when(accounts.findByAccountNumber(dst.getAccountNumber())).thenReturn(Optional.of(dst));
        // Locked reads: called with each id
        lenient().when(accounts.findByIdForUpdate(src.getId())).thenReturn(Optional.of(src));
        lenient().when(accounts.findByIdForUpdate(dst.getId())).thenReturn(Optional.of(dst));
        when(accounts.findOwnerUserId(src.getId())).thenReturn(Optional.of(caller));
    }

    @Test
    void transfer_success() {
        Account src = account(1L, "1000000001", "1000.00");
        Account dst = account(2L, "1000000002", "100.00");
        stubTransfer(src, dst, 7L);

        var res = transferService.transfer(7L, request("1000000001", "1000000002", "250.00"));

        assertEquals(new BigDecimal("750.00"), res.getSourceBalanceAfter());
        assertEquals("1000000001", res.getSourceAccountNumber());
        verify(accounts).updateBalance(eq(1L), eq(new BigDecimal("750.00")));
        verify(accounts).updateBalance(eq(2L), eq(new BigDecimal("350.00")));
        // Both legs of the transfer must be recorded atomically.
        verify(transactions, times(2)).save(any());
    }

    @Test
    void transfer_sameAccountRejected() {
        assertThrows(InvalidTransactionException.class, () ->
                transferService.transfer(7L, request("1000000001", "1000000001", "10.00")));
        verify(transactions, never()).save(any());
    }

    @Test
    void transfer_insufficientBalance_rollsBack() {
        Account src = account(1L, "1000000001", "50.00");
        Account dst = account(2L, "1000000002", "100.00");
        stubTransfer(src, dst, 7L);

        assertThrows(InsufficientBalanceException.class, () ->
                transferService.transfer(7L, request("1000000001", "1000000002", "500.00")));

        // Nothing may be debited/credited or recorded when validation fails.
        verify(accounts, never()).updateBalance(anyLong(), any());
        verify(transactions, never()).save(any());
    }

    @Test
    void transfer_unauthorizedSource() {
        Account src = account(1L, "1000000001", "1000.00");
        Account dst = account(2L, "1000000002", "100.00");
        stubTransfer(src, dst, 99L); // owned by someone else

        assertThrows(UnauthorizedAccountAccessException.class, () ->
                transferService.transfer(7L, request("1000000001", "1000000002", "10.00")));
        verify(accounts, never()).updateBalance(anyLong(), any());
    }
}
