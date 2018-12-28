package org.textup

import com.twilio.base.ResourceSet
import com.twilio.exception.TwilioException
import com.twilio.http.HttpMethod
import com.twilio.rest.api.v2010.account.availablephonenumbercountry.Local
import com.twilio.rest.api.v2010.account.availablephonenumbercountry.LocalReader
import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import com.twilio.rest.api.v2010.account.IncomingPhoneNumberDeleter
import com.twilio.rest.lookups.v1.PhoneNumber as LookupPhoneNumber
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.util.*
import org.textup.validator.*

// [UNTESTED] cannot mock for testing because all Twilio SDK methods are final

@GrailsTypeChecked
@Transactional
class NumberService {

	GrailsApplication grailsApplication
	ResultFactory resultFactory
    TokenService tokenService
    TextService textService

    // Verifying ownership
    // -------------------

    @RollbackOnResultFailure
    Result<Void> startVerifyOwnership(PhoneNumber toNum) {
        // validate to number
        if (!toNum.validate()) {
            return resultFactory.failWithValidationErrors(toNum.errors)
        }
        // Notification number will always be on our main account so no need for customAccountId
        String customAccountId = null
        Utils
            .tryGetNotificationNumber()
            .then { PhoneNumber fromNum -> tokenService.generateVerifyNumber(toNum).curry(fromNum) }
            .then { PhoneNumber fromNum, Token tok1 ->
                String msg = IOCUtils.getMessage("numberService.startVerifyOwnership.message", [tok1.token])
                textService
                    .send(fromNum, [toNum], msg, customAccountId)
                    .logFail("NumberService.startVerifyOwnership from $fromNum to $toNum")
            }
    }

    @RollbackOnResultFailure
    Result<Void> finishVerifyOwnership(String token, PhoneNumber toVerify) {
        tokenService.verifyNumber(token, toVerify)
    }

    // Numbers utility methods
    // -----------------------

    // [UNTESTED] due to mocking constraints
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
            resultFactory.failWithThrowable(e)
        }
    }

    // [UNTESTED] due to mocking constraints
    Result<List<AvailablePhoneNumber>> listNewNumbers(String toMatch, Location loc = null,
    	Integer searchRadius = 200) {
    	try {
    		String query = cleanQuery(toMatch)
    		LocalReader reader = Local
    			.reader("US")
		        .setSmsEnabled(true)
		        .setMmsEnabled(true)
		        .setVoiceEnabled(true)
            // if three numbers then use this as an area code search
            if (query?.size() == 3 && query.isInteger()) {
                reader.setAreaCode(query.toInteger())
            }
            else {
                // only accept queries LONGER THAN 1 character. Twilio's search
                // will throw an "Invalid Pattern Provided" error if given only one character
                // because the search is too broad
                if (query?.size() > 1) {
                    reader.setContains(query)
                }
                // if we are not doing an area code search, also scope results to location
                // to improve relevance of results
                if (loc) {
                    reader
                        .setNearLatLong("${loc.lat},${loc.lon}".toString())
                        .setDistance(searchRadius)
                }
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
            // we accept freeform input so we should expect TwilioException errors
            // return an empty array on error
            log.debug("NumberService.listNewNumbers: ${e.message}")
            resultFactory.success([])
        }
    }

    // [UNTESTED] due to mocking constraints
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
    		resultFactory.failWithThrowable(e)
    	}
    }

    protected cleanQuery(String query) {
    	// only allow these specified valid characters
    	query?.replaceAll(/[^\[0-9a-zA-Z\]\*]/, "") ?: ""
    }

    // Operations on numbers
    // ---------------------

    // [UNTESTED] due to mocking constraints
    Result<IncomingPhoneNumber> changeForNumber(PhoneNumber pNum) {
        try {
            String unavailable = grailsApplication.flatConfig["textup.apiKeys.twilio.unavailable"],
                appId = grailsApplication.flatConfig["textup.apiKeys.twilio.appId"]
            IncomingPhoneNumber iNum = IncomingPhoneNumber
                .creator(pNum.toApiPhoneNumber())
                .setFriendlyName(unavailable)
                .setSmsApplicationSid(appId)
                .setSmsMethod(HttpMethod.POST)
                .setVoiceApplicationSid(appId)
                .setVoiceMethod(HttpMethod.POST)
                .create()
            resultFactory.success(iNum)
        }
        catch (TwilioException e) {
            resultFactory.failWithThrowable(e)
        }
    }

    // [UNTESTED] due to mocking constraints
    Result<IncomingPhoneNumber> changeForApiId(String newApiId) {
        try {
            String unavailable = grailsApplication.flatConfig["textup.apiKeys.twilio.unavailable"],
                appId = grailsApplication.flatConfig["textup.apiKeys.twilio.appId"]
            IncomingPhoneNumber uNum = IncomingPhoneNumber
                .updater(newApiId)
                .setFriendlyName(unavailable)
                .setSmsApplicationSid(appId)
                .setSmsMethod(HttpMethod.POST)
                .setVoiceApplicationSid(appId)
                .setVoiceMethod(HttpMethod.POST)
                .update()
            resultFactory.success(uNum)
        }
        catch (TwilioException e) {
            resultFactory.failWithThrowable(e)
        }
    }

    // [UNTESTED] due to mocking constraints
    Result<Phone> updatePhoneWithNewNumber(IncomingPhoneNumber newNum, Phone p1) {
        String oldApiId = p1.apiId
        p1.apiId = newNum.sid
        p1.number = new PhoneNumber(number:newNum.phoneNumber as String)
        if (oldApiId) {
            freeExistingNumberToInternalPool(oldApiId).then({ resultFactory.success(p1) })
        }
        else { resultFactory.success(p1) }
    }

    // [UNTESTED] due to mocking constraints
    Result<IncomingPhoneNumber> freeExistingNumberToInternalPool(String oldApiId) {
        try {
            String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
            IncomingPhoneNumber uNum = IncomingPhoneNumber
                .updater(oldApiId)
                .setFriendlyName(available)
                .update()
            resultFactory.success(uNum)
        }
        catch (TwilioException e) {
            resultFactory.failWithThrowable(e)
        }
    }

    // [UNTESTED] due to mocking constraints
    Result<Tuple<Collection<String>, Collection<String>>> cleanupInternalNumberPool() {
        try {
            Collection<String> deletedNumberIds = [],
                errorNumberIds = []
            String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
            ResourceSet<IncomingPhoneNumber> iNums = IncomingPhoneNumber
                .reader()
                .setFriendlyName(available)
                .read()
            for (IncomingPhoneNumber iNum in iNums) {
                if (IncomingPhoneNumber.deleter(iNum.sid).delete()) {
                    deletedNumberIds << iNum.sid
                }
                else { errorNumberIds << iNum.sid }
            }
            resultFactory.success(deletedNumberIds, errorNumberIds)
        }
        catch (TwilioException e) {
            resultFactory.failWithThrowable(e)
        }
    }
}
