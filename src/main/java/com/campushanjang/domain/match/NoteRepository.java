package com.campushanjang.domain.match;

import com.campushanjang.domain.match.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    @Query("SELECT n FROM Note n JOIN FETCH n.selector WHERE n.selected.id = :selectedId ORDER BY n.createdAt DESC")
    List<Note> findBySelectedIdWithSelector(@Param("selectedId") UUID selectedId);

    @Query("SELECT n FROM Note n JOIN FETCH n.selected WHERE n.selector.id = :selectorId ORDER BY n.createdAt DESC")
    List<Note> findBySelectorIdWithSelected(@Param("selectorId") UUID selectorId);

    @Query("SELECT n FROM Note n JOIN FETCH n.selector JOIN FETCH n.selected WHERE n.id = :noteId")
    Optional<Note> findByIdWithAll(@Param("noteId") UUID noteId);

    boolean existsBySelectorIdAndSelectedId(UUID selectorId, UUID selectedId);
}
