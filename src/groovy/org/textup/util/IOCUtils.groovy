package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.plugin.springsecurity.SpringSecurityService
import grails.util.Holders
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.quartz.Scheduler
import org.springframework.context.*
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class IOCUtils {

    static SpringSecurityService getSecurity() { getBean(SpringSecurityService) }

    static DaoAuthenticationProvider getAuthProvider() { getBean(DaoAuthenticationProvider) }

    static PhoneCache getPhoneCache() { getBean(PhoneCache) }

    static ResultFactory getResultFactory() { getBean(ResultFactory) }

    static Scheduler getQuartzScheduler() { getBean(Scheduler) }

    static LinkGenerator getLinkGenerator() { getBean(LinkGenerator) }

    static String getWebhookLink(Map linkParams = [:]) {
        IOCUtils.linkGenerator.link(resource: "publicRecord",
            action: "save",
            absolute: true,
            params: linkParams)
    }

    static String getHandleLink(Object handle, Map linkParams = [:]) {
        Map combinedParams = new HashMap(linkParams)
        combinedParams.put(CallbackUtils.PARAM_HANDLE, TypeUtils.to(String, handle))
        getWebhookLink(combinedParams)
    }

    static String getMessage(String code, List params = []) {
        try {
            IOCUtils.messageSource.getMessage(code, params as Object[], LCH.getLocale())
        }
        catch (NoSuchMessageException e) {
            log.error("getMessage: for code $code with error ${e.message}")
            ""
        }
    }

    static String getMessage(MessageSourceResolvable resolvable) {
        try {
            IOCUtils.messageSource.getMessage(resolvable, LCH.getLocale())
        }
        catch (NoSuchMessageException e) {
            log.error("getMessage: for resolvable $resolvable with error ${e.message}")
            ""
        }
    }

    // Helpers
    // -------

    protected static <T> T getBean(Class<T> clazz) {
        Holders.applicationContext.getBean(clazz)
    }

    protected static MessageSource getMessageSource() {
        getBean(MessageSource)
    }
}
