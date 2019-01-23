package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
@Transactional
class DuplicateController extends BaseController {

    DuplicateService duplicateService

    @Override
    void index() {
        TypeMap data = TypeMap.create(params)
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
