package com.hr.dedup.service;

import com.hr.common.exception.BusinessException;
import com.hr.talent.entity.Candidate;
import com.hr.talent.entity.CandidateTagRel;
import com.hr.talent.entity.RecruitProcess;
import com.hr.talent.repository.CandidateRepository;
import com.hr.talent.repository.CandidateTagRelRepository;
import com.hr.talent.repository.RecruitProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 候选人去重服务，对齐 Flask dedup_service.py。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DedupService {

    private final CandidateRepository candidateRepository;
    private final CandidateTagRelRepository candidateTagRelRepository;
    private final RecruitProcessRepository recruitProcessRepository;

    /**
     * 对手机号数字部分做 SHA-256 哈希，对齐 Flask hash_phone()。
     */
    public static String hashPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(digits.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * POST /api/dedup/check — 按姓名/手机号/邮箱查重。
     */
    public Map<String, Object> checkDuplicates(Map<String, Object> body) {
        String name = str(body, "name");
        String phone = str(body, "phone");
        String email = str(body, "email");

        Set<Long> matchedIds = new java.util.LinkedHashSet<>();
        List<Map<String, Object>> matches = new ArrayList<>();

        // 1. Match by mobile_hash (exact)
        if (!phone.isEmpty()) {
            String phoneHash = hashPhone(phone);
            if (!phoneHash.isEmpty()) {
                List<Candidate> phoneMatches = candidateRepository.findByMobileHashAndIsDeleted(phoneHash, 0);
                for (Candidate c : phoneMatches) {
                    if (matchedIds.add(c.getId())) {
                        matches.add(candidateMatch(c, "phone", "high"));
                    }
                }
            }
        }

        // 2. Match by email (case-insensitive)
        if (!email.isEmpty()) {
            List<Candidate> emailMatches = candidateRepository.findByEmailIgnoreCaseAndIsDeleted(email.toLowerCase(), 0);
            for (Candidate c : emailMatches) {
                if (matchedIds.add(c.getId())) {
                    matches.add(candidateMatch(c, "email", "high"));
                }
            }
        }

        // 3. Match by name (case-insensitive, exact match)
        if (!name.isEmpty()) {
            List<Candidate> nameMatches = candidateRepository.findByCandidateNameIgnoreCaseAndIsDeleted(name.toLowerCase(), 0);
            for (Candidate c : nameMatches) {
                if (matchedIds.add(c.getId())) {
                    matches.add(candidateMatch(c, "name", "medium"));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("has_duplicates", !matches.isEmpty());
        result.put("candidates", matches);
        return result;
    }

    /**
     * GET /api/dedup/scan — 全局扫描重复候选人组。
     */
    public Map<String, Object> scanDuplicates() {
        List<Map<String, Object>> groups = new ArrayList<>();

        // 1. Group by mobile_hash (non-null, non-empty, count > 1)
        List<Object[]> phoneRows = candidateRepository.findDuplicateMobileHashGroups();
        for (Object[] row : phoneRows) {
            String hash = (String) row[0];
            List<Candidate> candidates = candidateRepository.findByMobileHashAndIsDeleted(hash, 0);
            groups.add(buildGroup("phone", hash, candidates));
        }

        // 2. Group by email (case-insensitive, non-null, non-empty, count > 1)
        List<Object[]> emailRows = candidateRepository.findDuplicateEmailGroups();
        for (Object[] row : emailRows) {
            String emailLower = (String) row[0];
            List<Candidate> candidates = candidateRepository.findByEmailIgnoreCaseAndIsDeleted(emailLower, 0);
            groups.add(buildGroup("email", emailLower, candidates));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_groups", groups.size());
        result.put("groups", groups);
        return result;
    }

    /**
     * POST /api/dedup/merge — 合并重复候选人。
     */
    @Transactional
    public Map<String, Object> mergeCandidates(Map<String, Object> body) {
        Object primaryIdObj = body.get("primary_id");
        if (primaryIdObj == null) {
            throw BusinessException.invalidInput("缺少 primary_id 参数");
        }
        Long primaryId = toLong(primaryIdObj);

        @SuppressWarnings("unchecked")
        List<Object> duplicateIdsRaw = (List<Object>) body.get("duplicate_ids");
        if (duplicateIdsRaw == null || duplicateIdsRaw.isEmpty()) {
            throw BusinessException.invalidInput("缺少 duplicate_ids 参数或格式错误");
        }
        List<Long> duplicateIds = duplicateIdsRaw.stream().map(this::toLong).collect(Collectors.toList());

        // 校验 primary 存在
        Candidate primary = candidateRepository.findById(primaryId)
                .filter(c -> c.getIsDeleted() == null || c.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("主候选人不存在"));

        // 查询 duplicates
        List<Candidate> duplicates = candidateRepository.findAllById(duplicateIds).stream()
                .filter(c -> c.getIsDeleted() == null || c.getIsDeleted() == 0)
                .collect(Collectors.toList());

        Set<Long> foundIds = duplicates.stream().map(Candidate::getId).collect(Collectors.toSet());
        List<String> missing = duplicateIds.stream()
                .filter(did -> !foundIds.contains(did))
                .map(String::valueOf)
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            log.warn("Some duplicate IDs not found or already deleted: {}", missing);
        }

        List<String> mergedNotes = new ArrayList<>();
        List<Long> mergedTags = new ArrayList<>();
        int updatedProcessCount = 0;

        for (Candidate dup : duplicates) {
            // Copy note (append)
            if (dup.getNote() != null && !dup.getNote().isEmpty()) {
                if (primary.getNote() != null && !primary.getNote().isEmpty()) {
                    primary.setNote(primary.getNote() + "\n---\n" + dup.getNote());
                } else {
                    primary.setNote(dup.getNote());
                }
                mergedNotes.add(dup.getCandidateNo());
            }

            // Copy tags from duplicate to primary
            List<CandidateTagRel> dupTags = candidateTagRelRepository.findByCandidateId(dup.getId());
            for (CandidateTagRel tagRel : dupTags) {
                List<CandidateTagRel> existing = candidateTagRelRepository
                        .findByCandidateIdAndTagId(primaryId, tagRel.getTagId());
                if (existing.isEmpty()) {
                    CandidateTagRel newRel = new CandidateTagRel();
                    newRel.setCandidateId(primaryId);
                    newRel.setTagId(tagRel.getTagId());
                    newRel.setTagSource(tagRel.getTagSource());
                    newRel.setValidEnd(tagRel.getValidEnd());
                    newRel.setCreatedAt(LocalDateTime.now());
                    newRel.setIsDeleted(0);
                    candidateTagRelRepository.save(newRel);
                    mergedTags.add(tagRel.getTagId());
                }
            }

            // Update RecruitProcess records → point to primary
            List<RecruitProcess> processes = recruitProcessRepository.findByCandidateId(dup.getId());
            for (RecruitProcess proc : processes) {
                proc.setCandidateId(primaryId);
                proc.setUpdatedAt(LocalDateTime.now());
                recruitProcessRepository.save(proc);
                updatedProcessCount++;
            }

            // Soft-delete the duplicate
            dup.setIsDeleted(1);
            dup.setUpdatedAt(LocalDateTime.now());
            candidateRepository.save(dup);
        }

        candidateRepository.save(primary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("primary_id", primaryId);
        result.put("primary_no", primary.getCandidateNo());
        result.put("duplicates_merged", duplicates.size());
        result.put("notes_copied", mergedNotes.size());
        result.put("tags_copied", (long) new java.util.HashSet<>(mergedTags).size());
        result.put("process_updated", updatedProcessCount);
        result.put("missing_ids", missing);
        return result;
    }

    // --- helpers ---

    private Map<String, Object> candidateMatch(Candidate c, String reason, String score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("candidate_no", c.getCandidateNo());
        m.put("name", c.getCandidateName());
        m.put("match_reason", reason);
        m.put("match_score", score);
        return m;
    }

    private Map<String, Object> candidateBrief(Candidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("candidate_no", c.getCandidateNo());
        m.put("name", c.getCandidateName());
        m.put("mobile", c.getMobile());
        m.put("email", c.getEmail());
        m.put("status", c.getStatus());
        m.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> buildGroup(String reason, String key, List<Candidate> candidates) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("reason", reason);
        group.put("hash_or_email", key);
        group.put("candidates", candidates.stream().map(this::candidateBrief).collect(Collectors.toList()));
        return group;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private static Long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(v.toString());
    }
}
