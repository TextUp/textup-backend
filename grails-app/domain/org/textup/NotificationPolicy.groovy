package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.type.NotificationLevel


import org.springframework.transaction.TransactionStatus



@GrailsTypeChecked
@EqualsAndHashCode
class NotificationPolicy implements Schedulable {

    ResultFactory resultFactory

	private HashSet<Long> _blacklist // transient
	private HashSet<Long> _whitelist // transient
    private Staff _staff // transient

	Long staffId
	NotificationLevel level = NotificationLevel.ALL

    boolean useStaffAvailability = true

    boolean manualSchedule = true
    boolean isAvailable = true
    Schedule schedule

	// black/white list of record ids
	String blacklistData = ""
	String whitelistData = ""

	static transients = ["resultFactory", "_blacklist", "_whitelist", "_staff"]
    // we chose not to implement an existence check for staff id
    // because we know that we will pass in a staff id into this
    // constructor. Since we don't update notification policies
    // to match the current phone owners, it seems needlessly exactly
    // to force existence of staff in the staff id either. If the staff
    // id corresponds to a nonexistence staff member, then it will simply
    // be ignored to no great detriment.
    static constraints = {
    	blacklistData nullable:true, blank:true
    	whitelistData nullable:true, blank:true
        schedule nullable:true
    }
    static mapping = {
        blacklistData type:"text"
        whitelistData type:"text"
    }

    // Hooks
    // -----

    def beforeValidate() {
    	this.blacklistData = dehydrateList(this._blacklist)
    	this.whitelistData = dehydrateList(this._whitelist)
    }

    // Methods
    // -------

    boolean isAvailableNow() {
        useStaffAvailability ? getStaff()?.isAvailableNow() : policyIsAvailableNow()
    }
    protected boolean policyIsAvailableNow() {
        manualSchedule ? isAvailable : schedule?.isAvailableNow()
    }

    Result<Schedule> updateSchedule(Map<String,List<String>> params, String timezone="UTC") {
        if (!schedule) {
            schedule = new WeeklySchedule([:])
            if (!schedule.save()) {
                return resultFactory.failWithValidationErrors(schedule.errors)
            }
        }
        schedule.instanceOf(WeeklySchedule) ?
            (schedule as WeeklySchedule).updateWithIntervalStrings(params, timezone) :
            schedule.update(params)
    }

    boolean canNotifyForAny(Collection<Long> recordIds) {
    	recordIds?.any { Long recordId -> canNotify(recordId) }
    }
    // do NOT integrate notion of availability into this method because we want staff
    // members to still be able to configure per-record notification settings even
    // when they are not available. If we integrated availability into this method
    // then these two concepts would be mixed
 	boolean canNotify(Long recordId) {
 		if (level == NotificationLevel.ALL) {
 			isInBlacklist(recordId) ? false : true
 		}
 		else { // otherwise is NotificationLevel.NONE
 			isInWhitelist(recordId) ? true : false
 		}
 	}
    boolean enable(Long recordId) {
        addToWhitelist(recordId) && removeFromBlacklist(recordId)
    }
    boolean disable(Long recordId) {
        addToBlacklist(recordId) && removeFromWhitelist(recordId)
    }

    // Properties
    // ----------

    boolean addToBlacklist(Long recordId) {
    	getOrBuildBlacklist().add(recordId)
    }
    boolean removeFromBlacklist(Long recordId) {
    	getOrBuildBlacklist().remove(recordId)
    }
    boolean isInBlacklist(Long recordId) {
    	getOrBuildBlacklist().contains(recordId)
    }

    boolean addToWhitelist(Long recordId) {
    	getOrBuildWhitelist().add(recordId)
    }
    boolean removeFromWhitelist(Long recordId) {
    	getOrBuildWhitelist().remove(recordId)
    }
    boolean isInWhitelist(Long recordId) {
    	getOrBuildWhitelist().contains(recordId)
    }

    void setStaffId(Long sId) {
        this.staffId = sId
        this._staff = null // clear transient staff so can be refetch next time needed
    }

    // Helpers
    // -------

    protected Staff getStaff() {
        if (!this._staff) {
            this._staff = Staff.get(this.staffId)
        }
        this._staff
    }
    protected HashSet<Long> getOrBuildBlacklist() {
        if (!this._blacklist) {
            this._blacklist = hydrateList(this.blacklistData)
        }
        this._blacklist
    }
    protected HashSet<Long> getOrBuildWhitelist() {
        if (!this._whitelist) {
            this._whitelist = hydrateList(this.whitelistData)
        }
        this._whitelist
    }

    protected HashSet<Long> hydrateList(String data) {
    	HashSet<Long> hydrated = new HashSet<>()
    	if (!data) {
    		return hydrated
    	}
    	try {
    		(Helpers.toJson(data) as Collection).each { Object val ->
    			Long recordId = Helpers.to(Long, val)
    			if (recordId) { hydrated.add(recordId) }
    		}
    	}
    	catch (Throwable e) {
    		log.error("NotificationPolicy.invalid data: ${e.message} for string '${data}'")
            e.printStackTrace()
    	}
    	hydrated
    }
    protected String dehydrateList(HashSet<Long> data) {
    	data ? Helpers.toJsonString(data) : ""
    }
}
