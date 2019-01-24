package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode(includes = ["session"])
class AnnouncementReceipt implements WithId, CanSave<AnnouncementReceipt> {

	DateTime whenCreated = JodaUtils.now()
	FeaturedAnnouncement announcement
	IncomingSession session
	RecordItemType type

    static mapping = {
        whenCreated type:PersistentDateTime
    }
    static constraints = {
    	announcement validator: { FeaturedAnnouncement fa1, AnnouncementReceipt obj ->
    		if (fa1?.phone?.id != obj.session?.phone?.id) { ["differentPhones"] }
    	}
    }

    static Result<AnnouncementReceipt> tryCreate(FeaturedAnnouncement fa1, IncomingSession is1,
        RecordItemType type) {

        AnnouncementReceipt rpt = new AnnouncementReceipt(type: type, session: is1, announcement: fa1)
        DomainUtils.trySave(rpt, ResultStatus.CREATED)
    }
}
