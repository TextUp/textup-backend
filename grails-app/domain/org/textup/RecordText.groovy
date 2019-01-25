package org.textup

import groovy.transform.EqualsAndHashCode
import grails.compiler.GrailsTypeChecked
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
class RecordText extends RecordItem implements ReadOnlyRecordText {

	String contents

    static mapping = {
        contents type: "text"
    }
    static constraints = {
    	contents blank: true, nullable: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
    }

    static Result<RecordText> tryCreate(Record rec1, String contents) {
        DomainUtils.trySave(new RecordText(record: rec1, contents: contents), ResultStatus.CREATED)
    }

    // Properties
    // ----------

    int getNumSegments() {
        int totalNumSegments = 0
        receipts?.each { RecordItemReceipt rpt1 ->
            if (rpt1.numBillable) {
                totalNumSegments += rpt1.numBillable
            }
        }
        totalNumSegments
    }
}
