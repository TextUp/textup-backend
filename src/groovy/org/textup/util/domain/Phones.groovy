package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.cache.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class Phones {

    @GrailsTypeChecked
    static Result<Phone> mustFindForId(Long pId) {
        Phone p1 = pId ? Phone.get(pId) : null
        if (p1) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("phones.notFoundForId",
                ResultStatus.NOT_FOUND, [pId])
        }
    }

    @GrailsTypeChecked
    static Result<Phone> mustFindActiveForId(Long pId) {
        Phone p1 = pId ? Phone.get(pId) : null
        if (p1?.isActive()) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("phones.notFoundActiveForId",
                ResultStatus.NOT_FOUND, [pId])
        }
    }

    @GrailsTypeChecked
    static Result<Phone> mustFindActiveForNumber(BasePhoneNumber bNum) {
        Phone p1 = bNum ? Phones.buildActiveForNumber(bNum).list(max: 1)[0] : null
        if (p1) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("phones.notFoundForNumber",
                ResultStatus.NOT_FOUND, [bNum])
        }
    }

    static DetachedCriteria<Phone> buildActiveForNumber(BasePhoneNumber bNum) {
        new DetachedCriteria(Phone)
            .build { eq("numberAsString", bNum?.number) }
            .build(Phones.forActive())
    }

    // do not add in `forActive` to return both active AND inactive phones
    static DetachedCriteria<Phone> buildAnyForOwnerIdAndType(Long ownerId, PhoneOwnershipType type) {
        new DetachedCriteria(Phone)
            .build {
                owner {
                    eq("ownerId", ownerId)
                    eq("type", type)
                }
            }
    }

    // Subqueries cannot include an `or` clause or else results in an NPE because of an existing bug.
    // Therefore, we move `or` out of the subquery and return a closure instead of a `DetachedCriteria`
    // see: https://github.com/grails/grails-data-mapping/issues/655
    static Closure activeForPhonePropNameAndStaffId(String phonePropName, Long staffId) {
        return {
            if (phonePropName) {
                or {
                    "in"("${phonePropName}.id", PhoneOwnerships
                        .buildAnyStaffPhonesForStaffId(staffId)
                        .build(PhoneOwnerships.returnsPhoneId()))
                    "in"("${phonePropName}.id", PhoneOwnerships
                        .buildAnyTeamPhonesForStaffId(staffId)
                        .build(PhoneOwnerships.returnsPhoneId()))
                }
                "${phonePropName}" {
                    ClosureUtils.compose(delegate, Phones.forActive())
                }
            }
        }
    }

    static Closure forActive() {
        return {
            isNotNull("numberAsString")
        }
    }

    @GrailsTypeChecked
    static Result<Void> canShare(PhoneOwnership owner, PhoneOwnership target) {
        Boolean canShare
        if (owner && target) {
            PhoneOwnershipType ownerType = owner.type,
                targetType = target.type
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
        }
        canShare ?
            Result.void() :
            IOCUtils.resultFactory.failWithCodeAndStatus("phones.shareForbidden",
                ResultStatus.FORBIDDEN, [owner?.buildName(), target?.buildName()])
    }
}
