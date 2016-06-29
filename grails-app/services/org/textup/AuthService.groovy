package org.textup

import grails.gorm.DetachedCriteria
import grails.plugin.springsecurity.SpringSecurityService
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.types.OrgStatus
import org.textup.types.StaffStatus

@Transactional(readOnly=true)
class AuthService {

	SpringSecurityService springSecurityService

    // Logged in
    // ---------

    boolean exists(Class clazz, Long id) {
        id ? clazz.exists(id) : false
    }
    boolean getIsActive() {
        this.isActive(this.loggedIn)
    }
    boolean isActive(Staff s1) {
        s1 && (s1.status == StaffStatus.STAFF || s1.status == StaffStatus.ADMIN) &&
            s1.org.status == OrgStatus.APPROVED
    }
    boolean isLoggedIn(Long sId) {
        getLoggedIn()?.id == sId
    }
    boolean isLoggedInAndActive(Long sId) {
        getLoggedInAndActive()?.id == sId
    }
    Staff getLoggedIn() {
        Staff.findByUsername(springSecurityService.principal?.username)
    }
    Staff getLoggedInAndActive() {
        this.isActive ? this.loggedIn : null
    }

    // Admin
    // -----

    boolean isAdminAt(Long orgId) {
        Organization org = Organization.get(orgId)
        if (org) {
            Staff.findByIdAndOrgAndStatus(this.loggedIn?.id, org, StaffStatus.ADMIN)
        }
        else { false }
    }
    boolean isAdminAtSameOrgAs(Long sId) {
        Staff s1 = this.loggedIn
        if (s1) {
            Staff.where { id == sId && org == s1.org }.count() > 0 &&
                s1.status == StaffStatus.ADMIN
        }
        else { false }
    }
    boolean isAdminForTeam(Long teamId) {
        Staff s1 = this.loggedIn
        if (s1) {
            Team.where { id == teamId && org == s1.org }.count() > 0 &&
                s1.status == StaffStatus.ADMIN
        }
        else { false }
    }

    // Contact
    // -------

    /**
     * Can have permission for this Contact if
     * (1) This is your contact
     * (2) This contact belongs to one of the teams you are on
     * @param  cId Id of the contact in question
     * @return     Whether you have permission
     */
    boolean hasPermissionsForContact(Long cId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && cId) {
            List<Phone> phones = s1.allPhones
            Contact.createCriteria().count {
                eq("id", cId)
                if (phones) {
                    "in"("phone", phones)
                }
                else { eq("phone", null) }
            } > 0
        }
        else { false }
    }

    // Sharing
    // -------

    /**
     * Can have permission for a Contact that is not your's if
     * you are the receipient of an unexpired SharedContact
     * @param  cId  Id of the Contact in question
     * @return      Id of unexpired SharedContact that you have
     *                 permissions for or null otherwise
     */
    Long getSharedContactIdForContact(Long cId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && cId) {
            SharedContact.createCriteria().list(max:1) {
                projections { property("id") }
                eq("contact.id", cId)
                eq("sharedWith", s1?.phone)
                or {
                    isNull("dateExpired") //not expired if null
                    ge("dateExpired", DateTime.now())
                }
            }[0]
        }
        else { null }
    }


    // Team
    // ----

    /**
     * Can have permission for this team if
     * (1) You are on this team
     * (2) You are an admin at this team's organization
     * @param  tId Id of the team in question
     * @return     Whether you have permission
     */
    boolean hasPermissionsForTeam(Long tId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && tId) {
            this.isAdminForTeam(tId) || Team.forStaffs([s1]).count {
                idEq(tId)
            } > 0
        }
        else { false }
    }

    // Staff
    // -----

    /**
     * Can have permission for this staff member if
     * (1) You are this staff member
     * (2) You are an admin at this staff member's organization
     * (3) You are on a same team as this staff member
     * @param  sId Id of the staff member in question
     * @return     Whether you have permission for staff member
     */
    boolean hasPermissionsForStaff(Long sId) {
        Staff loggedIn = getLoggedIn(),
            s2 = Staff.get(sId)
        if (loggedIn && s2) {
            loggedIn == s2 ||
            this.isAdminAtSameOrgAs(s2.id) ||
            (isActive(loggedIn) && loggedIn.sharesTeamWith(s2))
        }
        else { false }
    }

    // Tag
    // ---

    /**
     * Can have permission for this Tag if
     * (1) This tag belongs to you
     * (2) This tag belongs to a team you are on
     * @param  tId Id of the Tag in question
     * @return     Whether you have permission for this Tag
     */
    boolean hasPermissionsForTag(Long tId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && tId) {
            List<Phone> phones = s1.allPhones
            ContactTag.createCriteria().count {
                eq("id", tId)
                if (phones) {
                    "in"("phone", phones)
                }
                else { eq("phone", null) }
            } > 0
        }
        else { false }
    }

    // Record item
    // -----------

    /**
     * Can have permission for this RecordItem if
     * (1) This item belongs to one of your contacts
     * (2) This item belongs to a contact that is currently shared with you
     * (3) This item belongs to a contact of one of the teams you're on
     * (4 - 6) same as 1 through 3 for an item belonging to a tag
     * @param  itemId Id of the item in question
     * @return        Whether or have permission
     */
    boolean hasPermissionsForItem(Long itemId) {
        List<Phone> staffPhones = getLoggedInAndActive()?.allPhones
        if (staffPhones && itemId) {
            Record rec = RecordItem.get(itemId)?.record
            if (!rec) { return false }
            HashSet<Phone> allowedPhones = getPhonesForRecords([rec])
            staffPhones.any { it in allowedPhones }
        }
        else { false }
    }

    HashSet<Phone> getPhonesForRecords(List<Record> recs, boolean includeShared = true) {
        if (!recs) { return [] }
        List<Phone> cPhones = Contact.createCriteria().list {
            projections { property("phone") }
                "in"("record", recs)
            }, tPhones = ContactTag.createCriteria().list {
                projections { property("phone") }
                "in"("record", recs)
            },
            phones = cPhones + tPhones,
            sPhones = []
        // phones from contacts that are shared with me
        if (includeShared) {
            sPhones = SharedContact.createCriteria().list {
                projections { property("sharedWith") }
                contact {
                    "in"("record", recs)
                }
                if (phones) {
                    "in"("sharedBy", phones)
                }
                else { eq("sharedBy", null) }
            }
        }
        new HashSet<Phone>(phones + sPhones)
    }

    // Session
    // -------

    boolean hasPermissionsForSession(Long sId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && sId) {
            List<Phone> phones = s1.allPhones
            IncomingSession.createCriteria().count {
                eq("id", sId)
                if (phones) {
                    "in"("phone", phones)
                }
                else { eq("phone", null) }
            } > 0
        }
        else { false }
    }

    // Announcement
    // ------------

    boolean hasPermissionsForAnnouncement(Long aId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && aId) {
            List<Phone> phones = s1.allPhones
            FeaturedAnnouncement.createCriteria().count {
                eq("id", aId)
                if (phones) {
                    "in"("owner", phones)
                }
                else { eq("owner", null) }
            } > 0
        }
        else { false }
    }
}
