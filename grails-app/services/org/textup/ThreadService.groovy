package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@GrailsTypeChecked
@Transactional
class ThreadService {

    private ExecutorService _pool

    @PostConstruct
    // sometimes hot reloading will call the destroy hook so we need to make sure the initialization
    // is also in a similar annotation driven lifecycle hook
    protected void startPool() {
        _pool = Executors.newCachedThreadPool();
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
            if (!_pool.awaitTermination(60, TimeUnit.SECONDS))
                log.error("ThreadService.cleanUp: pool did not terminate")
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
        _pool.submit([call: wrapAction(action)] as Callable<T>)
    }

    // Helpers
    // -------

    protected <T> Closure<T> wrapAction(Closure<T> action) {
        return { ->
            try {
                // doesn't matter which domain class we call this on
                Organization.withNewSession {
                    Organization.withTransaction {
                        action()
                    }
                }
            } catch(Throwable e) {
                log.error("ThreadService.wrapAction: uncaught exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
