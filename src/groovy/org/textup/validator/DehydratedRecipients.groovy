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

    private final Long phoneId
    private final Collection<Long> allIds
    private final Integer maxNum
    private final VoiceLanguage language

    static Result<DehydratedRecipients> tryCreate(Recipients recips1) {
        DomainUtils.tryValidate(recips1).then {
            DehydratedRecipients dr1 = new DehydratedRecipients(recips1.phone.id, recips1.all*.id,
                recips1.maxNum, recips1.language)
            DomainUtils.tryValidate(dr1, ResultStatus.CREATED)
        }
    }

    // Methods
    // -------

    @Override
    Result<Recipients> tryRehydrate() {
        Recipients.tryCreate(Phone.get(phoneId),
            AsyncUtils.getAllIds(PhoneRecord, allIds),
            language,
            maxNum)
    }
}
