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
class DehydratedRecipients implements CanValidate, Rehydratable<Recipients> {

    private final Long phoneId
    private final Collection<Long> allIds
    private final Integer maxNum
    private final VoiceLanguage language

    static Result<DehydratedRecipients> tryCreate(Recipients recips1) {
        DomainUtils.tryValidate(recips1).then {
            DehydratedRecipients dr1 = new DehydratedRecipients(phoneId: recips1.phone.id,
                allIds: recips1.all*.id,
                maxNum: recips1.maxNum,
                language: recips1.language)
            DomainUtils.tryValidate(dr1, ResultStatus.CREATED)
        }
    }

    // Methods
    // -------

    @Override
    Result<Recipients> tryRehydrate() {
        Recipients r1 = new Recipients(phone: Phone.get(phoneId),
            all: AsyncUtils.getAllIds(PhoneRecord, allIds),
            maxNum: maxNum,
            language: language)
        DomainUtils.tryValidate(r1)
    }
}
