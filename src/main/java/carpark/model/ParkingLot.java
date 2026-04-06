package carpark.model;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe bounded buffer representing the parking lot.
 *
 * Producers block on {@code notFull} when the lot has no free spaces, consumers
 * block on {@code notEmpty} when there are no cars to remove, and both sides
 * sleep on Conditions instead of busy waiting.
 */
public class ParkingLot {

    private final Queue<Car> spaces = new ArrayDeque<>();
    private final ReentrantLock mutex = new ReentrantLock(true);
    private final Condition notFull = mutex.newCondition();
    private final Condition notEmpty = mutex.newCondition();

    private volatile int capacity;
    private int totalParked;
    private int totalLeft;

    public ParkingLot(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0, got: " + capacity);
        }
        this.capacity = capacity;
    }

    public void park(Car car) throws InterruptedException {
        if (car == null) {
            throw new IllegalArgumentException("Car must not be null");
        }

        mutex.lockInterruptibly();
        try {
            while (spaces.size() >= capacity) {
                notFull.await();
            }

            spaces.offer(car);
            totalParked++;
            notEmpty.signal();
        } finally {
            mutex.unlock();
        }
    }

    public Car leave() throws InterruptedException {
        mutex.lockInterruptibly();
        try {
            while (spaces.isEmpty()) {
                notEmpty.await();
            }

            Car car = spaces.poll();
            totalLeft++;
            notFull.signal();
            return car;
        } finally {
            mutex.unlock();
        }
    }

    public void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            return;
        }

        mutex.lock();
        try {
            capacity = newCapacity;
            notFull.signalAll();
            notEmpty.signalAll();
        } finally {
            mutex.unlock();
        }
    }

    public void wakeAll() {
        mutex.lock();
        try {
            notFull.signalAll();
            notEmpty.signalAll();
        } finally {
            mutex.unlock();
        }
    }

    public int getOccupied() {
        mutex.lock();
        try {
            return spaces.size();
        } finally {
            mutex.unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailable() {
        return Math.max(0, getCapacity() - getOccupied());
    }

    public double getOccupancyPercent() {
        int currentCapacity = getCapacity();
        return currentCapacity > 0 ? (double) getOccupied() / currentCapacity * 100.0 : 0.0;
    }

    public int getTotalParked() {
        mutex.lock();
        try {
            return totalParked;
        } finally {
            mutex.unlock();
        }
    }

    public int getTotalLeft() {
        mutex.lock();
        try {
            return totalLeft;
        } finally {
            mutex.unlock();
        }
    }
}
