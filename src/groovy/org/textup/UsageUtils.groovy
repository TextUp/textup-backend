package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.*
import org.textup.type.*

@GrailsTypeChecked
class UsageUtils {

    static <T extends UsageService.HasActivity> List<T> associateActivity(List<T> activityOwners,
        List<UsageService.ActivityRecord> activityList) {

        Map<BigInteger, T> ownerMap = [:]
        activityOwners.each { T ha1 -> ownerMap[ha1.id] = ha1 }
        activityList.each { UsageService.ActivityRecord a1 ->
            ownerMap.get(a1.ownerId)?.setActivity(a1)
        }
        new ArrayList<T>(ownerMap.values())
    }

    static String getTableName(PhoneOwnershipType type) {
        switch (type) {
            case PhoneOwnershipType.INDIVIDUAL: return "staff"
            case PhoneOwnershipType.GROUP: return "team"
            default: return ""
        }
    }

    static int earliestAvailableYear() {
        RecordItem rItem = RecordItem.first("whenCreated")
        DateTime dt = rItem?.whenCreated ?: DateTime.now()
        dt.year
    }
}
