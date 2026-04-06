package carpark.threads;

import carpark.model.Car;
import carpark.model.ParkingLot;
import carpark.model.ThreadStatus;
import carpark.stats.SimulationStats;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Produces cars and parks them in the shared parking lot.
 */
public class ProducerThread extends Thread {

    private static final AtomicInteger CAR_ID_COUNTER = new AtomicInteger(0);

    private final int producerId;
    private final ParkingLot parkingLot;
    private final SimulationStats stats;

    private volatile int productionRateMs;
    private volatile boolean running = false;
    private volatile ThreadStatus status = ThreadStatus.WAITING;
    private volatile int carsProduced = 0;

    public ProducerThread(int producerId, ParkingLot parkingLot,
                          SimulationStats stats, int productionRateMs) {
        super("Producer-" + producerId);
        if (producerId <= 0) {
            throw new IllegalArgumentException("Producer ID must be positive");
        }
        if (productionRateMs < 50) {
            throw new IllegalArgumentException("Production rate must be >= 50 ms");
        }
        this.producerId = producerId;
        this.parkingLot = parkingLot;
        this.stats = stats;
        this.productionRateMs = productionRateMs;
        setDaemon(true);
    }

    @Override
    public void run() {
        running = true;
        status = ThreadStatus.ACTIVE;

        try {
            while (running && !isInterrupted()) {
                status = ThreadStatus.ACTIVE;
                Thread.sleep(productionRateMs);

                if (!running) {
                    break;
                }

                Car car = new Car(CAR_ID_COUNTER.incrementAndGet());

                status = ThreadStatus.WAITING;
                long waitStartNanos = System.nanoTime();
                parkingLot.park(car);
                long waitNanos = System.nanoTime() - waitStartNanos;

                status = ThreadStatus.ACTIVE;
                carsProduced++;
                stats.recordProduced(waitNanos);
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

    public void stopProducer() {
        running = false;
        interrupt();
    }

    public void setProductionRateMs(int rateMs) {
        productionRateMs = Math.max(50, rateMs);
    }

    public int getProducerId() { return producerId; }

    public ThreadStatus getStatus() { return status; }

    public int getCarsProduced() { return carsProduced; }

    public int getProductionRateMs() { return productionRateMs; }
}
