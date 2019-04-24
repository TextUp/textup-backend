package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
@Validateable
class RecordItemRequestSection implements CanValidate {

    // Need to allow empty record items because FOP still expects the other fields even if no items
    final List<? extends ReadOnlyRecordItem> recordItems
    final Collection<String> contactNames
    final Collection<String> sharedContactNames
    final Collection<String> tagNames
    final String phoneName
    final String phoneNumber

    static Result<RecordItemRequestSection> tryCreate(String pName, BasePhoneNumber pNum,
        Collection<? extends ReadOnlyRecordItem> thisItems, Collection<? extends PhoneRecordWrapper> wraps) {

        // pass in `false` to sort so it does not mutate
        List<? extends ReadOnlyRecordItem> rItems = thisItems ? thisItems.sort(false) : []
        Collection<String> cNames = WrapperUtils.secureNamesIgnoreFails(wraps) { PhoneRecordWrapper w1 ->
            WrapperUtils.isContact(w1)
        }
        Collection<String> sNames = WrapperUtils.secureNamesIgnoreFails(wraps) { PhoneRecordWrapper w1 ->
            WrapperUtils.isSharedContact(w1)
        }
        Collection<String> tNames = WrapperUtils.secureNamesIgnoreFails(wraps) { PhoneRecordWrapper w1 ->
            WrapperUtils.isTag(w1)
        }
        RecordItemRequestSection section1 = new RecordItemRequestSection(
            Collections.unmodifiableList(rItems),
            Collections.unmodifiableCollection(cNames),
            Collections.unmodifiableCollection(sNames),
            Collections.unmodifiableCollection(tNames),
            pName,
            pNum?.prettyPhoneNumber)
        DomainUtils.tryValidate(section1, ResultStatus.CREATED)
    }
}
