-- Composite indexes for the current paginated lists, logical-delete filters and lookup paths.
-- Verify with EXPLAIN on production-sized data before adding further indexes.
ALTER TABLE `t_core_user`
  ADD KEY `ix_t_core_user_active_role` (`status`, `is_deleted`, `role_code`, `user_id`),
  ADD KEY `ix_t_core_user_active_dept` (`dept_id`, `status`, `is_deleted`, `user_id`);

ALTER TABLE `t_hr_candidate`
  ADD KEY `ix_t_hr_candidate_active_status` (`is_deleted`, `status`, `id`),
  ADD KEY `ix_t_hr_candidate_active_source` (`is_deleted`, `source_channel`, `id`);

ALTER TABLE `t_hr_resume`
  ADD KEY `ix_t_hr_resume_candidate_active_time` (`candidate_id`, `is_deleted`, `storage_time`, `id`);

ALTER TABLE `t_hr_resume_match`
  ADD KEY `ix_t_hr_resume_match_lookup` (`resume_id`, `demand_id`, `is_deleted`, `calculate_time`);

ALTER TABLE `t_hr_recruit_process`
  ADD KEY `ix_t_hr_recruit_process_demand_active` (`demand_id`, `is_deleted`, `id`),
  ADD KEY `ix_t_hr_recruit_process_candidate_demand` (`candidate_id`, `demand_id`, `is_deleted`, `process_status`);

ALTER TABLE `t_hr_recruit_demand`
  ADD KEY `ix_t_hr_recruit_demand_active_status` (`is_deleted`, `demand_status`, `id`),
  ADD KEY `ix_t_hr_recruit_demand_active_dept_status` (`dept_id`, `is_deleted`, `demand_status`, `id`);

ALTER TABLE `t_hr_interview_slot`
  ADD KEY `ix_t_hr_interview_slot_interviewer_active_time` (`interviewer_id`, `is_deleted`, `start_dt`);

ALTER TABLE `t_hr_interview_book`
  ADD KEY `ix_t_hr_interview_book_demand_active_time` (`demand_id`, `is_deleted`, `book_time`);

ALTER TABLE `t_hr_password_reset`
  ADD KEY `ix_t_hr_password_reset_target_pending` (`target`, `status`, `is_deleted`, `expires_at`);
