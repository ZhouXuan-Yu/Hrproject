package com.hr.hire.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 录用事件表，映射 t_hr_hire_event。
 * hire_type: 1外部Offer录用 2内部调岗 3离职返聘
 * event_status: 0待办理 1已生成入职单 2作废
 */
@Data
@Entity
@Table(name = "t_hr_hire_event")
public class HireEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", unique = true, nullable = false)
    private String eventNo;

    @Column(name = "process_id")
    private Long processId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "offer_id")
    private Long offerId;

    @Column(name = "hire_type", nullable = false)
    private Integer hireType;

    @Column(name = "event_status", nullable = false)
    private Integer eventStatus;

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
