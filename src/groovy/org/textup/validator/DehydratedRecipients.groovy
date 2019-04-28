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
class DehydratedRecipients implements CanValidate, Rehydratable<Recipients> {

    final Long phoneId
    final Collection<Long> allIds
    final Integer maxNum
    final VoiceLanguage language

    static Result<DehydratedRecipients> tryCreate(Recipients recips1) {
        DomainUtils.tryValidate(recips1).then {
            Collection<Long> allIds = recips1.all.collect { it.id }
            DehydratedRecipients dr1 = new DehydratedRecipients(recips1.phone.id, allIds,
                recips1.maxNum, recips1.language)
            DomainUtils.tryValidate(dr1, ResultStatus.CREATED)
        }
    }

    // Methods
    // -------

    @Override
    Result<Recipients> tryRehydrate() {
        Recipients.tryCreateForPhoneAndObjs(Phone.get(phoneId),
            AsyncUtils.getAllIds(PhoneRecord, allIds),
            language,
            maxNum)
    }
}
