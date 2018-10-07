package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.textup.type.*

@GrailsTypeChecked
@Log4j
class TwilioUtils {

    // Processing request
    // ------------------

    static String getBrowserURL(HttpServletRequest request) {
        String browserURL = (request.requestURL.toString() - request.requestURI) +
            TwilioUtils.getForwardURI(request)
        request.queryString ? "$browserURL?${request.queryString}" : browserURL
    }

    static Map<String,String> extractTwilioParams(HttpServletRequest request,
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

    static List<IncomingMediaInfo> buildIncomingMedia(int numMedia, String messageId,
        TypeConvertingMap params) {

        List<IncomingMediaInfo> mediaList = []
        for (int i = 0; i < numMedia; ++i) {
            String contentUrl = params["MediaUrl${i}"],
                contentType = params["MediaContentType${i}"]
            mediaList << new IncomingMediaInfo(url: contentUrl,
                messageId: messageId,
                mimeType: contentType,
                mediaId: TwilioUtils.extractMediaIdFromUrl(contentUrl))
        }
        mediaList
    }

    static IncomingRecordingInfo buildIncomingRecording(TypeConvertingMap params) {
        new IncomingRecordingInfo(mimeType: MediaType.AUDIO_MP3.mimeType,
            url: params.RecordingUrl as String,
            mediaId: params.RecordingSid as String)
    }

    // Twiml
    // -----

    static Result<Closure> invalidTwimlInputs(String code) {
        log.error("TwilioUtils.invalidTwimlInputs: invalid inputs in callback for $code")
        Helpers.resultFactory.failWithCodeAndStatus("twimlBuilder.invalidCode",
            ResultStatus.BAD_REQUEST, [code])
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Closure> noResponseTwiml() {
        TwilioUtils.wrapTwiml { -> }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Closure> wrapTwiml(Closure body) {
        Helpers.resultFactory.success {
            Response {
                body.delegate = delegate
                body()
            }
        }
    }

    static List<String> formatAnnouncementsForRequest(Collection<FeaturedAnnouncement> announces) {
        if (announces) {
            announces.collect { FeaturedAnnouncement a1 ->
                TwilioUtils.formatAnnouncementForRequest(a1.whenCreated, a1.owner.name, a1.message)
            }
        }
        else { [Helpers.getMessage("twimlBuilder.noAnnouncements")] }
    }
    static String formatAnnouncementForRequest(DateTime dt, String identifier, String msg) {
        String timeAgo = new PrettyTime(LCH.getLocale()).format(dt.toDate())
        Helpers.getMessage("twimlBuilder.announcement", [timeAgo, identifier, msg])
    }
    static String formatAnnouncementForSend(String identifier, String message) {
        String unsubscribe = Helpers.getMessage("twimlBuilder.text.announcementUnsubscribe",
            [Constants.TEXT_TOGGLE_SUBSCRIBE])
        "${identifier}: ${message}. ${unsubscribe}"
    }

    // Helpers
    // -------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static String getForwardURI(HttpServletRequest request) {
        request.getForwardURI()
    }

    protected static String extractMediaIdFromUrl(String url) {
        url ? url.substring(url.lastIndexOf("/") + 1) : ""
    }
}
