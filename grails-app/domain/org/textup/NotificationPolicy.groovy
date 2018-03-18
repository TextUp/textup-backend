package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import org.textup.type.NotificationLevel

@GrailsCompileStatic
@EqualsAndHashCode
class NotificationPolicy {

	private HashSet<Long> _blacklist // Transient
	private HashSet<Long> _whitelist // Transient

	Long staffId
	NotificationLevel level = NotificationLevel.ALL

	// black/white list of record ids
	String blacklistData = ""
	String whitelistData = ""

	static transients = ["_blacklist", "_whitelist"]
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

    boolean canNotifyForAny(Collection<Long> recordIds) {
    	recordIds?.any { Long recordId -> canNotify(recordId) }
    }
 	boolean canNotify(Long recordId) {
 		if (this.level == NotificationLevel.ALL) {
 			this.isInBlacklist(recordId) ? false : true
 		}
 		else { // otherwise is NotificationLevel.NONE
 			this.isInWhitelist(recordId) ? true : false
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

    // Helpers
    // -------

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
