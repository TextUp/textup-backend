package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class RecordItemRequestSection extends Validateable {
    String phoneName
    String phoneNumber
    Collection<? extends ReadOnlyRecordItem> recordItems
    Collection<String> names

    static constraints = {
        recordItems minSize: 1
    }

    static Result<RecordItemRequestSection> tryCreate(String pName, BasePhoneNumber pNum,
        Collection<? extends ReadOnlyRecordItem> rItems, Collection<String> names) {

        RecordItemRequestSection section1 = new RecordItemRequestSection(phoneName: pName,
            phoneNumber: pNum.prettyPhoneNumber,
            recordItems: rItems,
            names: names)
        DomainUtils.tryValidate(section1, ResultStatus.CREATED)
    }
}
