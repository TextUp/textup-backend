package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class RecordItemRequestSection implements CanValidate {

    final String phoneName
    final String phoneNumber
    final Collection<? extends ReadOnlyRecordItem> recordItems
    final Collection<String> contactNames
    final Collection<String> sharedContactNames
    final Collection<String> tagNames

    static constraints = {
        recordItems minSize: 1
    }

    static Result<RecordItemRequestSection> tryCreate(String pName, BasePhoneNumber pNum,
        Collection<? extends ReadOnlyRecordItem> rItems, Collection<? extends PhoneRecordWrapper> wraps) {

        Collection<String> cNames = WrapperUtils.secureNamesIgnoreFails(wraps) { PhoneRecordWrapper w1 ->
            WrapperUtils.isContact(w1)
        }
        Collection<String> sNames = WrapperUtils.secureNamesIgnoreFails(wraps) { PhoneRecordWrapper w1 ->
            WrapperUtils.isSharedContact(w1)
        }
        Collection<String> tNames = WrapperUtils.secureNamesIgnoreFails(wraps) { PhoneRecordWrapper w1 ->
            WrapperUtils.isTag(w1)
        }
        RecordItemRequestSection section1 = new RecordItemRequestSection(phoneName: pName,
            phoneNumber: pNum.prettyPhoneNumber,
            recordItems: Collections.unmodifiableCollection(rItems),
            contactNames: Collections.unmodifiableCollection(cNames),
            sharedContactNames: Collections.unmodifiableCollection(sNames),
            tagNames: Collections.unmodifiableCollection(tNames))
        DomainUtils.tryValidate(section1, ResultStatus.CREATED)
    }
}
