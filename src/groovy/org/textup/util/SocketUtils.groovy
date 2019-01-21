import org.textup.util

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class SocketUtils {

    private static final SOCKET_PREFIX = "private-"

    static String channelName(Staff s1) {
        userToChannelName(s1.username)
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
