package org.textup.util

import com.twilio.security.RequestValidator
import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.textup.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class TwilioUtils {

    // Processing request
    // ------------------

    static Result<Void> validate(HttpServletRequest request, TypeConvertingMap params) {
        // step 1: try to extract auth header
        String errCode = "twilioUtils.validate.invalid",
            authHeader = request.getHeader("x-twilio-signature")
        if (!authHeader) {
            return IOCUtils.resultFactory.failWithCodeAndStatus(errCode, ResultStatus.BAD_REQUEST)
        }
        // step 2: build browser url and extract Twilio params
        String url = TwilioUtils.getBrowserURL(request)
        Map<String, String> twilioParams = TwilioUtils.extractTwilioParams(request, params)
        // step 3: build and run request validator. Note that this is the only place
        // where we require the sub-account authToken
        String authToken = TwilioUtils.getAuthToken(params.AccountSid as String)
        RequestValidator validator = new RequestValidator(authToken)

        validator.validate(url, twilioParams, authHeader) ?
            IOCUtils.resultFactory.success() :
            IOCUtils.resultFactory.failWithCodeAndStatus(errCode, ResultStatus.BAD_REQUEST)
    }

    static List<IncomingMediaInfo> buildIncomingMedia(int numMedia, String messageId,
        TypeConvertingMap params) {

        List<IncomingMediaInfo> mediaList = []
        for (int i = 0; i < numMedia; ++i) {
            String contentUrl = params["MediaUrl${i}"],
                contentType = params["MediaContentType${i}"]
            mediaList << new IncomingMediaInfo(url: contentUrl,
                messageId: messageId,
                mimeType: contentType,
                mediaId: TwilioUtils.extractMediaIdFromUrl(contentUrl),
                accountId: params.AccountSid as String)
        }
        mediaList
    }

    static IncomingRecordingInfo buildIncomingRecording(TypeConvertingMap params) {
        new IncomingRecordingInfo(mimeType: MediaType.AUDIO_MP3.mimeType,
            url: params.RecordingUrl as String,
            mediaId: params.RecordingSid as String,
            accountId: params.AccountSid as String)
    }

    // Updating status
    // ---------------

    static boolean shouldUpdateStatus(ReceiptStatus oldStatus, ReceiptStatus newStatus) {
        !oldStatus || oldStatus.isEarlierInSequenceThan(newStatus)
    }

    static boolean shouldUpdateDuration(Integer oldDuration, Integer newDuration) {
        newDuration != null && (oldDuration == null || oldDuration != newDuration)
    }

    // Twiml
    // -----

    static Result<Closure> invalidTwimlInputs(String code) {
        log.error("TwilioUtils.invalidTwimlInputs: invalid inputs in callback for $code")
        IOCUtils.resultFactory.failWithCodeAndStatus("twimlBuilder.invalidCode",
            ResultStatus.BAD_REQUEST, [code])
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Closure> noResponseTwiml() {
        TwilioUtils.wrapTwiml { -> }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Closure> wrapTwiml(Closure body) {
        IOCUtils.resultFactory.success {
            Response {
                body.delegate = delegate
                body()
            }
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
            announces.collect { FeaturedAnnouncement a1 ->
                TwilioUtils.formatAnnouncementForRequest(a1.whenCreated, a1.owner.name, a1.message)
            }
        }
        else { [IOCUtils.getMessage("twimlBuilder.noAnnouncements")] }
    }
    static String formatAnnouncementForRequest(DateTime dt, String identifier, String msg) {
        String timeAgo = new PrettyTime(LCH.getLocale()).format(dt.toDate())
        IOCUtils.getMessage("twimlBuilder.announcement", [timeAgo, identifier, msg])
    }
    static String formatAnnouncementForSend(String identifier, String message) {
        String unsubscribe = IOCUtils.getMessage("twimlBuilder.text.announcementUnsubscribe",
            [Constants.TEXT_TOGGLE_SUBSCRIBE])
        "${identifier}: ${message}. ${unsubscribe}"
    }

    // Helpers
    // -------

    protected static String getAuthToken(String sid) {
        if (sid == Holders.flatConfig["textup.apiKeys.twilio.sid"]) {
            Holders.flatConfig["textup.apiKeys.twilio.authToken"]
        }
        else {
            // uses query cache
            CustomAccountDetails cd1 = CustomAccountDetails.findByAccountId(sid, [cache: true])
            cd1?.authToken ?: ""
        }
    }

    protected static String getBrowserURL(HttpServletRequest request) {
        String browserURL = (request.requestURL.toString() - request.requestURI) +
            TwilioUtils.getForwardURI(request)
        request.queryString ? "$browserURL?${request.queryString}" : browserURL
    }

    protected static Map<String,String> extractTwilioParams(HttpServletRequest request,
        TypeConvertingMap allParams) {

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

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static String getForwardURI(HttpServletRequest request) {
        request.getForwardURI()
    }

    protected static String cleanForSay(String msg) {
        String cleaned = msg
            ?.replaceAll(/(\/|\-)/, "") // remove slashes and dashes because these are pronouned
            ?.replaceAll(/(\d)/, / $0 /) // surround digits with spaces
            ?.replaceAll(/\s+/, " ") // replace multiple sequential spaces with just one
            ?.trim() // trim any surround whitespace
        cleaned ?: ""
    }

    protected static String extractMediaIdFromUrl(String url) {
        url ? url.substring(url.lastIndexOf("/") + 1) : ""
    }
}
