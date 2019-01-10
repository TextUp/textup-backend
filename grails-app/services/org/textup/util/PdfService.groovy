package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.nio.file.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.xml.transform.Result as XMLResult
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
import javax.xml.transform.Templates
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import org.apache.fop.apps.*
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.core.io.Resource
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class PdfService {

    GrailsApplication grailsApplication

    private final String XSLT_PROCESSOR_CLASS = "net.sf.saxon.TransformerFactoryImpl"
    private FopFactory _fopFactory
    private Templates _pdfTransformTemplate

    @PostConstruct
    protected void startUp() {
        _fopFactory = FopFactory.newInstance(new File(".").toURI())
        Resource pdfTransform = grailsApplication.mainContext.getResource("record-items-export.xsl")
        if (!pdfTransform.isReadable()) {
            throw new IllegalArgumentException("Path to pdf XSLT file must be readable")
        }
        // see https://stackoverflow.com/a/3389479
        // use Saxon XSLT processor instead of XalanJ bundled with Apache FOP
        TransformerFactory tFactory = TransformerFactory.newInstance(XSLT_PROCESSOR_CLASS, null)
        _pdfTransformTemplate = tFactory.newTemplates(new StreamSource(pdfTransform.file))
    }

    @PreDestroy
    protected void cleanUp() {
        _fopFactory = null
        _pdfTransformTemplate = null
    }

    Result<byte[]> buildRecordItems(RecordItemRequest itemRequest) {
        if (!itemRequest) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("pdfService.buildRecordItems.noInput",
                ResultStatus.INTERNAL_SERVER_ERROR)
        }
        OutputStream out
        try {
            out = new ByteArrayOutputStream()
            Fop fop = _fopFactory.newFop(MimeConstants.MIME_PDF, out)
            Object xmlString = DataFormatUtils.toXmlString(DataFormatUtils.jsonToObject(itemRequest))
            Source src = new StreamSource(new StringReader(xmlString))
            XMLResult res = new SAXResult(fop.getDefaultHandler())
            buildTransformer().transform(src, res)

            IOCUtils.resultFactory.success(out.toByteArray())
        }
        catch (Throwable e) {
            log.error("PdfService.buildRecordItems: ${e.class}: ${e.message}")
            IOCUtils.resultFactory.failWithThrowable(e)
        }
        finally {
            out?.close()
        }
    }

    Transformer buildTransformer() {
        String imagePath = grailsApplication.flatConfig["textup.export.imagePath"]
        Transformer transformer = _pdfTransformTemplate.newTransformer()
        transformer.setParameter("logoFilePath", imagePath)
        transformer
    }
}
