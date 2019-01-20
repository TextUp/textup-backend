package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class DuplicateService {

    Result<List<MergeGroup>> findAllDuplicates(Long phoneId) {
        IndividualPhoneRecords.tryFindEveryIdByNumbers(phoneId)
            .then { Map<String, HashSet<Long>> numToIds ->
                DuplicateUtils.tryBuildMergeGroups(numToIds).toResult(false)
            }
    }

    Result<List<MergeGroup>> findDuplicates(Collection<Long> iprIds) {
    	if (!iprIds) {
            return IOCUtils.resultFactory.failWithCodeAndStatus(
                "duplicateService.findDuplicates.missingContactIds",
                ResultStatus.UNPROCESSABLE_ENTITY)
    	}
        IndividualPhoneRecords.tryFindEveryIdByNumbers(null, iprIds)
            .then { Map<String, HashSet<Long>> numToIds ->
                DuplicateUtils.tryBuildMergeGroups(numToIds).toResult(false)
            }
    }
}
