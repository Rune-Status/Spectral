package org.spectral.mapper.execution

/**
 * Responsible for running two [Execution] instances in
 * parallel execution.
 *
 * This is used to compare multiple method executions to score their similarity.
 *
 * @property executionA
 * @property executionB
 */
class ParallelExecutor(val executionA: Execution, val executionB: Execution) {

    /**
     * The predicate for pausing executions
     */
    private var pausePredicate: ((Execution) -> Boolean)? = null

    /**
     * Initializes both [executionA] and [executionB]
     */
    fun initialize() {
        executionA.initialize()
        executionB.initialize()
    }

    /**
     * Gets whether the executor is running given both [executionA] and
     * [executionB] execution states.
     *
     * @return Boolean
     */
    fun isRunning(): Boolean {
        if(isPaused() || isTerminated()) return false
        return true
    }

    /**
     * Whether both executions are paused.
     *
     * @return Boolean
     */
    fun isPaused(): Boolean {
        if(executionA.paused && executionB.paused) return true
        return false
    }

    /**
     * Whether both executions have terminated.
     *
     * @return Boolean
     */
    fun isTerminated(): Boolean {
        if(executionA.terminated && executionB.terminated) return true
        return false
    }

    /**
     * Un-pause both executions
     */
    fun unpause() {
        executionA.paused = false
        executionB.paused = false
    }

    /**
     * Sets a predicate to pause either [executionA] or [executionB] [Execution]
     * process.
     *
     * @param predicate Function1<Execution, Boolean>
     */
    fun pauseWhen(predicate: (Execution) -> Boolean) {
        this.pausePredicate = predicate
    }

    /**
     * Executes both [executionA] and [executionB] and steps them both
     * forward by one each time this method is called.
     *
     * @param consumer A consumer callback for state operations.
     */
    fun executeParallel(consumer: (ParallelExecutor) -> Boolean): Boolean {
        /*
         * Step both of the executions forward.
         */
        if(!executionA.paused && !executionA.terminated) executionA.step()
        if(!executionB.paused && !executionB.terminated) executionB.step()

        if(!executionA.paused) {
            executionA.paused = pausePredicate?.invoke(executionA) ?: false
        }

        if(!executionB.paused) {
            executionB.paused = pausePredicate?.invoke(executionB) ?: false
        }

        if(isPaused()) {
            return consumer(this)
        }

        return true
    }
}