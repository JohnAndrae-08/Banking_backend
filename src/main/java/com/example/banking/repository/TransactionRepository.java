package com.example.banking.repository;

import com.example.banking.model.BankTransaction;
import com.example.banking.monitor.FlowPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbc;
    private final FlowPublisher flow;

    public TransactionRepository(JdbcTemplate jdbc, FlowPublisher flow) {
        this.jdbc = jdbc;
        this.flow = flow;
    }

    private final RowMapper<BankTransaction> rowMapper = (rs, rowNum) -> {
        BankTransaction t = new BankTransaction();
        t.setId(rs.getLong("id"));
        t.setReferenceNumber(rs.getString("reference_number"));
        t.setAccountId(rs.getLong("account_id"));
        long related = rs.getLong("related_account_id");
        t.setRelatedAccountId(rs.wasNull() ? null : related);
        t.setTransactionType(rs.getString("transaction_type"));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setBalanceBefore(rs.getBigDecimal("balance_before"));
        t.setBalanceAfter(rs.getBigDecimal("balance_after"));
        t.setDescription(rs.getString("description"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) t.setCreatedAt(created.toInstant());
        return t;
    };

    // SQL: Insert transaction record (immutable audit row)
    public BankTransaction save(BankTransaction tx) {
        String sql = """
                INSERT INTO transactions
                    (reference_number, account_id, related_account_id, transaction_type,
                     amount, balance_before, balance_after, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, tx.getReferenceNumber());
            ps.setLong(2, tx.getAccountId());
            if (tx.getRelatedAccountId() == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, tx.getRelatedAccountId());
            }
            ps.setString(4, tx.getTransactionType());
            ps.setBigDecimal(5, tx.getAmount());
            ps.setBigDecimal(6, tx.getBalanceBefore());
            ps.setBigDecimal(7, tx.getBalanceAfter());
            ps.setString(8, tx.getDescription());
            return ps;
        }, keys);
        Number key = (Number) keys.getKeys().get("id");
        if (key != null) tx.setId(key.longValue());
        flow.sql("INSERT INTO transactions (reference_number, account_id, related_account_id, transaction_type, amount, balance_before, balance_after, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", List.of("transactions"));
        return tx;
    }

    // SQL: Retrieve transaction history (paginated, newest first)
    public List<BankTransaction> findByAccountId(Long accountId, int limit, int offset) {
        String sql = """
                SELECT * FROM transactions
                WHERE account_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;
        flow.sql("SELECT * FROM transactions WHERE account_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", List.of("transactions"));
        return jdbc.query(sql, rowMapper, accountId, limit, offset);
    }

    public int countByAccountId(Long accountId) {
        String sql = "SELECT COUNT(*) FROM transactions WHERE account_id = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, accountId);
        return count == null ? 0 : count;
    }

    public Optional<BankTransaction> findById(Long id) {
        String sql = "SELECT * FROM transactions WHERE id = ?";
        flow.sql(sql, List.of("transactions"));
        List<BankTransaction> list = jdbc.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }
}
