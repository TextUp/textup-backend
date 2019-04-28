package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@GrailsTypeChecked
class MailData {

    @EqualsAndHashCode
    @Validateable
    static class InvitedStaff implements CanValidate {
        String inviter
        String invitee
        String username
        String password
        String lockCode
        String link = LinkUtils.setupAccount()

        static constraints = {
            link url: true
        }
    }

    @EqualsAndHashCode
    @Validateable
    static class ApprovedStaff implements CanValidate {
        String name
        String username
        String org
        String link = LinkUtils.setupAccount()

        static constraints = {
            link url: true
        }
    }

    @EqualsAndHashCode
    @Validateable
    static class PendingStaff implements CanValidate {
        String staff
        String org
        String link = LinkUtils.adminDashboard()

        static constraints = {
            link url: true
        }
    }

    @EqualsAndHashCode
    @Validateable
    static class RejectedStaff implements CanValidate {
        String name
        String username
    }

    @EqualsAndHashCode
    @Validateable
    static class PendingOrg implements CanValidate {
        String org
        String link = LinkUtils.superDashboard()

        static constraints = {
            link url: true
        }
    }

    @EqualsAndHashCode
    @Validateable
    static class PasswordReset implements CanValidate {
        String name
        String username
        String link

        static constraints = {
            link url: true
        }
    }

    @EqualsAndHashCode
    @Validateable
    static class Notification implements CanValidate {
        String staffName
        String phoneName
        String phoneNumber
        String timePeriodDescription

        String incomingDescription
        String outgoingDescription
        int numIncoming
        int numOutgoing

        String link

        static constraints = {
            link nullable: true, blank: true, url: true
        }
    }
}
