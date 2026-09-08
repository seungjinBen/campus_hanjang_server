package com.campushanjang.domain.match;

import com.campushanjang.domain.match.entity.Selection;
import com.campushanjang.domain.match.entity.enums.SelectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SelectionRepository extends JpaRepository<Selection, UUID> {

    @Query("SELECT s FROM Selection s JOIN FETCH s.selector WHERE s.selected.id = :selectedId AND s.type = :type ORDER BY s.createdAt DESC")
    List<Selection> findBySelectedIdAndType(
            @Param("selectedId") UUID selectedId,
            @Param("type") SelectionType type
    );

    boolean existsBySelectorIdAndSelectedId(UUID selectorId, UUID selectedId);

    Optional<Selection> findFirstBySelectorIdAndSelectedIdOrderByCreatedAtDesc(UUID selectorId, UUID selectedId);

    boolean existsBySelectorIdAndSelectedIdAndType(UUID selectorId, UUID selectedId, SelectionType type);
}
