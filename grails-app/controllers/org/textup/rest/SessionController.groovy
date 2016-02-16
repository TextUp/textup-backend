package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class SessionController extends BaseController {

	static namespace = "v1"

    // authService from superclass
    SessionService sessionService

    // List
    // ----

    @Transactional(readOnly=true)
    def index() {
    	Phone p1
        if (params.teamId) {
            Team t1 = Team.get(params.long("teamId"))
            if (!t1 || !t1.phone) {
                return notFound()
            }
            else if (!authService.hasPermissionsForTeam(t1.id)) {
                return forbidden()
            }
            p1 = t1.phone
        }
        else {
            Staff s1 = authService.loggedInAndActive
            if (!s1 || !s1.phone) {
                return forbidden()
            }
            p1 = s1.phone
        }
        Closure count, list
        if (params.subscribed == "call") {
        	count = { Map params -> p1.countCallSubscribedSessions() }
        	list = { Map params -> p1.getCallSubscribedSessions(params) }
        }
        else if (params.subscribed == "text") {
        	count = { Map params -> p1.countTextSubscribedSessions() }
        	list = { Map params -> p1.getTextSubscribedSessions(params) }
        }
        else {
        	count = { Map params -> p1.countSessions() }
        	list = { Map params -> p1.getSessions(params) }
        }
        genericListActionForClosures("session", count, list, params)
    }

    // Show
    // ----

    @Transactional(readOnly=true)
    def show() {
    	Long id = params.long("id")
        if (IncomingSession.exists(id)) {
            if (authService.hasPermissionsForSession(id)) {
                genericShowAction(IncomingSession, id)
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    def save() {
    	if (!validateJsonRequest(request, "session")) { return }
    	Map sessInfo = (request.properties.JSON as Map).session as Map
        if (params.long("teamId")) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.hasPermissionsForTeam(tId)) {
                    handleSaveResult("session",
                    	sessionService.createForTeam(tId, sessInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else {
            if (authService.isActive) {
                handleSaveResult("session", sessionService.createForStaff(sessInfo))
            }
            else { forbidden() }
        }
    }

    // Update
    // ------

    def update() {
    	if (!validateJsonRequest(request, "session")) { return }
    	Long id = params.long("id")
        Map sessInfo = (request.properties.JSON as Map).session as Map
    	if (authService.exists(IncomingSession, id)) {
    		if (authService.hasPermissionsForSession(id)) {
    			handleUpdateResult("session", sessionService.update(id, sessInfo))
			}
			else { forbidden() }
    	}
    	else { notFound() }
    }

    // Delete
    // ------

    def delete() { notAllowed() }
}
