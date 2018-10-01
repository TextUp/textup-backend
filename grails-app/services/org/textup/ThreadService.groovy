package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@GrailsTypeChecked
@Transactional
class ThreadService {

    private ScheduledThreadPoolExecutor _pool
    private final int NUM_THREADS = 500

    @PostConstruct
    // sometimes hot reloading will call the destroy hook so we need to make sure the initialization
    // is also in a similar annotation driven lifecycle hook
    protected void startPool() {
        // when core number of threads = max number of threads, effective same as Executors.newFixedThreadPool
        _pool = new ScheduledThreadPoolExecutor(NUM_THREADS, new ThreadPoolExecutor.AbortPolicy())
        // if we allow core thread timeout, we core threads need to have non-zero keep alive times
        _pool.setKeepAliveTime(2L, TimeUnit.MINUTES)
        _pool.allowCoreThreadTimeOut(true)
    }

    @PreDestroy
    // See https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html
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
        _pool.submit(wrapAsCallable(action))
    }

    public <T> ScheduledFuture<T> submit(long delay, TimeUnit unit, Closure<T> action) {
        _pool.schedule(wrapAsCallable(action), delay, unit)
    }

    // Helpers
    // -------

    protected <T> Callable<T> wrapAsCallable(Closure<T> action) {
        [call: addSessionAndTransaction(action)] as Callable<T>
    }
    protected <T> Closure<T> addSessionAndTransaction(Closure<T> action) {
        return { ->
            // [FUTURE] remove this log line once we've determined an appropriate max size for the
            // thread pool from logging real-world usage
            log.error("poolSize: ${_pool.poolSize}, largestPoolSize: ${_pool.largestPoolSize}, queue.size(): ${_pool.queue.size()}")
            try {
                // doesn't matter which domain class we call this on
                Organization.withNewSession {
                    Organization.withTransaction {
                        action()
                    }
                }
            } catch(Throwable e) {
                log.error("ThreadService.addSessionAndTransaction: uncaught exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
