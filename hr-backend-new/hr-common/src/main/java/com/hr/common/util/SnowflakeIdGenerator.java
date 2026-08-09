package com.hr.common.util;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;

/**
 * 雪花 ID 生成器，用于业务单号（需求单号、Offer 单号等）。
 */
public final class SnowflakeIdGenerator {
    private static final long EPOCH = 1700000000000L; // 2023-11-14
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator();
    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    private SnowflakeIdGenerator() {
        this.datacenterId = 0L;
        this.workerId = resolveWorkerId();
    }

    private long resolveWorkerId() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            long candidate = 0;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    for (byte b : mac) {
                        candidate += (b & 0xFF);
                    }
                }
            }
            return candidate % (MAX_WORKER_ID + 1);
        } catch (Exception e) {
            return Math.abs(new SecureRandom().nextInt()) % (MAX_WORKER_ID + 1);
        }
    }

    public static long nextId() {
        return INSTANCE.generate();
    }

    public static String nextIdStr() {
        return String.valueOf(nextId());
    }

    public static String nextBizNo(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return prefix + datePart + String.format("%010d", nextId() % 10_000_000_000L);
    }

    private synchronized long generate() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            timestamp = lastTimestamp;
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTimestamp) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
