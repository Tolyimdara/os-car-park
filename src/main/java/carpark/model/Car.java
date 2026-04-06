package carpark.model;

/**
 * Represents a car arriving at the car park facility.
 *
 * <p>Cars are produced by {@link carpark.threads.ProducerThread} and
 * consumed (guided to a parking space) by {@link carpark.threads.ConsumerThread}.
 * Each car records its arrival time to allow wait-time calculations.</p>
 *
 * @author Student
 * @version 1.0
 */
public class Car {

    /** Unique identifier for this car. */
    private final int id;

    /** Licence plate number in format CAM-XXXX. */
    private final String plateNumber;

    /** System time (ms) when the car arrived. */
    private final long arrivalTime;

    /**
     * Constructs a new Car with a given ID.
     * The plate number and arrival timestamp are set automatically.
     *
     * @param id a positive unique integer identifying this car
     * @throws IllegalArgumentException if id is not positive
     */
    public Car(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Car ID must be positive, got: " + id);
        }
        this.id = id;
        this.plateNumber = String.format("CAM-%04d", id % 10000);
        this.arrivalTime = System.currentTimeMillis();
    }

    /**
     * Returns the unique car identifier.
     *
     * @return positive integer ID
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the formatted licence plate number.
     *
     * @return plate string, e.g. "CAM-0042"
     */
    public String getPlateNumber() {
        return plateNumber;
    }

    /**
     * Returns the epoch-millisecond timestamp at which the car arrived.
     *
     * @return arrival time in milliseconds
     */
    public long getArrivalTime() {
        return arrivalTime;
    }

    /**
     * Returns a human-readable description of this car.
     *
     * @return string in the format "Car[CAM-XXXX]"
     */
    @Override
    public String toString() {
        return "Car[" + plateNumber + "]";
    }
}
