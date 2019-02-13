package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class AnnouncementService implements ManagesDomain.Creater<FeaturedAnnouncement>, ManagesDomain.Updater<FeaturedAnnouncement> {

    OutgoingAnnouncementService outgoingAnnouncementService

    @RollbackOnResultFailure
	Result<FeaturedAnnouncement> create(Long pId, TypeMap body) {
        Phones.mustFindActiveForId(pId)
            .then { Phone p1 ->
                FeaturedAnnouncement.tryCreate(p1, body.dateTime("expiresAt"), body.string("message"))
            }
            .then { FeaturedAnnouncement fa1 -> AuthUtils.tryGetActiveAuthUser().curry(fa1) }
            .then { FeaturedAnnouncement fa1, Staff s1 ->
                outgoingAnnouncementService.send(fa1, Author.create(s1))
            }
            .then { FeaturedAnnouncement fa1 -> DomainUtils.trySave(fa1, ResultStatus.CREATED) }
	}

    @RollbackOnResultFailure
    Result<FeaturedAnnouncement> update(Long aId, TypeMap body) {
        FeaturedAnnouncements.mustFindForId(aId)
            .then { FeaturedAnnouncement fa1 ->
                fa1.expiresAt = body.dateTime("expiresAt")
                DomainUtils.trySave(fa1)
            }
    }
}
