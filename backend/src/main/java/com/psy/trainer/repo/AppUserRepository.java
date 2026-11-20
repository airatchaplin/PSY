package com.psy.trainer.repo;

import com.psy.trainer.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AppUser u set u.balanceRub = u.balanceRub - :amount where u.id = :id")
    int deductBalance(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    @Query("select coalesce(sum(u.balanceRub), 0) from AppUser u")
    BigDecimal sumBalanceRub();
}
