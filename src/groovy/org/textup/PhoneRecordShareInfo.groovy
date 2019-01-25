package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class PhoneRecordShareInfo {
    final DateTime whenCreated
    final Long phoneId
    final String permission
}
