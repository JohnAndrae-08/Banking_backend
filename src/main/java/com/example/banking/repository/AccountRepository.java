package com.example.banking.repository;

import com.example.banking.model.Account;
import com.example.banking.monitor.FlowPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
@Repository
public class AccountRepository {

    private final JdbcTemplate jdbc;
    private final FlowPublisher flow;

    public AccountRepository(JdbcTemplate jdbc, FlowPublisher flow) {
        this.jdbc = jdbc;
        this.flow = flow;
    }

    private final RowMapper<Account> rowMapper = (rs, rowNum) -> {
        Account a = new Account();
        a.setId(rs.getLong("id"));
        a.setCustomerId(rs.getLong("customer_id"));
        a.setAccountNumber(rs.getString("account_number"));
        a.setAccountType(rs.getString("account_type"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setStatus(rs.getString("status"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        if (created != null) a.setCreatedAt(created.toInstant());
        if (updated != null) a.setUpdatedAt(updated.toInstant());
        return a;
    };

    // SQL: Create account
    public Account save(Account account) {
        String sql = """
                INSERT INTO accounts (customer_id, account_number, account_type, balance, status)
                VALUES (?, ?, ?, ?, ?)
                """;        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, account.getCustomerId());
            ps.setString(2, account.getAccountNumber());
            ps.setString(3, account.getAccountType());
            ps.setBigDecimal(4, account.getBalance());
            ps.setString(5, account.getStatus());
            return ps;
        }, keys);
        Number key = (Number) keys.getKeys().get("id");
        if (key != null) account.setId(key.longValue());
        flow.sql("INSERT INTO accounts (customer_id, account_number, account_type, balance, status) VALUES (?, ?, ?, ?, ?)", List.of("accounts"));
        return account;
    }

    // SQL: Find account by id
    public Optional<Account> findById(Long id) {
        String sql = "SELECT * FROM accounts WHERE id = ?";
        flow.sql(sql, List.of("accounts"));
        List<Account> list = jdbc.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        flow.sql(sql, List.of("accounts"));
        List<Account> list = jdbc.query(sql, rowMapper, accountNumber);
        return list.stream().findFirst();
    }

    public boolean existsByAccountNumber(String accountNumber) {
        String sql = "SELECT COUNT(*) FROM accounts WHERE account_number = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, accountNumber);
        return count != null && count > 0;
    }

    public List<Account> findByCustomerId(Long customerId) {
        String sql = "SELECT * FROM accounts WHERE customer_id = ? ORDER BY created_at DESC";
        return jdbc.query(sql, rowMapper, customerId);
    }

    // SQL: Lock account row for update (concurrency control for transfers)
    public Optional<Account> findByIdForUpdate(Long id) {
        String sql = "SELECT * FROM accounts WHERE id = ? FOR UPDATE";
        flow.sql(sql, List.of("accounts"));
        List<Account> list = jdbc.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public Optional<Account> findByAccountNumberForUpdate(String accountNumber) {
        String sql = "SELECT * FROM accounts WHERE account_number = ? FOR UPDATE";
        flow.sql(sql, List.of("accounts"));
        List<Account> list = jdbc.query(sql, rowMapper, accountNumber);
        return list.stream().findFirst();
    }

    // SQL: Check balance
    public Optional<BigDecimal> findBalanceById(Long id) {
        String sql = "SELECT balance FROM accounts WHERE id = ?";
        List<BigDecimal> list = jdbc.query(sql, (rs, rn) -> rs.getBigDecimal("balance"), id);
        return list.stream().findFirst();
    }

    // SQL: Deposit (atomic increment)
    public int depositById(Long id, BigDecimal amount) {
        String sql = "UPDATE accounts SET balance = balance + ?, updated_at = NOW() WHERE id = ? AND status = 'ACTIVE'";
        flow.sql(sql, List.of("accounts"));
        int rows = jdbc.update(sql, amount, id);
        flow.database("Deposit UPDATE affected " + rows + " row(s)");
        return rows;
    }

    // SQL: Withdrawal with negative-balance guard (atomic conditional update)
    public int withdrawById(Long id, BigDecimal amount) {
        String sql = """
                UPDATE accounts
                SET balance = balance - ?, updated_at = NOW()
                WHERE id = ? AND status = 'ACTIVE' AND balance >= ?
                """;
        flow.sql("UPDATE accounts SET balance = balance - ?, updated_at = NOW() WHERE id = ? AND status = 'ACTIVE' AND balance >= ?", List.of("accounts"));
        int rows = jdbc.update(sql, amount, id, amount);
        flow.database("Withdrawal UPDATE affected " + rows + " row(s)");
        return rows;
    }

    // SQL: Generic balance set (used after FOR UPDATE locking in transfers)
    public int updateBalance(Long id, BigDecimal newBalance) {
        String sql = "UPDATE accounts SET balance = ?, updated_at = NOW() WHERE id = ?";
        flow.sql(sql, List.of("accounts"));
        int rows = jdbc.update(sql, newBalance, id);
        flow.database("Balance UPDATE affected " + rows + " row(s)");
        return rows;
    }

    // SQL: Resolve owner user id of an account (for authorization checks)
    public Optional<Long> findOwnerUserId(Long accountId) {
        String sql = """
                SELECT c.user_id FROM accounts a
                JOIN customers c ON c.id = a.customer_id
                WHERE a.id = ?
                """;
        List<Long> list = jdbc.query(sql, (rs, rn) -> rs.getLong("user_id"), accountId);
        return list.stream().findFirst();
    }

    public Optional<Long> findOwnerUserIdByAccountNumber(String accountNumber) {
        String sql = """
                SELECT c.user_id FROM accounts a
                JOIN customers c ON c.id = a.customer_id
                WHERE a.account_number = ?
                """;
        List<Long> list = jdbc.query(sql, (rs, rn) -> rs.getLong("user_id"), accountNumber);
        return list.stream().findFirst();
    }
}
