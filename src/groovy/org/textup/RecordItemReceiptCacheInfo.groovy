package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
class RecordItemReceiptCacheInfo {

    final Long id
    final Long itemId
    final ReceiptStatus status
    final Integer numBillable

    static RecordItemReceiptCacheInfo create(Long id, RecordItem rItem1, ReceiptStatus status,
        Integer numBillable) {

        new RecordItemReceiptCacheInfo(id, rItem1?.id, status, numBillable)
    }
}
