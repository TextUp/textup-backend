package org.textup.rest

import grails.rest.render.ContainerRenderer
import org.codehaus.groovy.grails.web.mime.MimeType

class ApiJsonCollectionRenderer extends ApiJsonRenderer implements ContainerRenderer {
    final Class componentType

    public ApiJsonCollectionRenderer(Class classType) {
        super(Collection)
        componentType = classType
    }

    public ApiJsonCollectionRenderer(Class classType, MimeType... mimeTypes) {
        super(Collection, mimeTypes)
        componentType = classType
    }
}
