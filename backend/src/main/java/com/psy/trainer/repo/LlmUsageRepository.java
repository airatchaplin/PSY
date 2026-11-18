package com.psy.trainer.repo;

import com.psy.trainer.domain.LlmUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LlmUsageRepository extends JpaRepository<LlmUsage, UUID> {

    @Query(value = """
            select u.model as model,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            where u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.model
            order by coalesce(sum(u.cost_rub), 0) desc nulls last, count(*) desc
            """, nativeQuery = true)
    List<Object[]> aggregateByModelFiltered(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select to_char(date_trunc('day', u.created_at), 'YYYY-MM-DD') as bucket,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            where u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> aggregateByDayFiltered(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select to_char(date_trunc('hour', u.created_at), 'YYYY-MM-DD HH24:00') as bucket,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            where u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> aggregateByHourFiltered(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select u.therapist_id,
                   coalesce(t.display_name, 'Неизвестный'),
                   t.email,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            left join therapist_profile t on t.id = u.therapist_id
            where u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.therapist_id, t.display_name, t.email
            order by coalesce(sum(u.cost_rub), 0) desc nulls last, count(*) desc
            """, nativeQuery = true)
    List<Object[]> aggregateByTherapistFiltered(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select distinct u.model
            from llm_usage u
            where u.created_at >= :from
              and u.created_at < :to
            order by 1
            """, nativeQuery = true)
    List<String> distinctModels(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            select distinct u.purpose
            from llm_usage u
            where u.created_at >= :from
              and u.created_at < :to
            order by 1
            """, nativeQuery = true)
    List<String> distinctPurposes(@Param("from") Instant from, @Param("to") Instant to);
}
