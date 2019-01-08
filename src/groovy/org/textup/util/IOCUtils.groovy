package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.plugin.springsecurity.SpringSecurityService
import grails.util.Holders
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.quartz.Scheduler
import org.springframework.context.*
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.textup.*

@GrailsTypeChecked
@Log4j
class IOCUtils {

    static <T> T getBean(Class<T> clazz) {
        Holders.applicationContext.getBean(clazz)>.asType(clazz)
    }

    static SpringSecurityService getSecurity() {
        IOCUtils.getBean(SpringSecurityService)
    }

    static ResultFactory getResultFactory() {
        IOCUtils.getBean(ResultFactory)
    }

    static Scheduler getQuartzScheduler() {
        IOCUtils.getBean(Scheduler)
    }

    static String getWebhookLink(Map linkParams = [:]) {
        IOCUtils.linkGenerator.link(namespace: "v1",
            resource: "publicRecord",
            action: "save",
            absolute: true,
            params: linkParams)
    }

    static String getMessage(String code, List params = []) {
        try {
            IOCUtils.messageSource.getMessage(code, params as Object[], LCH.getLocale())
        }
        catch (NoSuchMessageException e) {
            log.error("IOCUtils.getMessage for code $code with error ${e.message}")
            ""
        }
    }

    static String getMessage(MessageSourceResolvable resolvable) {
        try {
            IOCUtils.messageSource.getMessage(resolvable, LCH.getLocale())
        }
        catch (NoSuchMessageException e) {
            log.error("ResultFactory.getMessage for resolvable $resolvable with error ${e.message}")
            ""
        }
    }

    // Helpers
    // -------

    protected static LinkGenerator getLinkGenerator() {
        IOCUtils.getBean(LinkGenerator)
    }

    protected static MessageSource getMessageSource() {
        IOCUtils.getBean(MessageSource)
    }
}
