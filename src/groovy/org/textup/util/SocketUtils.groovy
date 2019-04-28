package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class SocketUtils {

    private static final SOCKET_PREFIX = "private-"

    static String channelName(ReadOnlyStaff rs1) {
        userToChannelName(rs1.username)
    }

    static String channelToUserName(String channelName) {
        channelName ? channelName - SOCKET_PREFIX : ""
    }

    // Helpers
    // -------

    protected static String userToChannelName(String un) {
        un ? "${SOCKET_PREFIX}${un}" : ""
    }
}
