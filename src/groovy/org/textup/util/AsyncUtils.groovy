package org.textup.util

import grails.async.*
import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import java.util.concurrent.*
import org.textup.*

@GrailsTypeChecked
@Log4j
class AsyncUtils {

    static final int SEND_BATCH_SIZE = 20
    static final int UPLOAD_BATCH_SIZE = 8

    // TODO still need this?
    static <I extends WithId> Map<Long, I> idMap(Collection<I> withIdObjects) {
        MapUtils.<Long, I>buildObjectMap(withIdObjects) { I obj -> obj.id }
    }

    // TODO skip type checking?
    // TODO this needs to return domain objects in the SAME order as the passed-in ids
    static <T extends Saveable> Collection<T> getAllIds(Clazz<T> clazz, Collection<Long> ids) {
        Collection<T> found = clazz.getAll(ids?.unique() as Iterable<Serializable>)
        if (found.size() != ids.size()) {
            log.error("getAllIds: did not find all for `$clazz` and `$ids`")
        }
        CollectionUtils.ensureNoNulls(found)
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
