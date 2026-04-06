package carpark.stats;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe accumulator for simulation statistics.
 */
public class SimulationStats {

    private final AtomicInteger totalProduced = new AtomicInteger(0);
    private final AtomicInteger totalConsumed = new AtomicInteger(0);
    private final AtomicLong totalProducerWaitNanos = new AtomicLong(0);
    private final AtomicLong totalConsumerWaitNanos = new AtomicLong(0);
    private final AtomicInteger producerWaitSamples = new AtomicInteger(0);
    private final AtomicInteger consumerWaitSamples = new AtomicInteger(0);

    private volatile long startTimeNanos = System.nanoTime();

    public void recordProduced(long producerWaitNanos) {
        totalProduced.incrementAndGet();
        addWaitSample(totalProducerWaitNanos, producerWaitSamples, producerWaitNanos);
    }

    public void recordConsumed(long consumerWaitNanos) {
        totalConsumed.incrementAndGet();
        addWaitSample(totalConsumerWaitNanos, consumerWaitSamples, consumerWaitNanos);
    }

    private void addWaitSample(AtomicLong totalWaitNanos, AtomicInteger samples, long waitNanos) {
        totalWaitNanos.addAndGet(Math.max(0L, waitNanos));
        samples.incrementAndGet();
    }

    public double getThroughput() {
        double elapsedSec = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
        return elapsedSec > 0 ? totalConsumed.get() / elapsedSec : 0.0;
    }

    public double getAverageWaitTimeMs() {
        long totalWaitNanos = totalProducerWaitNanos.get() + totalConsumerWaitNanos.get();
        int samples = producerWaitSamples.get() + consumerWaitSamples.get();
        return samples > 0 ? totalWaitNanos / 1_000_000.0 / samples : 0.0;
    }

    public double getAverageProducerWaitTimeMs() {
        int samples = producerWaitSamples.get();
        return samples > 0 ? totalProducerWaitNanos.get() / 1_000_000.0 / samples : 0.0;
    }

    public double getAverageConsumerWaitTimeMs() {
        int samples = consumerWaitSamples.get();
        return samples > 0 ? totalConsumerWaitNanos.get() / 1_000_000.0 / samples : 0.0;
    }

    public int getTotalProduced() { return totalProduced.get(); }

    public int getTotalConsumed() { return totalConsumed.get(); }

    public void reset() {
        totalProduced.set(0);
        totalConsumed.set(0);
        totalProducerWaitNanos.set(0);
        totalConsumerWaitNanos.set(0);
        producerWaitSamples.set(0);
        consumerWaitSamples.set(0);
        startTimeNanos = System.nanoTime();
    }
}
