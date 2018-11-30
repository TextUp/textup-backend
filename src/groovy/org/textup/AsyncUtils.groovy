package org.textup

import grails.async.*
import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import java.util.concurrent.*

@GrailsTypeChecked
@Log4j
class AsyncUtils {

    static final int SEND_BATCH_SIZE = 20
    static final int UPLOAD_BATCH_SIZE = 8

    static <I extends WithId> Map<Long, I> idMap(Collection<I> withIdObjects) {
        MapUtils.<Long, I>buildObjectMap({ I obj -> obj.id }, withIdObjects)
    }

    static <T> Future<T> noOpFuture(T obj = null) {
        [
            cancel: { boolean b -> true },
            get: { long timeout = 0l, TimeUnit unit = null -> obj },
            isCancelled: { false },
            isDone: { true }
        ] as Future<?>
    }

    static <K, T> List<T> doAsyncInBatches(Collection<K> data, Closure<T> doAction,
        int batchSize = SEND_BATCH_SIZE) {

        if (!data) {
            return []
        }
        // step 1: process in batches
        PromiseList<T> pList1 = new PromiseList<>()
        new ArrayList<K>(data)
            .collate(batchSize)
            .collect { List<K> batch ->
                pList1 << Promises.task { batch.collect(doAction) }
            }
        // step 2: flatten batched results array
        List<T> results = []
        List<List<T>> batchedResults = (pList1.get(1, TimeUnit.MINUTES) as List<List<T>>)
        batchedResults.each(results.&addAll)
        results
    }
}
