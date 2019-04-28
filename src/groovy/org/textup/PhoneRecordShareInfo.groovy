package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.joda.time.DateTime
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
class PhoneRecordShareInfo {

    final DateTime whenCreated
    final Long phoneId
    final String permission

    static PhoneRecordShareInfo create(DateTime whenCreated, Phone p1, SharePermission perm1) {
        new PhoneRecordShareInfo(whenCreated, p1?.id, perm1?.toString())
    }
}
