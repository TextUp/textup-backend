package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*
import org.joda.time.DateTime

@GrailsTypeChecked
@Transactional
class AnnouncementService {

	AuthService authService
	ResultFactory resultFactory

	// Create
	// ------

    Result<FeaturedAnnouncement> createForTeam(Long tId, Map body) {
    	create(Team.get(tId)?.phone, body)
    }
	Result<FeaturedAnnouncement> createForStaff(Map body) {
		create(authService.loggedInAndActive?.phone, body)
	}
	protected Result<FeaturedAnnouncement> create(Phone p1, Map body) {
		if (!p1) {
			return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
				"announcementService.create.noPhone")
		}
        String msg = body.message as String
        DateTime expires = Helpers.toUTCDateTime(body.expiresAt)
        Staff loggedIn = authService.loggedInAndActive
        p1.sendAnnouncement(msg, expires, loggedIn)
	}

    // Update
    // ------

    Result<FeaturedAnnouncement> update(Long aId, Map body) {
    	FeaturedAnnouncement announce = FeaturedAnnouncement.get(aId)
    	if (!announce) {
    		return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "announcementService.update.notFound", [aId])
    	}
        announce.expiresAt = Helpers.toUTCDateTime(body.expiresAt)
        if (announce.save()) {
            resultFactory.success(announce)
        }
        else {
            resultFactory.failWithValidationErrors(announce.errors)
        }
    }
}
