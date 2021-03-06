package org.textup.util

import grails.async.*
import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import java.util.concurrent.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class AsyncUtils {

    static final int UPLOAD_BATCH_SIZE = 8

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static <T extends CanSave> Collection<T> getAllIds(Class<T> clazz, Collection<Long> thisIds) {
        Collection<Long> ids = CollectionUtils.shallowCopyNoNull(thisIds)
        Collection<T> found = clazz.getAll(ids as Iterable<Serializable>)
        if (found.size() != ids.size()) {
            log.error("getAllIds: did not find all for `$clazz` and `$ids`")
        }
        CollectionUtils.ensureNoNull(found)
    }

    static <T> Future<T> noOpFuture(T obj = null) {
        [
            cancel: { boolean b -> true },
            get: { long timeout = 0l, TimeUnit unit = null -> obj },
            isCancelled: { false },
            isDone: { true }
        ] as Future<?>
    }

    static <K, T> List<T> doAsyncInBatches(Collection<K> data, int batchSize, Closure<T> doAction) {
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
