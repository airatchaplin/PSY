package com.psy.trainer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "balance_topup")
public class BalanceTopUp {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "amount_rub", nullable = false, precision = 14, scale = 4)
    private BigDecimal amountRub;

    @Column(name = "balance_after_rub", nullable = false, precision = 14, scale = 4)
    private BigDecimal balanceAfterRub;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_by_username", length = 64)
    private String createdByUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BalanceTopUp() {
    }

    public static BalanceTopUp of(
            AppUser user,
            BigDecimal amountRub,
            BigDecimal balanceAfterRub,
            AppUser createdBy
    ) {
        BalanceTopUp row = new BalanceTopUp();
        row.id = UUID.randomUUID();
        row.userId = user.getId();
        row.username = user.getUsername();
        row.displayName = user.getDisplayName();
        row.amountRub = amountRub;
        row.balanceAfterRub = balanceAfterRub;
        if (createdBy != null) {
            row.createdByUserId = createdBy.getId();
            row.createdByUsername = createdBy.getUsername();
        }
        row.createdAt = Instant.now();
        return row;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getAmountRub() {
        return amountRub;
    }

    public BigDecimal getBalanceAfterRub() {
        return balanceAfterRub;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
