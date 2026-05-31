package io.docpilot.common.id;

import java.util.concurrent.locks.ReentrantLock;

/**
 * MoonPX-style Snowflake id generator.
 */
public class SnowflakeIdGenerator {

    /**
     * MoonPX epoch: 2024-06-28 12:00:00 UTC.
     */
    public static final long START_TIMESTAMP = 1719547200000L;

    private static final long SEQUENCE_BIT = 12L;

    private static final long MACHINE_BIT = 5L;

    private static final long DATACENTER_BIT = 5L;

    private static final long MAX_DATACENTER_NUM = ~(-1L << DATACENTER_BIT);

    private static final long MAX_MACHINE_NUM = ~(-1L << MACHINE_BIT);

    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

    private static final long MACHINE_LEFT = SEQUENCE_BIT;

    private static final long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;

    private static final long TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    /**
     * Datacenter id, range 0..31.
     */
    private final long datacenterId;

    /**
     * Machine id, range 0..31.
     */
    private final long machineId;

    /**
     * Guards sequence updates inside one JVM.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Sequence number inside the same millisecond.
     */
    private long sequence = 0L;

    /**
     * Last timestamp used to generate an id.
     */
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long datacenterId, long machineId) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_NUM) {
            throw new IllegalArgumentException("datacenterId must be between 0 and " + MAX_DATACENTER_NUM);
        }
        if (machineId < 0 || machineId > MAX_MACHINE_NUM) {
            throw new IllegalArgumentException("machineId must be between 0 and " + MAX_MACHINE_NUM);
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    public long nextId() {
        lock.lock();
        try {
            long currentTimestamp = currentTimestamp();
            if (currentTimestamp < lastTimestamp) {
                long offset = lastTimestamp - currentTimestamp;
                if (offset <= 5L) {
                    sleep(offset);
                    currentTimestamp = currentTimestamp();
                } else {
                    throw new IllegalStateException("Clock moved backwards by " + offset + "ms");
                }
            }

            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1L) & MAX_SEQUENCE;
                if (sequence == 0L) {
                    currentTimestamp = nextMillisecond(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = currentTimestamp;
            return (currentTimestamp - START_TIMESTAMP) << TIMESTAMP_LEFT
                    | datacenterId << DATACENTER_LEFT
                    | machineId << MACHINE_LEFT
                    | sequence;
        } finally {
            lock.unlock();
        }
    }

    private long currentTimestamp() {
        return System.currentTimeMillis();
    }

    private long nextMillisecond(long previousTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= previousTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
