package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@GrailsTypeChecked
@Transactional
class ThreadService {

    private static final int MIN_POOL_SIZE = 10
    private static final int POOL_DELTA = 10
    private static final int QUEUE_THRESHOLD = 10
    private static final int MAX_POOL_SIZE = 250

    private ScheduledThreadPoolExecutor _pool

    // sometimes hot reloading will call the destroy hook so we need to make sure the initialization
    // is also in a similar annotation driven lifecycle hook
    @PostConstruct
    protected void startPool() {
        // when core number of threads = max number of threads, effective same as Executors.newFixedThreadPool
        _pool = new ScheduledThreadPoolExecutor(MIN_POOL_SIZE, new ThreadPoolExecutor.AbortPolicy())
        // if we allow core thread timeout, we core threads need to have non-zero keep alive times
        _pool.setKeepAliveTime(2L, TimeUnit.MINUTES)
        _pool.allowCoreThreadTimeOut(true)
    }

    // See https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html
    @PreDestroy
    protected void cleanUp() {
        log.warn("Shutting down ThreadService")
        _pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!_pool.awaitTermination(60, TimeUnit.SECONDS)) {
                _pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!_pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("ThreadService.cleanUp: pool did not terminate")
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            _pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    // Public methods
    // --------------

    public <T> Future<T> submit(Closure<T> action) {
        Future<T> res = _pool.submit(wrapAsCallable(action))
        tryAdjustPoolSize()
        res
    }

    public <T> ScheduledFuture<T> delay(long delay, TimeUnit unit, Closure<T> action) {
        ScheduledFuture<T> res = _pool.schedule(wrapAsCallable(action), delay, unit)
        tryAdjustPoolSize()
        res
    }

    // Helpers
    // -------

    protected void tryAdjustPoolSize() {
        int actualPoolSize = _pool.poolSize,
            corePoolSize = _pool.corePoolSize,
            queueSize = _pool.queue.size()

        Integer newPoolSize
        if (queueSize > QUEUE_THRESHOLD && corePoolSize + POOL_DELTA <= MAX_POOL_SIZE) {
            newPoolSize = corePoolSize + POOL_DELTA
        }
        else if (queueSize == 0 && actualPoolSize + (POOL_DELTA * 2) < corePoolSize &&
            actualPoolSize + POOL_DELTA >= MIN_POOL_SIZE) {
            newPoolSize = actualPoolSize + POOL_DELTA
        }

        if (newPoolSize) {
            _pool.corePoolSize = newPoolSize
        }
    }

    protected <T> Callable<T> wrapAsCallable(Closure<T> action) {
        [call: addSessionAndTransaction(action)] as Callable<T>
    }

    protected <T> Closure<T> addSessionAndTransaction(Closure<T> action) {
        return { ->
            try {
                // doesn't matter which domain class we call this on
                Organization.withNewSession {
                    Organization.withTransaction {
                        action()
                    }
                }
            } catch(Throwable e) {
                log.error("addSessionAndTransaction: uncaught exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
