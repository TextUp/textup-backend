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
class IndividualPhoneRecordInfo {

    final Long id
    final String name
    final String note
    final Collection<? extends BasePhoneNumber> numbers

    static IndividualPhoneRecordInfo create(Long id, String name, String note,
        Collection<? extends BasePhoneNumber> cNums) {

        new IndividualPhoneRecordInfo(id,
            name,
            note,
            Collections.unmodifiableCollection(cNums))
    }
}
