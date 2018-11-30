package org.textup.rest

import com.pusher.rest.Pusher
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class SocketController extends BaseController {

    static String namespace = "v1"

    //grailsApplication from superclass
    AuthService authService
    Pusher pusherService

    @Override
    protected String getNamespaceAsString() { namespace }

    // POST for authenticating private channels with Pusher
    def save() {
        String authUsername = ((request.userPrincipal as Authentication)
                ?.principal as UserDetails)?.username,
            channelName = params.channel_name,
            channelUsername = channelName ? (channelName - "private-") : null,
            socketId = params.socket_id
        if ((authUsername && channelUsername && socketId) &&
            authUsername == channelUsername) {
            String authResult = pusherService.authenticate(socketId, channelName)
            try {
                render status:ResultStatus.OK.apiStatus
                respond(DataFormatUtils.jsonToObject(authResult))
            }
            catch (Throwable e) {
                log.error("SocketController.save: could not parse authResult: \
                    ${authResult} with error: ${e.message}")
                e.printStackTrace()
                render status:ResultStatus.FORBIDDEN.apiStatus
            }
        }
        else {
            render status:ResultStatus.FORBIDDEN.apiStatus
        }
    }

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }
}
