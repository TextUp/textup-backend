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
    static final String phoneName
    static final String phoneNumber
    static final Collection<? extends ReadOnlyRecordItem> recordItems
    static final Collection<String> contactNames
    static final Collection<String> sharedContactNames
    static final Collection<String> tagNames

    static constraints = {
        recordItems minSize: 1
    }

    static Result<RecordItemRequestSection> tryCreate(String pName, BasePhoneNumber pNum,
        Collection<? extends ReadOnlyRecordItem> rItems, Collection<? extends PhoneRecordWrapper> wraps) {

        Collection<String> cNames = WrapperUtils.secureNamesIgnoreFails(wrappers) { WrapperUtils.isContact(it) },
            sNames = WrapperUtils.secureNamesIgnoreFails(wrappers) { WrapperUtils.isSharedContact(it) },
            tNames = WrapperUtils.secureNamesIgnoreFails(wrappers) { WrapperUtils.isTag(it) }
        RecordItemRequestSection section1 = new RecordItemRequestSection(phoneName: pName,
            phoneNumber: pNum.prettyPhoneNumber,
            recordItems: Collections.unmodifiableCollection(rItems),
            contactNames: Collections.unmodifiableCollection(cNames),
            sharedContactNames: Collections.unmodifiableCollection(sNames),
            tagNames: Collections.unmodifiableCollection(tNames))
        DomainUtils.tryValidate(section1, ResultStatus.CREATED)
    }
}
