package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class IndividualPhoneRecordInfo {
    final Long id
    final String name
    final String note
    final Collection<? extends BasePhoneNumber> numbers
}
