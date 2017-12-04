package org.textup

import com.twilio.base.ResourceSet
import com.twilio.exception.TwilioException
import com.twilio.rest.api.v2010.account.availablephonenumbercountry.Local
import com.twilio.rest.api.v2010.account.availablephonenumbercountry.LocalReader
import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import com.twilio.rest.lookups.v1.PhoneNumber as LookupPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.validator.AvailablePhoneNumber
import org.textup.validator.BasePhoneNumber

@GrailsTypeChecked
@Transactional
class NumberService {

	GrailsApplication grailsApplication
	ResultFactory resultFactory

    Result<List<AvailablePhoneNumber>> listExistingNumbers() {
    	try {
    		String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
    		List<AvailablePhoneNumber> aNums = []
    		ResourceSet<IncomingPhoneNumber> iNums = IncomingPhoneNumber
	            .reader()
	            .setFriendlyName(available)
	            .read()
	        for (iNum in iNums) {
	        	AvailablePhoneNumber aNum = new AvailablePhoneNumber()
	        	aNum.phoneNumber = iNum.phoneNumber.endpoint
	        	aNum.sid = iNum.sid
	        	if (aNum.validate()) { aNums << aNum }
	        	else {
	        		return resultFactory.failWithValidationErrors(aNum.errors)
	        	}
	        }
	        resultFactory.success(aNums)
    	}
    	catch (TwilioException e) {
            log.error("NumberService.listExistingNumbers: ${e.message}")
            resultFactory.failWithThrowable(e, false) // don't rollback transaction
        }
    }

    Result<List<AvailablePhoneNumber>> listNewNumbers(String toMatch, Location loc,
    	Integer searchRadius = 200) {
    	try {
    		String query = cleanQuery(toMatch)
    		LocalReader reader = Local
    			.reader("US")
		        .setSmsEnabled(true)
		        .setMmsEnabled(true)
		        .setVoiceEnabled(true)
		    if (loc) {
		    	reader
		    		.setNearLatLong("${loc.lat},${loc.lon}".toString())
		        	.setDistance(searchRadius)
		    }
		   	if (query) {
		   		reader.setContains(query)
		   	}
		   	ResourceSet<Local> lNums = reader.read()
		   	List<AvailablePhoneNumber> aNums = []
		    for (lNum in lNums) {
		    	AvailablePhoneNumber aNum = new AvailablePhoneNumber()
		    	aNum.phoneNumber = lNum.phoneNumber.endpoint
		    	aNum.region = "${lNum.region}, ${lNum.isoCountry}"
		    	if (aNum.validate()) { aNums << aNum }
		    	else {
		    		return resultFactory.failWithValidationErrors(aNum.errors)
		    	}
		    }
		    resultFactory.success(aNums)
    	}
    	catch (TwilioException e) {
            log.error("NumberService.listNewNumbers: ${e.message}")
            resultFactory.failWithThrowable(e, false) // don't rollback transaction
        }
    }

    Result<AvailablePhoneNumber> validateNumber(BasePhoneNumber pNum) {
    	try {
    		LookupPhoneNumber returnedNum = LookupPhoneNumber
    			.fetcher(pNum.toApiPhoneNumber())
    			.fetch()
    		AvailablePhoneNumber aNum = new AvailablePhoneNumber()
    		aNum.phoneNumber = returnedNum.phoneNumber.endpoint
    		aNum.region = returnedNum.countryCode
    		if (aNum.validate()) {
    			resultFactory.success(aNum)
    		}
    		else { resultFactory.failWithValidationErrors(aNum.errors) }
    	}
    	catch (TwilioException e) {
    		// don't log because we are validating numbers here and expect some to
    		// result in an exception when they are invalid
    		resultFactory.failWithThrowable(e, false) // don't rollback transaction
    	}
    }

    // Helpers
    // -------

    protected cleanQuery(String query) {
    	// only allow these specified valid characters
    	query?.replaceAll(/[^\[0-9a-zA-Z\]\*]/, "") ?: ""
    }
}
