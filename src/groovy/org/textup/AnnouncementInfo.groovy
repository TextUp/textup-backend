package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.type.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
class AnnouncementInfo {

    final Collection<PhoneNumber> recipients
    final Collection<PhoneNumber> callRecipients
    final Collection<PhoneNumber> textRecipients

    static AnnouncementInfo create(FeaturedAnnouncement fa1) {
        Collection<AnnouncementReceipt> rpts = AnnouncementReceipt.findAllByAnnouncement(fa1).unique()
        new AnnouncementInfo(Collections.unmodifiableCollection(nums(rpts)),
            Collections.unmodifiableCollection(nums(byType(rpts, RecordItemType.CALL))),
            Collections.unmodifiableCollection(nums(byType(rpts, RecordItemType.TEXT))))
    }

    // Helpers
    // -------

    protected static Collection<AnnouncementReceipt> byType(Collection<AnnouncementReceipt> rpts,
        RecordItemType type) {

        rpts.findAll { AnnouncementReceipt rpt -> rpt.type == type }

    }
    protected static Collection<PhoneNumber> nums(Collection<AnnouncementReceipt> rpts) {
        rpts.collect { AnnouncementReceipt rpt -> rpt.session.number }
    }
}
