package com.example.banking.repository;

import com.example.banking.model.User;
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
public class UserRepository {

    private final JdbcTemplate jdbc;
    private final FlowPublisher flow;

    public UserRepository(JdbcTemplate jdbc, FlowPublisher flow) {
        this.jdbc = jdbc;
        this.flow = flow;
    }

    private final RowMapper<User> rowMapper = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setStatus(rs.getString("status"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        if (created != null) u.setCreatedAt(created.toInstant());
        if (updated != null) u.setUpdatedAt(updated.toInstant());
        return u;
    };

    // SQL: Create user
    public User save(User user) {
        String sql = """
                INSERT INTO users (username, email, password_hash, role, status)
                VALUES (?, ?, ?, ?, ?)
                """;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole());
            ps.setString(5, user.getStatus());
            return ps;
        }, keys);
        Number key = (Number) keys.getKeys().get("id");
        if (key != null) user.setId(key.longValue());
        flow.sql("INSERT INTO users (username, email, password_hash, role, status) VALUES (?, ?, ?, ?, ?)", List.of("users"));
        return user;
    }

    // SQL: Find user by username/email
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        String sql = "SELECT * FROM users WHERE username = ? OR email = ?";
        flow.sql(sql, List.of("users"));
        List<User> list = jdbc.query(sql, rowMapper, usernameOrEmail, usernameOrEmail);
        return list.stream().findFirst();
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        List<User> list = jdbc.query(sql, rowMapper, username);
        return list.stream().findFirst();
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        List<User> list = jdbc.query(sql, rowMapper, email);
        return list.stream().findFirst();
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        List<User> list = jdbc.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }
}
