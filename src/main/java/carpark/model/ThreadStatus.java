package carpark.model;

/**
 * Represents the operational state of a simulation thread.
 *
 * <p>Used by the Visual Dashboard to display real-time thread health
 * indicators with colour-coded icons.</p>
 *
 * <ul>
 *   <li>{@link #ACTIVE}  – Thread is running and processing normally.</li>
 *   <li>{@link #WAITING} – Thread is blocked on a Condition Variable.</li>
 *   <li>{@link #CRASHED} – Thread has encountered an unrecoverable error.</li>
 * </ul>
 *
 * @author Student
 * @version 1.0
 */
public enum ThreadStatus {

    /** The thread is executing its main work loop. */
    ACTIVE,

    /**
     * The thread is blocked waiting on a Condition Variable
     * (e.g. a Producer waiting because the lot is full, or a
     * Consumer waiting because the lot is empty).
     */
    WAITING,

    /** The thread terminated unexpectedly due to an exception. */
    CRASHED
}
