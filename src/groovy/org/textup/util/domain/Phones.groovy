package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Phones {

    static Result<Phone> mustFindActiveForOwner(Long ownerId, PhoneOwnershipType type,
        boolean createIfAbsent) {

        Phone p1 = Holders.applicationContext.getBean(PhoneCache).findPhone(ownerId, type)
        if (p1) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            if (createIfAbsent) {
                Phone.tryCreate(ownerId, type)
            }
            else { // TODO add message
                IOCUtils.resultFactory.failWithCodeAndStatus("phone.notFound", ResultStatus.NOT_FOUND)
            }
        }
    }

    // TODO define "active" for phone
    static Result<Phone> mustFindActiveForId(Long pId) {
        Phone p1 = Phone.get(pId)
        if (p1) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus(
                "voicemailService.finishedProcessingVoicemailGreeting.phoneNotFound", // TODO
                ResultStatus.NOT_FOUND, [pId])
        }
    }

    // TODO define "active" for phone
    static Result<Phone> mustFindActiveForNumber(BasePhoneNumber bNum) {
        Phone p1 = Phones.buildForNumber(bNum).list()[0]
        if (p1) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("", // TODO
                ResultStatus.NOT_FOUND, [bNum])
        }
    }

    static DetachedCriteria<Phone> buildForNumber(BasePhoneNumber bNum) {
        new DetachedCriteria(Phone)
            .build { eq("numberAsString", bNum.number) }
            .build(Phones.forActive())
    }

    static DetachedCriteria<Phone> buildForOwnerIdAndType(Long ownerId, PhoneOwnershipType type) {
        new DetachedCriteria(Phone)
            .build {
                eq("owner.ownerId", ownerId)
                eq("owner.type", type)
            }
            .build(Phones.forActive())
    }

    static DetachedCriteria<Phone> buildAllPhonesForStaffId(Long staffId) {
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
        return { // TODO update as we change the definition of what it means for a phone to be active
            isNotNull("numberAsString")
        }
    }

    static Result<Void> canShare(PhoneOwnership owner, PhoneOwnership target) {
        PhoneOwnershipType ownerType = owner.type,
            targetType = target.type
        Boolean canShare
        if (ownerType == PhoneOwnershipType.INDIVIDUAL) {
            if (targetType == PhoneOwnershipType.INDIVIDUAL) {
                canShare = Teams.hasTeamsInCommon(owner.ownerId, target.ownerId)
            }
            else { // individual --> group
                canShare = Teams.teamContainsMember(target.ownerId, owner.ownerId)
            }
        }
        else {
            if (targetType == PhoneOwnershipType.INDIVIDUAL) {
                canShare = Teams.teamContainsMember(owner.ownerId, target.ownerId)
            }
            else { // group --> group
                canShare = owner.allowSharingWithOtherTeams
            }
        }
        canShare ?
            IOCUtils.resultFactory.success() :
            IOCUtils.resultFactory.failWithCodeAndStatus("phone.share.cannotShare",
                ResultStatus.FORBIDDEN, [target.buildName()])
    }
}
