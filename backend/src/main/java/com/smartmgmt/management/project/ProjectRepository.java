package com.smartmgmt.management.project;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository
        extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    @Override
    @EntityGraph(attributePaths = {"customer", "owner"})
    java.util.Optional<Project> findById(UUID id);

    @EntityGraph(attributePaths = {"customer", "owner"})
    List<Project> findByNameIgnoreCase(String name);

    @Query("""
            select count(p) from Project p
             where p.dueDate is not null and p.dueDate < :today
               and p.status not in :closed
            """)
    long countOverdue(@Param("today") LocalDate today, @Param("closed") Collection<ProjectStatus> closed);

    @Query("""
            select count(p) from Project p
             where p.dueDate is not null and p.dueDate < :today
               and p.status not in :closed
               and p.owner.id = :ownerId
            """)
    long countOverdueForOwner(@Param("today") LocalDate today,
            @Param("closed") Collection<ProjectStatus> closed,
            @Param("ownerId") UUID ownerId);
}
