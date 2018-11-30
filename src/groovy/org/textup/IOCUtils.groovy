package org.textup

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.quartz.Scheduler
import org.springframework.context.*
import org.springframework.context.i18n.LocaleContextHolder as LCH

@GrailsTypeChecked
@Log4j
class IOCUtils {

    static ResultFactory getResultFactory() {
        Holders
            .applicationContext
            .getBean(ResultFactory) as ResultFactory
    }
    static Scheduler getQuartzScheduler() {
        Holders
            .applicationContext
            .getBean(Scheduler) as Scheduler
    }

    static String getWebhookLink(Map linkParams = [:]) {
        IOCUtils.linkGenerator.link(namespace: "v1",
            resource: "publicRecord",
            action: "save",
            absolute: true,
            params: linkParams)
    }
    protected static LinkGenerator getLinkGenerator() {
        Holders
            .applicationContext
            .getBean(LinkGenerator) as LinkGenerator
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
    protected static MessageSource getMessageSource() {
        Holders
            .applicationContext
            .getBean(MessageSource) as MessageSource
    }
}
