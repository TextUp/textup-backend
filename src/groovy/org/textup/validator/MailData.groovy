package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked // TODO
class MailData {

    @Validateable
    static class InvitedStaff implements CanValidate {
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
    static class ApprovedStaff implements CanValidate {
        final String name
        final String username
        final String org
        final String link = LinkUtils.setupAccount()

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class PendingStaff implements CanValidate {
        final String staff
        final String org
        final String link = LinkUtils.adminDashboard()

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class RejectedStaff implements CanValidate {
        final String name
        final String username
    }

    @Validateable
    static class PendingOrg implements CanValidate {
        final String org
        final String link = LinkUtils.superDashboard()

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class PasswordReset implements CanValidate {
        final String name
        final String username
        final String link

        static constraints = {
            link url: true
        }
    }

    @Validateable
    static class Notification implements CanValidate {
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
