package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
@Transactional
class DuplicateController extends BaseController {

    DuplicateService duplicateService

    @Override
    void index() {
        TypedMap data = TypedMap.create(params)
        ControllerUtils.tryGetPhoneId(data.long("teamId"))
            .then { Long pId ->
                Long iprIds = data.typedList(Long, "ids[]")
                duplicateService.findDuplicates(pId, iprIds)
            }
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { List<MergeGroup> mgs ->
                respondWithMany({ mgs.size() }, { mgs }, data, MarshallerUtils.KEY_MERGE_GROUP)
            }
    }
}
