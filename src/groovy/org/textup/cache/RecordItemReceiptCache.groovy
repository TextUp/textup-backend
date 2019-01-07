package org.textup.cache

import grails.compiler.GrailsTypeChecked
import grails.plugin.cache.*
import grails.transaction.Transactional
import org.textup.*
import org.textup.type.*
import org.textup.util.*

// (1) We create a new class because Spring AOP advice is only trigger for PUBLIC, NON-STATIC methods
// called by an entity external to this containing class
// (2) We aren't able to use constants in the annotations because of a Groovy bug. Therefore
// we have to use the string cache name and remember to update these values in case the Constant
// value for CACHE_RECEIPT is ever chanced. See https://issues.apache.org/jira/browse/GROOVY-3278
// (3) This class must be managed by Spring's IOC container for the AOP annotations to be applied
// See `conf/spring/resources.groovy`

@GrailsTypeChecked
class RecordItemReceiptCache {

    // (1) Note that the return value of this method is the value in the in-memory cache map
    // See: https://docs.spring.io/spring/docs/3.1.x/spring-framework-reference/html/cache.html
    // (2) For SpEL, use the parameter index instead of the parameter name, which may be unreliably
    // available. see: https://stackoverflow.com/a/14197738
    @Cacheable(value = "receiptsCache", key = "#p0")
    List<RecordItemReceipt> findReceiptsByApiId(String apiId) {
        RecordItemReceipt.findAllByApiId(apiId)
    }

    // (1) Use CachePut instead of CacheEvict because we want to avoid excessive db calls with
    // caching so we will cache the updated state here save another read to the database. If we
    // used CacheEvict, we would only be reducing our db hits by a relatively small amount,
    // one fewer for calls and the same number for texts
    // (2) For SpEL, use the parameter index instead of the parameter name, which may be unreliably
    // available. see: https://stackoverflow.com/a/14197738
    // (3) Use `get` instead of `getAt` because `getAt` is dynamically added by Groovy and `get` is
    // part of the Java List interface, which is the source consulted by Spring
    // (4) need to `merge` instead of `save` because, when the receipts are returned from the cache
    // instead of from a db call, they are NOT attached to the currently-active session
    @CachePut(value = "receiptsCache", key = "#p0?.get(0)?.apiId")
    List<RecordItemReceipt> updateReceipts(List<RecordItemReceipt> receipts,
        ReceiptStatus newStatus, Integer newDuration = null) {

        if (!receipts) {
            return []
        }
        ResultGroup<RecordItemReceipt> resGroup = new ResultGroup<>()
        for (RecordItemReceipt receipt in receipts) {
            receipt.status = newStatus
            if (newDuration != null) {
                receipt.numBillable = newDuration
            }
            if (receipt.merge()) {
                resGroup << IOCUtils.resultFactory.success(receipt)
            }
            else { resGroup << IOCUtils.resultFactory.failWithValidationErrors(receipt.errors) }
        }
        resGroup.logFail("updateReceipts: apiIds ${receipts*.apiId}, newStatus: $newStatus, newDuration: $newDuration")
        resGroup.payload
    }
}
