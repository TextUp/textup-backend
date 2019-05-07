package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class PhoneUtils {

    static final String CUSTOMER_SUPPORT_NAME = "TextUp Customer Support"
    static final PhoneNumber CUSTOMER_SUPPORT_NUMBER = PhoneNumber.tryCreate("4015197932").payload

    static Result<Phone> tryAddChangeToHistory(Phone p1, BasePhoneNumber bNum) {
        DateTime dt = JodaUtils.utcNow()
        PhoneNumberHistory.tryCreate(dt, bNum)
            .then { PhoneNumberHistory nh1 ->
                if (p1) {
                    // find most recent entry before adding new one
                    PhoneNumberHistory nh2 = p1.numberHistoryEntries?.max()
                    if (nh2) {
                        nh2.endTime = dt
                    }
                    // add new entry
                    p1.addToNumberHistoryEntries(nh1)
                }
                DomainUtils.trySave(p1)
            }
    }
}
