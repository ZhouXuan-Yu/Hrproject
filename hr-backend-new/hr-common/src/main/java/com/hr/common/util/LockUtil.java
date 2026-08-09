package com.hr.common.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁工具，基于 Redisson。对标 Flask cache_service.lock()。
 * <p>
 * 用法：LockUtil.withLock(key, () -> businessLogic)
 */
@Component
@RequiredArgsConstructor
public class LockUtil {

    private final RedissonClient redissonClient;

    /**
     * 执行带锁逻辑，锁获取失败立即返回 null（快速失败）。
     */
    public <T> T withLock(String key, long leaseSeconds, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock("hrlock:v2:" + key);
        try {
            boolean acquired = lock.tryLock(0, leaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                return null;
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 无返回值版本。
     */
    public void withLock(String key, long leaseSeconds, Runnable runnable) {
        withLock(key, leaseSeconds, () -> {
            runnable.run();
            return true;
        });
    }
}
