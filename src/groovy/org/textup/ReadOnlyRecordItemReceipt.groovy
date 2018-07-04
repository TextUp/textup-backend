package org.textup

import grails.compiler.GrailsCompileStatic
import org.textup.type.ReceiptStatus
import org.textup.validator.PhoneNumber

@GrailsCompileStatic
interface ReadOnlyRecordItemReceipt {
    Long getId()
    ReceiptStatus getStatus()
    PhoneNumber getReceivedBy()
}
