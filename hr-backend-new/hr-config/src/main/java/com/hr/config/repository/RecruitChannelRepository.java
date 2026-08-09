package com.hr.config.repository;

import com.hr.config.entity.RecruitChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecruitChannelRepository extends JpaRepository<RecruitChannel, Long> {

    List<RecruitChannel> findByIsDeletedOrderByIdAsc(Integer isDeleted);

    Optional<RecruitChannel> findByChannelNameAndIsDeleted(String channelName, Integer isDeleted);
}
