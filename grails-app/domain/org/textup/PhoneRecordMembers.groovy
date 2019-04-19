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

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

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

    // Properties
    // ----------

    Collection<PhoneRecord> getAllActive() {
        phoneRecords?.findAll { PhoneRecord pr1 -> pr1.isActive() } ?: new ArrayList<PhoneRecord>()
    }

    // Can't move to static class because Grails manages this relationship so no direct queries
    Collection<PhoneRecord> getByStatus(Collection<PhoneRecordStatus> statuses) {
        if (statuses) {
            HashSet<PhoneRecordStatus> statusesToFind = new HashSet<>(statuses)
            getAllActive().findAll { PhoneRecord pr1 -> statusesToFind.contains(pr1.status) }
        }
        else { getAllActive() }
    }
}
