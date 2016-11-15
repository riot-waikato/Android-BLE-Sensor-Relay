package thyeway.xyz.activitytracker;

/**
 * Interface class to allow callbacks from AsyncTasks
 */
public interface TaskCallback {

    /**
     * Reports back the task status
     * @param status true if successful
     */
    void taskStatus(boolean status);

}
