package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.joda.time.format.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class RecordController extends BaseController {

    PdfService pdfService
    RecordService recordService

    @Transactional(readOnly=true)
    @Override
    void index() {
        TypeMap data = TypeMap.create(params)
        ControllerUtils.tryGetPhoneOwner(data.long("teamId"))
            .then { Tuple<Long, PhoneOwnershipType> processed ->
                Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
                    phoneCache.mustFindPhoneIdForOwner(ownerId, type)
                }
            }
            .then { Long pId -> RecordUtils.buildRecordItemRequest(pId, data) }
            .ifFail { Result<?> failRes -> respondWithResult(CLASS, failRes) }
            .thenEnd { RecordItemRequest iReq ->
                if (data.format == ControllerUtils.FORMAT_PDF) {
                    RequestUtils.trySetOnRequest(RequestUtils.PAGINATION_OPTIONS, data)
                    RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, data.string("timezone"))
                    String ts = DateTimeUtils.FILE_TIMESTAMP_FORMAT.print(DateTime.now())
                    respondWithPdf("textup-export-${ts}.pdf", pdfService.buildRecordItems(iReq))
                }
                else {
                    respondWithCriteria(RecordItem,
                        iReq.getCriteria(),
                        data,
                        RecordItems.forSort(true))
                }
            }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        Long id = params.long("id")
        RecordItems.isAllowed(id)
            .then { RecordItems.mustFindForId(id) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @OptimisticLockingRetry
    @Override
    void save() {
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body ->
                ControllerUtils.tryGetPhoneOwner(body.long("teamId")).curry(body)
            }
            .then { TypeMap body, Tuple<Long, PhoneOwnershipType> processed ->
                Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
                    recordService.create(ownerId, type, body)
                }
            }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void update() {
        Long id = params.long("id")
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap data -> RecordItems.isAllowed(id).curry(data) }
            .then { TypeMap data -> recordService.update(id, data) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void delete() {
        Long id = params.long("id")
        RecordItems.isAllowed(id)
            .then { recordService.delete(id) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    // Helpers
    // -------

    protected void respondWithPdf(String fileName, Result<byte[]> pdfRes) {
        // step 1: status
        render(status: pdfRes.status.apiStatus)
        // step 2: payload
        if (pdfRes.success) {
            withPDFFormat {
                InputStream iStream = new ByteArrayInputStream(pdfRes.payload)
                iStream.withCloseable {
                    render(file: iStream,
                        fileName: fileName,
                        contentType: ControllerUtils.CONTENT_TYPE_PDF)
                }
            }
        }
        else { respond(CollectionUtils.buildErrors(pdfRes)) }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected void withPDFFormat(Closure doPDF) {
        withFormat { pdf(doPDF) }
    }
}
