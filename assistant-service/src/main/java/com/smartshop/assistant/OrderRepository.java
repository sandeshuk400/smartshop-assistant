package com.smartshop.assistant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PreDestroy;

@Component
public class OrderRepository {

    public record Order(long id, long customerId, String status,
                        BigDecimal totalAmount, OffsetDateTime createdAt) {
    }

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;

    public OrderRepository(@Value("${orders.datasource.url}") String url,
                           @Value("${orders.datasource.username}") String username,
                           @Value("${orders.datasource.password}") String password) {
        this.dataSource = new HikariDataSource();
        this.dataSource.setJdbcUrl(url);
        this.dataSource.setUsername(username);
        this.dataSource.setPassword(password);
        this.dataSource.setMaximumPoolSize(3);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** Returns the order only if the order, customer and bill code all match. */
    public Optional<Order> findByIdCustomerAndCode(long orderId, long customerId, String code) {
        List<Order> rows = jdbc.query(
                "SELECT id, customer_id, status, total_amount, created_at "
                        + "FROM orders WHERE id = ? AND customer_id = ? AND bill_code = ?",
                (rs, i) -> new Order(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_amount"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                orderId, customerId, code);
        return rows.stream().findFirst();
    }

    @PreDestroy
    void close() {
        dataSource.close();
    }
}