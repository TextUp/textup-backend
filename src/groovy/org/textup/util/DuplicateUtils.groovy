package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class DuplicateUtils {

    static ResultGroup<MergeGroup> tryBuildMergeGroups(Map<PhoneNumber, ? extends Collection<Long>> numToIds) {
        // step 1: determine which ids are ambiguous
        HashSet<Long> ambiguousIds = findAmbiguousIds(numToIds)
        // step 2: determine target ids to build merge group items
        Map<Long, List<MergeGroupItem>> targetIdToMerges = [:]
            .withDefault { [] as List<MergeGroupItem> }
        numToIds?.each { PhoneNumber possibleNum, Collection<Long> possibleIds ->
            Long mergeTargetId = findMergeTargetId(possibleIds, ambiguousIds)
            if (mergeTargetId) {
                Collection<Long> mergeIds = CollectionUtils.difference(possibleIds, [mergeTargetId])
                targetIdToMerges[mergeTargetId] << MergeGroupItem.create(possibleNum, mergeIds)
            }
        }
        // step 3: package merge group items into merge groups
        ResultGroup<MergeGroup> resGroup = new ResultGroup<>()
        targetIdToMerges.each { Long tId, List<MergeGroupItem> possibleMerges ->
            resGroup << MergeGroup.tryCreate(tId, possibleMerges)
        }
        resGroup
    }

    // Helpers
    // -------

    protected static HashSet<Long> findAmbiguousIds(Map<PhoneNumber, ? extends Collection<Long>> numToIds) {
        Map<Long, HashSet<PhoneNumber>> idToNums = [:].withDefault { new HashSet<PhoneNumber>() }
        numToIds?.each { PhoneNumber pNum, Collection<Long> possibleIds ->
            // num only qualifies as a merge group if the set of contact ids attributable to it has
            // more than one contact id in it. If the set of contact ids only has one id, it is not
            // a merge group because merging by definition requires more than one item
            if (possibleIds.size() > 1) {
                possibleIds.each { Long cId -> idToNums[cId] << pNum }
            }
        }
        // Ambiguous contacts are those that could be part of more than one merge group. Each merge
        // group can have at most one ambiguous contact because having multiple ambiguous contacts
        // makes it unclear which one should be the target for the merge
        HashSet<Long> ambiguousIds = new HashSet<>()
        idToNums.each { Long id, HashSet<PhoneNumber> pNums ->
            if (pNums.size() > 1) { ambiguousIds << id }
        }
        ambiguousIds
    }

    protected static Long findMergeTargetId(Collection<Long> possibleIds, HashSet<Long> ambiguousIds) {
        if (possibleIds != null && ambiguousIds != null) {
            Collection<Long> possibleAmbiguousIds = possibleIds.intersect(ambiguousIds)
            // the ambiguous id has to be the target
            if (possibleAmbiguousIds.size() == 1) {
                possibleAmbiguousIds[0]
            }
            else if (possibleAmbiguousIds.isEmpty()) {
                possibleIds[0]
            }
            else { null } // can't merge if multiple ambiguous ids
        }
        else { null }
    }
}
