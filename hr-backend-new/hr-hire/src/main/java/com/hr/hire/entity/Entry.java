package com.hr.hire.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入职登记表，映射 t_hr_entry。
 */
@Data
@Entity
@Table(name = "t_hr_entry")
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_no", unique = true, nullable = false)
    private String entryNo;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "checklist_json", columnDefinition = "JSON")
    private String checklistJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
