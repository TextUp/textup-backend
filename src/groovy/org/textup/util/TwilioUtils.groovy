package org.textup.util

import com.twilio.security.RequestValidator
import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class TwilioUtils {

    static final String BODY = "Body"
    static final String CALL_DURATION = "CallDuration"
    static final String DIGITS = "Digits"
    static final String FROM = "From"
    static final String ID_ACCOUNT = "AccountSid"
    static final String ID_CALL = "CallSid"
    static final String ID_PARENT_CALL = "ParentCallSid"
    static final String ID_RECORDING = "RecordingSid"
    static final String ID_TEXT = "MessageSid"
    static final String MEDIA_CONTENT_TYPE_PREFIX = "MediaContentType"
    static final String MEDIA_URL_PREFIX = "MediaUrl"
    static final String NUM_MEDIA = "NumMedia"
    static final String NUM_SEGMENTS = "NumSegments"
    static final String RECORDING_DURATION = "RecordingDuration"
    static final String RECORDING_URL = "RecordingUrl"
    static final String STATUS_CALL = "CallStatus"
    static final String STATUS_DIALED_CALL = "DialCallStatus"
    static final String STATUS_TEXT = "MessageStatus"
    static final String TO = "To"

    static Result<Void> validate(HttpServletRequest request, TypeMap params) {
        Result.void()

        // // TODO
        // // step 1: try to extract auth header
        // String authHeader = request.getHeader("x-twilio-signature")
        // if (!authHeader) {
        //     return IOCUtils.resultFactory.failWithCodeAndStatus("twilioUtils.invalidRequest",
        //         ResultStatus.BAD_REQUEST)
        // }
        // // step 2: build browser url and extract Twilio params
        // String url = RequestUtils.getBrowserURL(request)
        // Map<String, String> twilioParams = extractTwilioParams(request, params)
        // // step 3: build and run request validator. Note that this is the only place
        // // where we require the sub-account authToken
        // String authToken = getAuthToken(params.string(TwilioUtils.ID_ACCOUNT))
        // RequestValidator validator = new RequestValidator(authToken)

        // validator.validate(url, twilioParams, authHeader) ?
        //     Result.void() :
        //     IOCUtils.resultFactory.failWithCodeAndStatus("twilioUtils.invalidRequest",
        //         ResultStatus.BAD_REQUEST)
    }

    static Result<List<IncomingMediaInfo>> tryBuildIncomingMedia(String messageId, TypeMap params) {
        ResultGroup<IncomingMediaInfo> resGroup = new ResultGroup<>()
        Integer numMedia = params.int(TwilioUtils.NUM_MEDIA, 0)
        for (int i = 0; i < numMedia; ++i) {
            resGroup << IncomingMediaInfo.tryCreate(messageId, params, i)
        }
        resGroup.toResult(false)
    }

    static Result<Closure> invalidTwimlInputs(String code) {
        log.error("invalidTwimlInputs: invalid inputs in callback for $code")
        IOCUtils.resultFactory.failWithCodeAndStatus("twilioUtils.invalidCode",
            ResultStatus.BAD_REQUEST, [code])
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Closure> noResponseTwiml() {
        TwilioUtils.wrapTwiml { -> }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Closure> wrapTwiml(Closure body) {
        IOCUtils.resultFactory.success {
            Response { ClosureUtils.compose(delegate, body) }
        }
    }

    static String say(String code, List<Object> args = []) {
        cleanForSay(IOCUtils.getMessage(code, args))
    }

    static String say(BasePhoneNumber pNum) {
        cleanForSay(pNum.number)
    }

    static List<String> formatAnnouncementsForRequest(Collection<FeaturedAnnouncement> announces) {
        if (announces) {
            announces.collect { FeaturedAnnouncement fa1 ->
                TwilioUtils.formatAnnouncementForRequest(fa1.whenCreated, fa1.phone.buildName(), fa1.message)
            }
        }
        else { [IOCUtils.getMessage("twilioUtils.noAnnouncements")] }
    }

    static String formatAnnouncementForRequest(DateTime dt, String identifier, String msg) {
        String timeAgo = new PrettyTime(LCH.getLocale()).format(dt.toDate())
        IOCUtils.getMessage("twilioUtils.announcement", [timeAgo, identifier, msg])
    }

    static String formatAnnouncementForSend(String identifier, String message) {
        String unsubscribe = IOCUtils.getMessage("twilioUtils.announcementUnsubscribe",
            [TextTwiml.BODY_TOGGLE_SUBSCRIBE])
        "${identifier}: ${message}. ${unsubscribe}"
    }

    static String cleanNumbersQuery(String query) {
        // only allow these specified valid characters
        query?.replaceAll(/[^\[0-9a-zA-Z\]\*]/, "") ?: ""
    }

    // Helpers
    // -------

    protected static String getAuthToken(String sid) {
        if (sid == Holders.flatConfig["textup.apiKeys.twilio.sid"]) {
            Holders.flatConfig["textup.apiKeys.twilio.authToken"]
        }
        else {
            // uses query cache
            CustomAccountDetails cad1 = CustomAccountDetails.findByAccountId(sid, [cache: true])
            cad1?.authToken ?: ""
        }
    }

    protected static Map<String,String> extractTwilioParams(HttpServletRequest request,
        TypeMap allParams) {

        // step 1: build list of what to ignore. Params that should be ignored are query params
        // that we append to the callback functions that should be factored into the validation
        Collection<String> requestParamKeys = request.parameterMap.keySet(),
            queryParams = []
        request.queryString?.tokenize("&")?.each { queryParams << it.tokenize("=")[0] }
        HashSet<String> ignoreParamKeys = new HashSet<>(queryParams),
            keepParamKeys = new HashSet<>(requestParamKeys)
        // step 2: collect params
        Map<String,String> twilioParams = [:]
        allParams.each {
            String key = it.key, val = it.value
            if (keepParamKeys.contains(key) && !ignoreParamKeys.contains(key)) {
                twilioParams[key] = val
            }
        }
        twilioParams
    }

    protected static String extractMediaIdFromUrl(String url) {
        url ? url.substring(url.lastIndexOf("/") + 1) : ""
    }

    protected static String cleanForSay(String msg) {
        if (msg) {
            msg.replaceAll(/(\/|\-)/, "") // remove slashes and dashes because these are pronouned
                .replaceAll(/(\d)/, / $0 /) // surround digits with spaces
                .replaceAll(/\s+/, " ") // replace multiple sequential spaces with just one
                .trim() // trim any surround whitespace
        }
        else { "" }
    }
}
