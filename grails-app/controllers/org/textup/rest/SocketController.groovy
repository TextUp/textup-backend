package org.textup.rest

import com.pusher.rest.Pusher
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.security.access.annotation.Secured
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
@Transactional
class SocketController extends BaseController {

    SocketService socketService

    @Override
    void save() {
        String channelName = params.string("channel_name"),
            socketId = params.string("socket_id")
        respondWithResult(socketService.authenticate(channelName, socketId))
    }
}
