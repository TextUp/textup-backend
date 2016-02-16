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
class AnnouncementController extends BaseController {

	static namespace = "v1"

    // authService from superclass
    AnnouncementService announcementService

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
        genericListActionForClosures("announcement", { Map params ->
        	p1.countAnnouncements()
    	}, { Map params ->
    		p1.getAnnouncements(params)
		}, params)
    }

    // Show
    // ----

    @Transactional(readOnly=true)
    def show() {
    	Long id = params.long("id")
        if (FeaturedAnnouncement.exists(id)) {
            if (authService.hasPermissionsForAnnouncement(id)) {
                genericShowAction(FeaturedAnnouncement, id)
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    def save() {
    	if (!validateJsonRequest(request, "announcement")) { return }
    	Map aInfo = (request.properties.JSON as Map).announcement as Map
        if (params.long("teamId")) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.hasPermissionsForTeam(tId)) {
                    handleSaveResult("announcement",
                    	announcementService.createForTeam(tId, aInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else {
            if (authService.isActive) {
                handleSaveResult("announcement",
                	announcementService.createForStaff(aInfo))
            }
            else { forbidden() }
        }
    }

    // Update
    // ------

    def update() {
    	if (!validateJsonRequest(request, "announcement")) { return }
    	Long id = params.long("id")
        Map aInfo = (request.properties.JSON as Map).announcement as Map
    	if (authService.exists(FeaturedAnnouncement, id)) {
    		if (authService.hasPermissionsForAnnouncement(id)) {
    			handleUpdateResult("announcement",
    				announcementService.update(id, aInfo))
			}
			else { forbidden() }
    	}
    	else { notFound() }
    }

    // Delete
    // ------

    def delete() { notAllowed() }
}
