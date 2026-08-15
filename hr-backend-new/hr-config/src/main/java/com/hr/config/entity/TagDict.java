package com.hr.config.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签字典表，映射 t_hr_tag_dict。
 */
@Data
@Entity
@Table(name = "t_hr_tag_dict")
public class TagDict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_name", nullable = false, length = 64)
    private String tagName;

    @Column(name = "tag_category", nullable = false, length = 32)
    private String tagCategory;

    @Column(name = "tag_group", length = 32)
    private String tagGroup;

    @Column(name = "sort_num")
    private Integer sortNum;

    @Column(name = "status")
    private Integer status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
