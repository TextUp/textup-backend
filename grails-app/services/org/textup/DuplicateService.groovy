package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class DuplicateService {

    Result<List<MergeGroup>> findDuplicates(Long phoneId, Collection<Long> iprIds = null) {
        Map<PhoneNumber, HashSet<Long>> numToIds = IndividualPhoneRecords
            .findNumToIdByPhoneIdAndOptions(phoneId, iprIds)
        DuplicateUtils.tryBuildMergeGroups(numToIds).toResult(false)
    }
}
