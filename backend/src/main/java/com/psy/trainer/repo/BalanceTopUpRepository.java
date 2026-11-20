package com.psy.trainer.repo;

import com.psy.trainer.domain.BalanceTopUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BalanceTopUpRepository extends JpaRepository<BalanceTopUp, UUID> {

    List<BalanceTopUp> findTop100ByOrderByCreatedAtDesc();

    List<BalanceTopUp> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
}
