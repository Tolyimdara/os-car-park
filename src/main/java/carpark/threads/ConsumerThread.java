package carpark.threads;

import carpark.model.Car;
import carpark.model.ParkingLot;
import carpark.model.ThreadStatus;
import carpark.stats.SimulationStats;

/**
 * Consumes cars from the shared parking lot and simulates guiding them out.
 */
public class ConsumerThread extends Thread {

    private final int consumerId;
    private final ParkingLot parkingLot;
    private final SimulationStats stats;

    private volatile int consumptionRateMs;
    private volatile int processingTimeMs;
    private volatile boolean running = false;
    private volatile ThreadStatus status = ThreadStatus.WAITING;
    private volatile int carsProcessed = 0;

    public ConsumerThread(int consumerId, ParkingLot parkingLot,
                          SimulationStats stats, int consumptionRateMs,
                          int processingTimeMs) {
        super("Consumer-" + consumerId);
        if (consumerId <= 0) {
            throw new IllegalArgumentException("Consumer ID must be positive");
        }
        this.consumerId = consumerId;
        this.parkingLot = parkingLot;
        this.stats = stats;
        this.consumptionRateMs = Math.max(0, consumptionRateMs);
        this.processingTimeMs = Math.max(0, processingTimeMs);
        setDaemon(true);
    }

    @Override
    public void run() {
        running = true;
        status = ThreadStatus.ACTIVE;

        try {
            while (running && !isInterrupted()) {
                status = ThreadStatus.WAITING;
                long waitStartNanos = System.nanoTime();
                Car car = parkingLot.leave();
                long waitNanos = System.nanoTime() - waitStartNanos;

                if (!running) {
                    break;
                }

                status = ThreadStatus.ACTIVE;
                if (car != null && processingTimeMs > 0) {
                    Thread.sleep(processingTimeMs);
                }

                carsProcessed++;
                stats.recordConsumed(waitNanos);

                if (consumptionRateMs > 0) {
                    Thread.sleep(consumptionRateMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            status = ThreadStatus.CRASHED;
        } finally {
            if (status != ThreadStatus.CRASHED) {
                status = ThreadStatus.WAITING;
            }
        }
    }

    public void stopConsumer() {
        running = false;
        interrupt();
    }

    public void setConsumptionRateMs(int rateMs) {
        consumptionRateMs = Math.max(0, rateMs);
    }

    public void setProcessingTimeMs(int timeMs) {
        processingTimeMs = Math.max(0, timeMs);
    }

    public int getConsumerId() { return consumerId; }

    public ThreadStatus getStatus() { return status; }

    public int getCarsProcessed() { return carsProcessed; }

    public int getConsumptionRateMs() { return consumptionRateMs; }

    public int getProcessingTimeMs() { return processingTimeMs; }
}
