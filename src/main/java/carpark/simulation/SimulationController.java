package carpark.simulation;

import carpark.model.ParkingLot;
import carpark.stats.SimulationStats;
import carpark.threads.ConsumerThread;
import carpark.threads.ProducerThread;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the entire car park simulation.
 */
public class SimulationController {

    public static final int NUM_PRODUCERS = 2;
    public static final int NUM_CONSUMERS = 3;
    public static final int DEFAULT_CAPACITY = 10;
    public static final int DEFAULT_PROD_RATE_MS = 3000;
    public static final int DEFAULT_CONS_RATE_MS = 15000;
    public static final int DEFAULT_PROC_TIME_MS = 800;

    private volatile ParkingLot parkingLot;
    private final SimulationStats stats;
    private final List<ProducerThread> producers = new ArrayList<>();
    private final List<ConsumerThread> consumers = new ArrayList<>();

    private volatile boolean running = false;
    private int configuredCapacity = DEFAULT_CAPACITY;
    private int configuredProductionRateMs = DEFAULT_PROD_RATE_MS;
    private int configuredConsumptionRateMs = DEFAULT_CONS_RATE_MS;
    private int configuredProcessingTimeMs = DEFAULT_PROC_TIME_MS;

    public SimulationController() {
        stats = new SimulationStats();
        parkingLot = new ParkingLot(configuredCapacity);
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        stats.reset();
        parkingLot.setCapacity(configuredCapacity);
        running = true;

        producers.clear();
        for (int i = 1; i <= NUM_PRODUCERS; i++) {
            ProducerThread producer = new ProducerThread(i, parkingLot, stats, configuredProductionRateMs);
            producers.add(producer);
            producer.start();
        }

        consumers.clear();
        for (int i = 1; i <= NUM_CONSUMERS; i++) {
            ConsumerThread consumer = new ConsumerThread(
                i,
                parkingLot,
                stats,
                configuredConsumptionRateMs,
                configuredProcessingTimeMs
            );
            consumers.add(consumer);
            consumer.start();
        }
    }

    public synchronized void stop() {
        if (!running && producers.isEmpty() && consumers.isEmpty()) {
            return;
        }

        running = false;

        List<ProducerThread> producerSnapshot = new ArrayList<>(producers);
        List<ConsumerThread> consumerSnapshot = new ArrayList<>(consumers);

        producerSnapshot.forEach(ProducerThread::stopProducer);
        consumerSnapshot.forEach(ConsumerThread::stopConsumer);
        parkingLot.wakeAll();

        joinAll(producerSnapshot);
        joinAll(consumerSnapshot);

        producers.clear();
        consumers.clear();
    }

    public synchronized void reset() {
        stop();
        stats.reset();
        parkingLot = new ParkingLot(configuredCapacity);
    }

    public synchronized void setProductionRateMs(int rateMs) {
        configuredProductionRateMs = Math.max(50, rateMs);
        producers.forEach(producer -> producer.setProductionRateMs(configuredProductionRateMs));
    }

    public synchronized void setConsumptionRateMs(int rateMs) {
        configuredConsumptionRateMs = Math.max(0, rateMs);
        consumers.forEach(consumer -> consumer.setConsumptionRateMs(configuredConsumptionRateMs));
    }

    public synchronized void setProcessingTimeMs(int timeMs) {
        configuredProcessingTimeMs = Math.max(0, timeMs);
        consumers.forEach(consumer -> consumer.setProcessingTimeMs(configuredProcessingTimeMs));
    }

    public synchronized void setBufferCapacity(int capacity) {
        configuredCapacity = Math.max(1, capacity);
        parkingLot.setCapacity(configuredCapacity);
    }

    public ParkingLot getParkingLot() { return parkingLot; }

    public SimulationStats getStats() { return stats; }

    public synchronized List<ProducerThread> getProducers() {
        return List.copyOf(producers);
    }

    public synchronized List<ConsumerThread> getConsumers() {
        return List.copyOf(consumers);
    }

    public boolean isRunning() { return running; }

    private void joinAll(List<? extends Thread> threads) {
        for (Thread thread : threads) {
            try {
                thread.join(1500);
                if (thread.isAlive()) {
                    thread.interrupt();
                    thread.join(250);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
