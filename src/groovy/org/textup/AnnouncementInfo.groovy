package org.textup

import grails.compiler.GrailsTypeChecked

// TODO integrate -- see RecordItemReceiptInfo

@GrailsTypeChecked
class AnnouncementInfo {

    final Collection<PhoneNumber> recipients
    final Collection<PhoneNumber> callReceipients
    final Collection<PhoneNumber> textRecipients

    AnnouncementInfo(FeaturedAnnouncement fa1) {
        Collection<AnnouncementReceipt> rpts = AnnouncementReceipt.findAllByAnnouncement(fa1).unique()
        recipients = Collections.unmodifiableCollection(nums(rpts))
        callRecipients = Collections.unmodifiableCollection(nums(byType(rpts, RecordItemType.CALL)))
        callRecipients = Collections.unmodifiableCollection(nums(byType(rpts, RecordItemType.TEXT)))
    }

    // Helpers
    // -------

    protected Collection<AnnouncementReceipt> byType(Collection<AnnouncementReceipt> rpts,
        RecordItemType type) {

        rpts.findAll { AnnouncementReceipt rpt -> rpt.type == type }

    }
    protected Collection<PhoneNumber> nums(Collection<AnnouncementReceipt> rpts) {
        rpts.collect { AnnouncementReceipt rpt -> rpt.session.number }
    }
}
