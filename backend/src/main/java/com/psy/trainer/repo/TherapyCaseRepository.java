package com.psy.trainer.repo;

import com.psy.trainer.domain.CaseSource;
import com.psy.trainer.domain.TherapyCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TherapyCaseRepository extends JpaRepository<TherapyCaseEntity, UUID> {

    List<TherapyCaseEntity> findAllByOrderByCreatedAtDesc();

    List<TherapyCaseEntity> findBySourceOrderByCreatedAtDesc(CaseSource source);

    Optional<TherapyCaseEntity> findBySlug(String slug);

    Optional<TherapyCaseEntity> findByShareToken(String shareToken);

    @Query("""
            select c from TherapyCaseEntity c
            where c.source = com.psy.trainer.domain.CaseSource.BUILTIN
               or c.createdBy = :therapistId
            order by
              case when c.source = com.psy.trainer.domain.CaseSource.BUILTIN then 0 else 1 end,
              c.createdAt desc
            """)
    List<TherapyCaseEntity> findVisibleFor(@Param("therapistId") UUID therapistId);

    boolean existsByOriginCaseIdAndCreatedBy(UUID originCaseId, UUID createdBy);

    @Query("""
            select case when count(c) > 0 then true else false end
            from TherapyCaseEntity c
            where c.id = :caseId
              and (c.source = com.psy.trainer.domain.CaseSource.BUILTIN or c.createdBy = :therapistId)
            """)
    boolean isAccessible(@Param("caseId") UUID caseId, @Param("therapistId") UUID therapistId);
}
