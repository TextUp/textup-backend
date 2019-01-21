import org.textup.util

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class ControllerUtils {

    static final String CONTENT_TYPE_PDF = "application/pdf"
    static final String CONTENT_TYPE_XML = "text/xml"
    static final String FORMAT_PDF = "pdf"

    private static final int DEFAULT_PAGINATION_MAX = 10
    private static final int MAX_PAGINATION_MAX = 5000

    static Result<Long> tryGetPhoneId(Long teamId = null) {

    }

    // TODO
    // static Result<Tuple<Long, PhoneOwnershipType>> tryGetPhoneOwner(Long teamId = null) {
    //     if (teamId) {
    //         Teams.isAllowed(body.long("teamId"))
    //             .then { Long tId -> IOCUtils.resultFactory.success(tId, PhoneOwnershipType.GROUP) }
    //     }
    //     else {
    //         AuthService.tryGetAuthId().then { Long authId ->
    //             IOCUtils.resultFactory.success(authId, PhoneOwnershipType.INDIVIDUAL)
    //         }
    //     }
    // }

    static Map<String, ?> buildErrors(Result<?> failRes) {
        [
            errors: failRes?.errorMessages
                ?.collect { String msg -> [message: msg, statusCode: failRes.status.intStatus] }
        ]
    }

    static Map<String, Integer> buildPagination(TypeMap data, Integer optTotal = null) {
        if (data) {
            Integer offset = data.int("offset", 0),
                max = Math.min(data.int("max", DEFAULT_PAGINATION_MAX), MAX_PAGINATION_MAX)
            [
                offset: data.int("offset", 0),
                max: max,
                total: (optTotal >= 0) ? optTotal : max
            ]
        }
        else { [:] }
    }

    static Map<String, String> buildLinks(Map<String, ?> linkParams, Integer offset, Integer max,
        Integer total) {

        Map<String, String> links = [:]
        // if there is an offset, then we need to include previous link
        if (offset > 0) {
            int prevOffset = (offset - max >= 0) ?: 0
            links.prev = IOCUtils.linkGenerator
                .link(linkParams + [params: [max: max, offset: prevOffset]])
        }
        // if there are more results that to display, include next link
        if ((offset + max) < total) {
            int nextOffset = offset + max
            links.next = IOCUtils.linkGenerator
                .link(linkParams + [params: [max: max, offset: nextOffset]])
        }
        links
    }
}
