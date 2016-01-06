package org.textup

import grails.gorm.DetachedCriteria
import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap

@Transactional(readOnly=true)
class AuthService {

    def grailsApplication
	def springSecurityService

    ////////////////////
    // Helper methods //
    ////////////////////

    boolean exists(Class clazz, Long id) {
        id ? clazz.exists(id) : false
    }

    Long getLoggedInId() {
    	Staff staff = Staff.findByUsername(springSecurityService.principal?.username)
    	if (!staff) { log.error("AuthService.getLoggedInId: no one is logged in!") }
    	staff?.id
    }

    boolean isLoggedIn(Long sId) {
        getLoggedInId() == sId
    }
    boolean isLoggedInAndActive(Long sId) {
        getLoggedInAndActive()?.id == sId
    }

    Staff getLoggedIn() {
        Staff.findByUsername(springSecurityService.principal?.username)
    }

    Staff getLoggedInAndActive() {
        Staff s1 = getLoggedIn()
        isActive(s1) ? s1 : null
    }

    boolean isActive(Staff s1) {
        s1 && (s1.status == Constants.STATUS_STAFF || s1.status == Constants.STATUS_ADMIN) &&
            s1.org.status == Constants.ORG_APPROVED
    }

    //////////////////
    // Admin status //
    //////////////////

    boolean isAdminAt(Long orgId) {
        Organization org = Organization.get(orgId)
        if (org) {
            Staff.findByIdAndOrgAndStatus(getLoggedInId(), org, Constants.STATUS_ADMIN)
        }
        else { false }
    }

    boolean isAdminAtSameOrgAs(Long sId) {
        Staff s1 = getLoggedIn()
        if (s1) {
            Staff.where { id == sId && org == s1.org }.count() > 0 &&
                s1.status == Constants.STATUS_ADMIN
        }
        else { false }
    }

    boolean isAdminForTeam(Long teamId) {
        Staff s1 = getLoggedIn()
        if (s1) {
            Team.where { id == teamId && org == s1.org }.count() > 0 &&
                s1.status == Constants.STATUS_ADMIN
        }
        else { false }
    }

    ///////////////////////////////////
    // Twilio request authentication //
    ///////////////////////////////////

    boolean authenticateRequest(HttpServletRequest request, GrailsParameterMap params) {
        String browserURL = (request.requestURL.toString() - request.requestURI) + request.forwardURI,
            authToken = grailsApplication.config.textup.apiKeys.twilio.authToken,
            authHeaderName = "x-twilio-signature",
            authHeader = request.getHeader(authHeaderName)
        if (authHeader) {
            if (request.queryString) { browserURL = "$browserURL?${request.queryString}" }
            Map<String,String> twilioParams = extractTwilioParams(request, params)

            StringBuilder data = new StringBuilder(browserURL)
            List<String> sortedKeys = twilioParams.keySet().toList().sort() //sort keys lexicographically
            sortedKeys.each { String key -> data << key << twilioParams[key] }
            //first check https then check http if fails. Scheme detection in request object is spotty
            String dataString = data.toString(), httpsData, httpData
            if (dataString.startsWith("http://")) {
                httpData = dataString
                httpsData = dataString.replace("http://", "https://")
            }
            else if (dataString.startsWith("https://")) {
                httpData = dataString.replace("https://", "http://")
                httpsData = dataString
            }
            else {
                log.error("AuthService.authenticateRequest: invalid data: $dataString")
                return false
            }
            //first try https
            String encoded = Helpers.getBase64HmacSHA1(httpsData.toString(), authToken)
            boolean isAuth = (encoded == authHeader)
            //then fallback to checking http if needed
            if (!isAuth) {
                encoded = Helpers.getBase64HmacSHA1(httpData.toString(), authToken)
                isAuth = (encoded == authHeader)
            }
            isAuth
        }
        else { false }
    }

    protected Map<String,String> extractTwilioParams(HttpServletRequest request, GrailsParameterMap allParams) {
        Collection<String> requestParamKeys = request.parameterMap.keySet(), queryParams = []
        request.queryString?.tokenize("&")?.each { queryParams << it.tokenize("=")[0] }
        HashSet<String> ignoreParamKeys = new HashSet<>(queryParams),
            keepParamKeys = new HashSet<>(requestParamKeys)
        Map<String,String> twilioParams = [:]
        allParams.each { String key, String val ->
            if (keepParamKeys.contains(key) && !ignoreParamKeys.contains(key)) {
                twilioParams[key] = val
            }
        }
        twilioParams
    }

    ////////////////////////
    // Contact permission //
    ////////////////////////

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
            List<Long> tPhoneIds = Helpers.allToLong(Team.teamPhoneIdsForStaffId(s1.id).list())
            Contact.createCriteria().count {
                eq("id", cId)
                or {
                    eq("phone", s1.phone) //(1)
                    if (tPhoneIds) { phone { "in"("id", tPhoneIds) } } //(2)
                }
            } > 0
        }
        else { false }
    }

    /////////////////////////////////
    // Sharing contact permissions //
    /////////////////////////////////

    boolean canShareContactWithStaff(Long cId, Long sId) {
        Contact c1 = Contact.get(cId)
        if (c1 && c1.phone.instanceOf(StaffPhone)) {
            TeamMembership.staffIdsOnSameTeamAs(sId).list().contains(c1.phone.ownerId)
        }
        else { false }
    }

    /**
     * Can have permission for a Contact that is not your's if
     * you are the receipient of an unexpired SharedContact
     * @param  cId  Id of the Contact in question
     * @return      Id of unexpired SharedContact that you have
     *                 permissions for or null otherwise
     */
    Long getSharedContactForContact(Long cId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && cId) {
            List<Long> sWithMeIds = Helpers.allToLong(SharedContact.sharedWithMeIds(s1.phone).list())
            List<Long> scIds = SharedContact.createCriteria().list {
                projections { property("id") }
                if (sWithMeIds) { "in"("id", sWithMeIds) }
                eq("contact.id", cId)
            }
            !scIds.isEmpty() ? scIds[0] : null
        }
        else { null }
    }


    //////////////////////
    // Team permissions //
    //////////////////////

    boolean belongsToSameTeamAs(Long teamId) {
        Staff s1 = getLoggedIn()
        if (s1) { TeamMembership.staffIdsForTeamId(teamId).list().contains(s1.id) }
        else { false }
    }

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
            int memberCount = TeamMembership.where {
                team.id == tId && staff == s1
            }.count()
            if (memberCount > 0) { true } //(1)
            else { // (2)
                int teamCount = Team.where { id == tId && org == s1.org }.count()
                teamCount > 0 && s1.status == Constants.STATUS_ADMIN
            }
        }
        else { false }
    }

    ///////////////////////
    // Staff permissions //
    ///////////////////////

    /**
     * Can have permission for this staff member if
     * (1) You are this staff member
     * (2) You are an admin at this staff member's organization
     * (3) You are on a same team as this staff member
     * @param  sId Id of the staff member in question
     * @return     Whether you have permission for staff member
     */
    boolean hasPermissionsForStaff(Long sId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && sId) {
            DetachedCriteria sQuery = Staff.where { id == sId && org == s1.org }
            (sId == s1.id) || //(1)
            (sQuery.count() > 0 && s1.status == Constants.STATUS_ADMIN) || //(2)
            TeamMembership.staffIdsOnSameTeamAs(sId).list().contains(s1.id) //(3)
        }
        else { false }
    }

    /////////////////////
    // Tag permissions //
    /////////////////////

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
            List<Long> tPhoneIds = Helpers.allToLong(Team.teamPhoneIdsForStaffId(s1.id).list())
            ContactTag.createCriteria().count {
                eq("id", tId)
                or {
                    eq("phone", s1.phone) //(1)
                    if (tPhoneIds) { phone { "in"("id", tPhoneIds) } } //(2)
                }
            } > 0
        }
        else { false }
    }

    /**
     * Determines if the given tag and contact belong
     * to the same owner (phone)
     * @param  t1 Tag id to inspect
     * @param  c1 Contact id to inspect
     * @return    Whether or not the two belong to the same owner
     */
    boolean tagAndContactBelongToSame(Long tId, Long cId) {
        ContactTag t1 = ContactTag.get(tId)
        Contact c1 = Contact.get(cId)
        if (t1 && c1) { t1.phone == c1.phone }
        else { false }
    }

    ////////////////////////
    // Record permissions //
    ////////////////////////

    /**
     * Can have permission for this RecordItem if
     * (1) This item belongs to one of your contacts
     * (2) This item belongs to a contact that is currently shared with you
     * (3) This item belongs to a contact of one of the teams you're on
     * @param  itemId Id of the item in question
     * @return        Whether or have permission
     */
    boolean hasPermissionsForItem(Long itemId) {
        Staff s1 = getLoggedInAndActive()
        if (s1 && itemId) {
            long pId = s1.phone.id
            List<Long> phoneRecIds = Helpers.allToLong(Contact.recordIdsForPhoneId(pId).list()),
                sharedRecIds = Helpers.allToLong(Contact.sharedRecordIdsForSharedWithId(pId).list()),
                teamRecIds = Helpers.allToLong(Contact.teamRecordIdsForStaffId(s1.id).list())
            RecordItem.createCriteria().count {
                eq("id", itemId)
                record {
                    or {
                        if (phoneRecIds) { "in"("id", phoneRecIds) } //(1)
                        if (sharedRecIds) { "in"("id", sharedRecIds) } //(2)
                        if (teamRecIds) { "in"("id", teamRecIds) } //(3)
                    }
                }
            } > 0
        }
        else { false }
    }

    //////////////////////////
    // Parse by permissions //
    //////////////////////////

    /**
     * From a list of contact ids, find any contact ids that the
     * currently logged in staff member does NOT have permission
     * to communicate with OR cannot be found
     * Has permission for contact if
     * (1) This is your contact
     * (2) This contact belongs to one of the teams you are on
     * @param  contactIds List of contact ids to check
     * @return            ParsedResult of valid and invalid ids
     */
    ParsedResult<Long,Long> parseContactIdsByPermission(List<Long> contactIds) {
        Staff s1 = this.getLoggedIn()
        if (s1) {
            List<Long> tPhoneIds = Helpers.allToLong(Team.teamPhoneIdsForStaffId(s1.id).list())
            List<Long> validIds = Contact.createCriteria().list {
                projections { property("id") }
                if (contactIds) { "in"("id", contactIds) }
                or {
                    eq("phone", s1.phone) //(1)
                    if (tPhoneIds) { phone { "in"("id", tPhoneIds) } } //(2)
                }
            }
            Helpers.parseFromList(validIds, contactIds)
        }
        else { new ParsedResult<Long,Long>(invalid:contactIds) }
    }

    /**
     * From a list of contact ids, find any contact ids that
     * do not represent contacts that have been shared with
     * the logged-in staff member
     * @param  contactIds List of contact ids to check
     * @return ParsedResult of valid SharedContacts and invalid contact ids
     */
    ParsedResult<SharedContact,Long> parseIntoSharedContactsByPermission(List<Long> contactIds) {
        Staff s1 = this.getLoggedIn()
        if (s1 && s1.phone) {
            List<SharedContact> validSharedContacts = SharedContact.sharedWithForContactIds(s1.phone, contactIds).list()
            List<Long> validContactIds = validSharedContacts*.contact*.id
            new ParsedResult(invalid:Helpers.parseFromList(validContactIds, contactIds).invalid,
                valid:validSharedContacts)
        }
        else { new ParsedResult<Long,Long>(invalid:contactIds) }
    }

    /**
     * From a list of tag ids, find any tag ids that the
     * currently logged in staff member does NOT have permission
     * to communicate with OR cannot be found
     * Has permission for tag if
     * (1) This tag belongs to you
     * (2) This tag belongs to a team you are on
     * @param  tIds List of tag ids to check
     * @return      ParsedResult of valid and invalid ids
     */
    ParsedResult<Long,Long> parseTagIdsByPermission(List<Long> tIds) {
        Staff s1 = this.getLoggedIn()
        if (s1) {
            List<Long> tPhoneIds = Helpers.allToLong(Team.teamPhoneIdsForStaffId(s1.id).list())
            List<Long> validIds = ContactTag.createCriteria().list {
                projections { property("id") }
                if (tIds) { "in"("id", tIds) }
                or {
                    eq("phone", s1.phone) //(1)
                    if (tPhoneIds) { phone { "in"("id", tPhoneIds) } }//(2)
                }
            }
            Helpers.parseFromList(validIds, tIds)
        }
        else { new ParsedResult<Long,Long>(invalid:tIds) }
    }
}
