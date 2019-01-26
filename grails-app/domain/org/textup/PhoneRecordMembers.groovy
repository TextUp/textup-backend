package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

// This class was created because GORM could not generate a join table for a `hasMany`
// property in a subclass (`GroupPhoneRecord`) while the superclass (`PhoneRecord`) has a
// self-referential property (`shareSource`)

@EqualsAndHashCode
@GrailsTypeChecked
class PhoneRecordMembers implements WithId, CanSave<PhoneRecordMembers> {

    static hasMany = [phoneRecords: PhoneRecord]

    static constraints = {
        phoneRecords validator: { Collection<PhoneRecord> val ->
            if (val.any { PhoneRecord pr1 -> pr1.instanceOf(GroupPhoneRecord) }) {
                ["nestingNotSupported"]
            }
        }
    }

    static Result<PhoneRecordMembers> tryCreate() {
        DomainUtils.trySave(new PhoneRecordMembers(), ResultStatus.CREATED)
    }
}
