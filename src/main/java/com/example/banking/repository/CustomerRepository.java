package com.example.banking.repository;

import com.example.banking.model.Customer;
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
public class CustomerRepository {

    private final JdbcTemplate jdbc;
    private final FlowPublisher flow;

    public CustomerRepository(JdbcTemplate jdbc, FlowPublisher flow) {
        this.jdbc = jdbc;
        this.flow = flow;
    }

    private final RowMapper<Customer> rowMapper = (rs, rowNum) -> {
        Customer c = new Customer();
        c.setId(rs.getLong("id"));
        c.setUserId(rs.getLong("user_id"));
        c.setFirstName(rs.getString("first_name"));
        c.setMiddleName(rs.getString("middle_name"));
        c.setLastName(rs.getString("last_name"));
        c.setPhone(rs.getString("phone"));
        c.setAddress(rs.getString("address"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        if (created != null) c.setCreatedAt(created.toInstant());
        if (updated != null) c.setUpdatedAt(updated.toInstant());
        return c;
    };

    // SQL: Create customer
    public Customer save(Customer customer) {
        String sql = """
                INSERT INTO customers (user_id, first_name, middle_name, last_name, phone, address)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, customer.getUserId());
            ps.setString(2, customer.getFirstName());
            ps.setString(3, customer.getMiddleName());
            ps.setString(4, customer.getLastName());
            ps.setString(5, customer.getPhone());
            ps.setString(6, customer.getAddress());
            return ps;
        }, keys);
        Number key = (Number) keys.getKeys().get("id");
        if (key != null) customer.setId(key.longValue());
        flow.sql("INSERT INTO customers (user_id, first_name, middle_name, last_name, phone, address) VALUES (?, ?, ?, ?, ?, ?)", List.of("customers"));
        return customer;
    }

    // SQL: Find customer by user
    public Optional<Customer> findByUserId(Long userId) {
        String sql = "SELECT * FROM customers WHERE user_id = ?";
        flow.sql(sql, List.of("customers"));
        List<Customer> list = jdbc.query(sql, rowMapper, userId);
        return list.stream().findFirst();
    }

    public Optional<Customer> findById(Long id) {
        String sql = "SELECT * FROM customers WHERE id = ?";
        List<Customer> list = jdbc.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    // SQL: Update customer profile
    public int update(Customer customer) {
        String sql = """
                UPDATE customers
                SET first_name = ?, middle_name = ?, last_name = ?, phone = ?, address = ?, updated_at = NOW()
                WHERE id = ?
                """;
        return jdbc.update(sql,
                customer.getFirstName(), customer.getMiddleName(), customer.getLastName(),
                customer.getPhone(), customer.getAddress(), customer.getId());
    }
}
