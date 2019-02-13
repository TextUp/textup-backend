package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class ControllerUtils {

    static final int DEFAULT_PAGINATION_MAX = 10
    static final int MAX_PAGINATION_MAX = 5000
    static final String CONTENT_TYPE_PDF = "application/pdf"
    static final String CONTENT_TYPE_XML = "text/xml"
    static final String FORMAT_PDF = "pdf"

    static Result<Long> tryGetPhoneId(Long teamId = null) {
        if (teamId) {
            Teams.isAllowed(teamId).then { Long tId ->
                IOCUtils.phoneCache.mustFindAnyPhoneIdForOwner(tId, PhoneOwnershipType.GROUP)
            }
        }
        else {
            AuthUtils.tryGetAuthId().then { Long authId ->
                IOCUtils.phoneCache.mustFindAnyPhoneIdForOwner(authId, PhoneOwnershipType.INDIVIDUAL)
            }
        }
    }

    static Map<String, ?> buildErrors(Result<?> failRes) {
        [
            errors: failRes?.errorMessages
                ?.collect { String msg -> [message: msg, statusCode: failRes.status.intStatus] }
        ]
    }

    static Map<String, Integer> buildPagination(Map params, Integer optTotal = null) {
        if (params) {
            TypeMap data = TypeMap.create(params)
            Integer offset = Math.max(0, data.int("offset", 0)),
                max = Math.min(Math.max(0, data.int("max", DEFAULT_PAGINATION_MAX)), MAX_PAGINATION_MAX)
            [offset: offset, max: max, total: (optTotal >= 0) ? optTotal : max]
        }
        else {
            [offset: 0, max: DEFAULT_PAGINATION_MAX, total: 0]
        }
    }

    static Map<String, String> buildLinks(Map<String, ?> linkParams, Integer offset, Integer max,
        Integer total) {

        Map<String, String> links = [:]
        // if there is an offset, then we need to include previous link
        if (offset > 0) {
            int prevOffset = (offset - max >= 0) ? offset - max : 0
            links.prev = IOCUtils.linkGenerator
                .link(params: linkParams?.plus(max: max, offset: prevOffset))
        }
        // if there are more results that to display, include next link
        if (offset?.plus(max) < total) {
            int nextOffset = offset + max
            links.next = IOCUtils.linkGenerator
                .link(params: linkParams?.plus(max: max, offset: nextOffset))
        }
        links
    }
}
