package org.textup.rest

import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.converters.configuration.*
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@Transactional
class MarshallerInitializerService implements ApplicationContextAware {

    ApplicationContext applicationContext

    void initialize() {
        registerNamedJsonMarshallers()
    }

    protected registerNamedJsonMarshallers() {
        Map configs = [:]
        for (Object o : applicationContext.getBeansOfType(JsonNamedMarshaller.class).values()) {
            JsonNamedMarshaller namedMarshaller = (JsonNamedMarshaller) o
            String name = namedMarshaller.name

            if (!configs[name]) {
                ConverterConfiguration config = ConvertersConfigurationHolder.getConverterConfiguration(JSON.class)
                configs[name] = new DefaultConverterConfiguration<JSON>(config)
            }

            DefaultConverterConfiguration<JSON> config = configs[name]
            config.registerObjectMarshaller(namedMarshaller.marshaller)
            ConvertersConfigurationHolder.setNamedConverterConfiguration(JSON.class, name, config)
        }
    }
}
