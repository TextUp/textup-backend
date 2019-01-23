package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import org.joda.time.DateTime
import org.textup.*
import org.textup.cache.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class Phones {

    static Result<Phone> mustFindActiveForOwner(Long ownerId, PhoneOwnershipType type,
        boolean createIfAbsent) {

        IOCUtils.getBean(PhoneCache).mustFindAnyPhoneIdForOwner(ownerId, type)
            .then { Long pId -> Phones.mustFindActiveForId(pId) }
            .ifFail { Result<?> failRes ->
                if (createIfAbsent) {
                    Phone.tryCreate(ownerId, type)
                }
                else { failRes }
            }
    }

    static Result<Phone> mustFindActiveForId(Long pId) {
        Phone p1 = Phone.get(pId)
        if (p1?.isActive()) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus(
                "voicemailService.finishedProcessingVoicemailGreeting.phoneNotFound", // TODO
                ResultStatus.NOT_FOUND, [pId])
        }
    }

    static Result<Phone> mustFindActiveForNumber(BasePhoneNumber bNum) {
        Phone p1 = Phones.buildActiveForNumber(bNum).list()[0]
        if (p1) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("", // TODO
                ResultStatus.NOT_FOUND, [bNum])
        }
    }

    static DetachedCriteria<Phone> buildActiveForNumber(BasePhoneNumber bNum) {
        new DetachedCriteria(Phone)
            .build { eq("numberAsString", bNum.number) }
            .build(Phones.forActive())
    }

    // do not add in `forActive` to return both active AND inactive phones
    static DetachedCriteria<Phone> buildAnyForOwnerIdAndType(Long ownerId, PhoneOwnershipType type) {
        new DetachedCriteria(Phone)
            .build {
                eq("owner.ownerId", ownerId)
                eq("owner.type", type)
            }
    }

    static DetachedCriteria<Phone> buildAllActivePhonesForStaffId(Long staffId) {
        new DetachedCriteria(Phone)
            .build {
                or {
                    and {
                        eq("owner.type", PhoneOwnershipType.INDIVIDUAL)
                        eq("owner.ownerId", staffId)
                    }
                    and {
                        eq("owner.type", PhoneOwnershipType.GROUP)
                        eq("owner.ownerId", Teams
                            .buildForStaffIds([staffId])
                            .build(CriteriaUtils.returnsId()))
                    }
                }
            }
            .build(Phones.forActive())
    }

    static Closure forActive() {
        return {
            isNotNull("numberAsString")
        }
    }

    static Result<Void> canShare(PhoneOwnership owner, PhoneOwnership target) {
        PhoneOwnershipType ownerType = owner.type,
            targetType = target.type
        Boolean canShare
        if (ownerType == PhoneOwnershipType.INDIVIDUAL) {
            if (targetType == PhoneOwnershipType.INDIVIDUAL) { // individual --> individual
                canShare = Teams.hasTeamsInCommon(owner.ownerId, target.ownerId)
            }
            else { // individual --> group
                canShare = Teams.teamContainsMember(target.ownerId, owner.ownerId)
            }
        }
        else {
            if (targetType == PhoneOwnershipType.INDIVIDUAL) { // group --> individual
                canShare = Teams.teamContainsMember(owner.ownerId, target.ownerId)
            }
            else { // group --> group
                canShare = owner.allowSharingWithOtherTeams
            }
        }
        canShare ?
            Result.void() :
            IOCUtils.resultFactory.failWithCodeAndStatus("phone.share.cannotShare",
                ResultStatus.FORBIDDEN, [target.buildName()])
    }
}
