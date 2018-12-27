package org.textup.cache

import grails.compiler.GrailsTypeChecked
import grails.plugin.cache.*
import grails.transaction.Transactional
import org.springframework.transaction.annotation.Propagation
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

    // (1) CacheEvict ignores return values, see section 28.3.3 of
    // https://docs.spring.io/spring/docs/3.1.x/spring-framework-reference/html/cache.html
    // (2) For SpEL, use the parameter index instead of the parameter name, which may be unreliably
    // available. see: https://stackoverflow.com/a/14197738
    // (3) Use `get` instead of `getAt` because `getAt` is dynamically added by Groovy and `get` is
    // part of the Java List interface, which is the source consulted by Spring
    @CacheEvict(value = "receiptsCache", key = "#p0?.get(0)?.apiId")
    Result<List<RecordItemReceipt>> updateStatusForReceipts(List<RecordItemReceipt> receipts,
        ReceiptStatus newStatus) {

        for (RecordItemReceipt receipt in receipts) {
            receipt.status = newStatus
            if (!receipt.save()) {
                return IOCUtils.resultFactory.failWithValidationErrors(receipt.errors)
            }
        }
        IOCUtils.resultFactory.success(receipts)
    }

    // See comments on `updateStatusForReceipts` for things to note
    @CacheEvict(value = "receiptsCache", key = "#p0?.get(0)?.apiId")
    Result<List<RecordItemReceipt>> updateDurationForCall(List<RecordItemReceipt> receipts,
        Integer duration) {

        for (RecordItemReceipt receipt in receipts) {
            receipt.numBillable = duration
            if (!receipt.save()) {
                return IOCUtils.resultFactory.failWithValidationErrors(receipt.errors)
            }
        }
        IOCUtils.resultFactory.success(receipts)
    }
}
