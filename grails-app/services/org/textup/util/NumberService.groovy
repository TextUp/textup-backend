package org.textup.util

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
import org.textup.validator.action.*

// [UNTESTED] cannot mock for testing because all Twilio SDK methods are final

@GrailsTypeChecked
@Transactional
class NumberService {

	GrailsApplication grailsApplication
    TokenService tokenService
    TextService textService

    // Verifying ownership
    // -------------------

    @RollbackOnResultFailure
    Result<Void> startVerifyOwnership(PhoneNumber toNum) {
        // Notification number will always be on our main account so no need for customAccountId
        String customAccountId = null
        DomainUtils.tryValidate(toNum)
            .then { Utils.tryGetNotificationNumber() }
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
        tokenService.findVerifyNumber(token)
            .then { PhoneNumber storedNum ->
                storedNum == toVerify ?
                    Result.void() :
                    IOCUtils.resultFactory.failWithCodeAndStatus(
                        "tokenService.verifyNumber.numbersNoMatch", // TODO
                        ResultStatus.NOT_FOUND)
            }
    }

    // Numbers utility methods
    // -----------------------

    // [UNTESTED] due to mocking constraints
    Result<List<AvailablePhoneNumber>> listExistingNumbers() {
    	try {
    		ResourceSet<IncomingPhoneNumber> iNums = IncomingPhoneNumber
	            .reader()
	            .setFriendlyName(grailsApplication.flatConfig["textup.apiKeys.twilio.available"])
	            .read()
            ResultGroup
                .collect(iNums) { IncomingPhoneNumber iNum ->
                    AvailablePhoneNumber.tryCreateNew(iNum.phoneNumber.endpoint, iNum.sid)
                }
                .toResult(false)
    	}
    	catch (TwilioException e) {
            IOCUtils.resultFactory.failWithThrowable(e, "listExistingNumbers")
        }
    }

    // [UNTESTED] due to mocking constraints
    Result<List<AvailablePhoneNumber>> listNewNumbers(String toMatch, Location loc = null,
    	Integer searchRadius = 200) {
    	try {
    		String query = TwilioUtils.cleanNumbersQuery(toMatch)
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
                        .setNearLatLong("${loc.lat},${loc.lng}".toString())
                        .setDistance(searchRadius)
                }
		   	}
            ResultGroup
                .collect(reader.read()) { Local lNum ->
                    AvailablePhoneNumber
                        .tryCreateExisting(lNum.phoneNumber.endpoint, lNum.isoCountry, lNum.region)
                }
                .toResult(false)
    	}
    	catch (TwilioException e) {
            // we accept freeform input so we should expect TwilioException errors
            // return an empty array on error
            log.debug("NumberService.listNewNumbers: ${e.message}")
            IOCUtils.resultFactory.success([])
        }
    }

    // [UNTESTED] due to mocking constraints
    Result<AvailablePhoneNumber> validateNumber(BasePhoneNumber pNum) {
    	try {
    		LookupPhoneNumber lNum = LookupPhoneNumber
    			.fetcher(pNum.toApiPhoneNumber())
    			.fetch()
            AvailablePhoneNumber.tryCreateExisting(lNum.phoneNumber.endpoint, lNum.countryCode, null)
    	}
    	catch (TwilioException e) {
    		// don't log because we are validating numbers here and expect some to
    		// result in an exception when they are invalid
    		IOCUtils.resultFactory.failWithThrowable(e)
    	}
    }

    // Operations on numbers
    // ---------------------

    // [UNTESTED] due to mocking constraints
    Result<String> changeForNumber(PhoneNumber pNum) {
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
            IOCUtils.resultFactory.success(iNum.sid)
        }
        catch (TwilioException e) {
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }

    // [UNTESTED] due to mocking constraints
    Result<PhoneNumber> changeForApiId(String newApiId) {
        try {
            String unavailable = grailsApplication.flatConfig["textup.apiKeys.twilio.unavailable"],
                appId = grailsApplication.flatConfig["textup.apiKeys.twilio.appId"]
            IncomingPhoneNumber iNum = IncomingPhoneNumber
                .updater(newApiId)
                .setFriendlyName(unavailable)
                .setSmsApplicationSid(appId)
                .setSmsMethod(HttpMethod.POST)
                .setVoiceApplicationSid(appId)
                .setVoiceMethod(HttpMethod.POST)
                .update()
            IOCUtils.resultFactory.success(PhoneNumber.create(iNum.phoneNumber.endpoint))
        }
        catch (TwilioException e) {
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }

    // [UNTESTED] due to mocking constraints
    Result<IncomingPhoneNumber> freeExistingNumberToInternalPool(String oldApiId) {
        try {
            String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
            IncomingPhoneNumber iNum = IncomingPhoneNumber
                .updater(oldApiId)
                .setFriendlyName(available)
                .update()
            IOCUtils.resultFactory.success(iNum)
        }
        catch (TwilioException e) {
            IOCUtils.resultFactory.failWithThrowable(e)
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
            IOCUtils.resultFactory.success(deletedNumberIds, errorNumberIds)
        }
        catch (TwilioException e) {
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }
}
