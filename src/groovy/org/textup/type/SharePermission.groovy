package org.textup.type

import grails.compiler.GrailsTypeChecked
import org.textup.util.*

@GrailsTypeChecked
enum SharePermission {
	DELEGATE("sharePermission.delegate"),
	VIEW("sharePermission.view"),
    NONE("sharePermission.stop")

    private final String messageCode

    SharePermission(String code) {
        messageCode = code
    }

    String buildSummary(Collection<String> names) {
        List<String> namesList = new ArrayList<>(names)
        String namesString = CollectionUtils.joinWithDifferentLast(namesList, ", ", ", and ")
        IOCUtils.getMessage(messageCode, [namesString])
    }
}
