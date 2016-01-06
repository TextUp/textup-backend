package org.textup.rest

import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*

@Secured(["ROLE_ADMIN", "ROLE_USER"])
class SocketController extends BaseController {

    static namespace = "v1"
    //grailsApplication from superclass
    //authService from superclass
    def pusherService

    /////////////////
    // Not allowed //
    /////////////////

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }

    // POST for authenticating private channels with Pusher
    def save() {
        String authUsername = request.userPrincipal?.principal?.username,
            channelName = params.channel_name,
            channelUsername = channelName ? (channelName - "private-") : null,
            socketId = params.socket_id
        if ((authUsername && channelUsername && socketId) || (authUsername != channelUsername)) {
            def authResult = pusherService.authenticate(socketId, channelName)
            try {
                withFormat {
                    json {
                        render status:OK
                        respond(new JsonSlurper().parseText(authResult))
                    }
                }
            }
            catch (e) {
                log.error("SocketController.save: could not parse authResult: ${authResult} with error: ${e.message}")
                render status:FORBIDDEN
            }
        }
        else {
            render status:FORBIDDEN
        }
    }
}
