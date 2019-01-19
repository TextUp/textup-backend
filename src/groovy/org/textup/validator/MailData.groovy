package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable

@GrailsTypeChecked
class MailData {

    @Validateable
    static class InvitedStaff {
        final String inviter
        final String invitee
        final String username
        final String password
        final String lockCode
        final String link = LinkUtils.setupAccount()

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class ApprovedStaff {
        final String name
        final String username
        final String org
        final String link = LinkUtils.setupAccount()

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class PendingStaff {
        final String staff
        final String org
        final String link = link: LinkUtils.adminDashboard()

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class RejectedStaff {
        final String name
        final String username
    }

    @Validateable
    static class PendingOrg {
        final String org
        final String link = LinkUtils.superDashboard()

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class PasswordReset {
        final String name
        final String username
        final String link

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class Notification {
        final String staffName
        final String phoneName
        final String phoneNumber
        final String timePeriodDescription

        final String incomingDescription
        final String outgoingDescription
        final int numIncoming
        final int numOutgoing

        final String link

        static constraints = {
            link nullable: true, blank: true, url: true
        }
    }
}
