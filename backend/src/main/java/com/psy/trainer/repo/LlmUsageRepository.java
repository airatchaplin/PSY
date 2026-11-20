package com.psy.trainer.repo;

import com.psy.trainer.domain.LlmUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LlmUsageRepository extends JpaRepository<LlmUsage, UUID> {

    @Query("""
            select u from LlmUsage u
            where (u.costRub is null or u.costRub = 0)
              and coalesce(u.totalTokens, 0) > 0
            """)
    List<LlmUsage> findMissingCostWithTokens();


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

    @Query(value = """
            select u.model as model,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            where u.therapist_id = :therapistId
              and u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.model
            order by coalesce(sum(u.cost_rub), 0) desc nulls last, count(*) desc
            """, nativeQuery = true)
    List<Object[]> aggregateByModelForTherapist(
            @Param("therapistId") UUID therapistId,
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
            where u.therapist_id = :therapistId
              and u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> aggregateByDayForTherapist(
            @Param("therapistId") UUID therapistId,
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
            where u.therapist_id = :therapistId
              and u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> aggregateByHourForTherapist(
            @Param("therapistId") UUID therapistId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select u.purpose as purpose,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            where u.therapist_id = :therapistId
              and u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.purpose
            order by coalesce(sum(u.cost_rub), 0) desc nulls last, count(*) desc
            """, nativeQuery = true)
    List<Object[]> aggregateByPurposeForTherapist(
            @Param("therapistId") UUID therapistId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select distinct u.model
            from llm_usage u
            where u.therapist_id = :therapistId
              and u.created_at >= :from
              and u.created_at < :to
            order by 1
            """, nativeQuery = true)
    List<String> distinctModelsForTherapist(
            @Param("therapistId") UUID therapistId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query(value = """
            select distinct u.purpose
            from llm_usage u
            where u.therapist_id = :therapistId
              and u.created_at >= :from
              and u.created_at < :to
            order by 1
            """, nativeQuery = true)
    List<String> distinctPurposesForTherapist(
            @Param("therapistId") UUID therapistId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query(value = """
            select u.purpose as purpose,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            where u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.purpose
            order by coalesce(sum(u.cost_rub), 0) desc nulls last, count(*) desc
            """, nativeQuery = true)
    List<Object[]> aggregateByPurposeFiltered(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select u.client_id,
                   coalesce(c.name, 'Общее'),
                   c.age,
                   u.session_id,
                   s.started_at,
                   s.status,
                   exists (
                     select 1 from session_analysis sa
                     where sa.session_id = u.session_id and sa.scope = 'SESSION'
                   ) as has_analysis,
                   u.purpose,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            left join client c on c.id = u.client_id
            left join therapy_session s on s.id = u.session_id
            where u.client_id is not null
              and u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.client_id, c.name, c.age, u.session_id, s.started_at, s.status,
                     exists (
                       select 1 from session_analysis sa
                       where sa.session_id = u.session_id and sa.scope = 'SESSION'
                     ),
                     u.purpose
            order by c.name nulls last, s.started_at nulls last, coalesce(sum(u.cost_rub), 0) desc
            """, nativeQuery = true)
    List<Object[]> aggregateByClientPurposeFiltered(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select u.client_id,
                   coalesce(c.name, 'Общее'),
                   c.age,
                   u.session_id,
                   s.started_at,
                   s.status,
                   exists (
                     select 1 from session_analysis sa
                     where sa.session_id = u.session_id and sa.scope = 'SESSION'
                   ) as has_analysis,
                   u.purpose,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            left join client c on c.id = u.client_id
            left join therapy_session s on s.id = u.session_id
            where u.therapist_id = :therapistId
              and u.client_id is not null
              and u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.client_id, c.name, c.age, u.session_id, s.started_at, s.status,
                     exists (
                       select 1 from session_analysis sa
                       where sa.session_id = u.session_id and sa.scope = 'SESSION'
                     ),
                     u.purpose
            order by c.name nulls last, s.started_at nulls last, coalesce(sum(u.cost_rub), 0) desc
            """, nativeQuery = true)
    List<Object[]> aggregateByClientPurposeForTherapist(
            @Param("therapistId") UUID therapistId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select u.purpose,
                   u.ref_id,
                   case
                     when u.purpose = 'case' then coalesce(max(tc.title), 'Кейс')
                     when u.purpose = 'analysis_global' then 'Анализ тетради'
                     else u.purpose
                   end as label,
                   coalesce(max(tc.created_at), max(sa.created_at), max(u.created_at)) as created_at,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            left join therapy_case tc on u.purpose = 'case' and tc.id = u.ref_id
            left join session_analysis sa on u.purpose = 'analysis_global' and sa.id = u.ref_id
            where u.client_id is null
              and u.purpose in ('analysis_global', 'case', 'embed')
              and u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.purpose, u.ref_id
            order by u.purpose,
                     coalesce(max(tc.created_at), max(sa.created_at), max(u.created_at)) desc nulls last
            """, nativeQuery = true)
    List<Object[]> aggregateGeneralByRefFiltered(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );

    @Query(value = """
            select u.purpose,
                   u.ref_id,
                   case
                     when u.purpose = 'case' then coalesce(max(tc.title), 'Кейс')
                     when u.purpose = 'analysis_global' then 'Анализ тетради'
                     else u.purpose
                   end as label,
                   coalesce(max(tc.created_at), max(sa.created_at), max(u.created_at)) as created_at,
                   count(*) as calls,
                   coalesce(sum(u.total_tokens), 0) as tokens,
                   coalesce(sum(u.cost_rub), 0) as cost_rub
            from llm_usage u
            left join therapy_case tc on u.purpose = 'case' and tc.id = u.ref_id
            left join session_analysis sa on u.purpose = 'analysis_global' and sa.id = u.ref_id
            where u.therapist_id = :therapistId
              and u.client_id is null
              and u.purpose in ('analysis_global', 'case', 'embed')
              and u.created_at >= :from
              and u.created_at < :to
              and (:model = '' or u.model = :model)
              and (:source = '' or u.purpose = :source)
            group by u.purpose, u.ref_id
            order by u.purpose,
                     coalesce(max(tc.created_at), max(sa.created_at), max(u.created_at)) desc nulls last
            """, nativeQuery = true)
    List<Object[]> aggregateGeneralByRefForTherapist(
            @Param("therapistId") UUID therapistId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("model") String model,
            @Param("source") String source
    );
}
