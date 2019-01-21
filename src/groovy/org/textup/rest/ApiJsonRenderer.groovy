package org.textup.rest

import grails.converters.JSON
import grails.rest.render.*
import grails.util.GrailsWebUtil
import groovy.util.logging.Log4j
import java.io.Writer
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException
import org.codehaus.groovy.grails.web.json.JSONWriter
import org.codehaus.groovy.grails.web.mime.MimeType

@Log4j
class ApiJsonRenderer<T> extends AbstractRenderer<T> {

    String label

    ApiJsonRenderer(Class<T> targetClass) {
        super(targetClass, MimeType.JSON)
    }

    @Override
    void render(T object, RenderContext context) {
        context.setContentType(GrailsWebUtil.getContentType(MimeType.JSON.name, GrailsWebUtil.DEFAULT_ENCODING))

        String view = context.arguments?.view ?: MarshallerUtils.MARSHALLER_DEFAULT
        ApiJson converter = useJsonWithDetail(view, object)
        Writer out = context.writer
        JSONWriter writer = new JSONWriter(out)

        writer.object()
        writer.key(getLabel())
        converter.renderPartial(writer)

        if (context.arguments?.getAt(MarshallerUtils.PARAM_META)) {
            writer.key(MarshallerUtils.PARAM_META)
            converter = context.arguments.getAt(MarshallerUtils.PARAM_META) as ApiJson
            converter.renderPartial(writer)
        }

        if (context.arguments?.getAt(MarshallerUtils.PARAM_LINKS)) {
            writer.key(MarshallerUtils.PARAM_LINKS)
            converter = context.arguments.getAt(MarshallerUtils.PARAM_LINKS) as ApiJson
            converter.renderPartial(writer)
        }

        if (context.arguments?.include) {
            //can be either a collection or single object
            context.arguments.include.each { String key, toBeRendered ->
                writer.key(key)
                converter = useJsonWithDetail(MarshallerUtils.MARSHALLER_DEFAULT, toBeRendered)
                converter.renderPartial(writer)
            }
        }

        writer.endObject()
        out.flush()
        out.close()
    }

    ApiJson useJsonWithDetail(String view, T object) {
        ApiJson converter
        try {
            JSON.use(view) {
                converter = object as ApiJson
            }
        }
        catch (ConverterException e1) {
            log.error "useJsonWithDetail: while rendering `$object`, converter with view `$view` not found: ${e1.message}"
            e1.printStackTrace()
            try {
                JSON.use(MarshallerUtils.MARSHALLER_DEFAULT) {
                    converter = object as ApiJson
                }
            }
            catch (ConverterException e2) {
                log.error "useJsonWithDetail: `default` converter also not found: ${e2.message}"
                e2.printStackTrace()
                converter = object as JSON
            }
        }
        return converter
    }

    String getLabel() {
        if (label) {
            label
        }
        else if (this instanceof ContainerRenderer) {
            MarshallerUtils.FALLBACK_PLURAL
        }
        else {
            MarshallerUtils.FALLBACK_SINGULAR
        }
    }
}
