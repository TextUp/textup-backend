package org.textup.rest

import grails.compiler.GrailsTypeChecked

// TODO hook up in url mappings

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class DuplicateController {

    @RestApiMethod(description="Finding duplicates", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="duplicates", type="Boolean", required=false,
            paramType=RestApiParamType.QUERY, description='''When true, will disregard all other
            query parameters except for the team or tag id parameters and will look through all
            contacts to try to find possible duplicates for contacts (not shared contacts)''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description='''The staff or team was not found. Or, the
            staff or team specified is not allowed to have contacts.'''),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    def index() { }

    // TODO
    //     if (body.boolean("duplicates")) {
    //         return listForDuplicates(duplicateService.findAllDuplicates(p1.id), body)
    //     }
    //     if (body.boolean("duplicates")) {
    //         listForDuplicates(duplicateService.findDuplicates(contacts*.id), body)
    //     }
    // protected def listForDuplicates(Result<List<MergeGroup>> res, TypeMap body) {
    //     if (!res.success) {
    //         return respondWithResult(Object, res)
    //     }
    //     List<MergeGroup> merges = res.payload
    //     Closure<Integer> count = { merges.size() }
    //     Closure<List<MergeGroup>> list = { merges }
    //     respondWithMany(MergeGroup, count, list, body)
    // }
}
