package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.apache.http.auth.*
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.HttpResponse
import org.apache.http.impl.client.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class HttpUtils {

    static final String PROTOCOL_HTTP = "http"
    static final String PROTOCOL_HTTPS = "https"

    static <T> Result<T> executeRequest(HttpUriRequest request, Closure<Result<T>> onSuccess) {
        try {
            CloseableHttpClient client = HttpClientBuilder.create().build()
            executeRequestWithClient(client, request, onSuccess)
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "executeRequest", true)
        }
    }

    // From http://www.baeldung.com/httpclient-4-basic-authentication
    // Twilio has inconsistent behavior with a basic authentication header string.
    // Therefore, use Credential Provider
    static <T> Result<T> executeBasicAuthRequest(String un, String pwd,
        HttpUriRequest request, Closure<Result<T>> onSuccess) {

        try {
            UsernamePasswordCredentials authValues = new UsernamePasswordCredentials(un, pwd)
            // See https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/http/examples/client/ClientAuthentication.java
            CredentialsProvider credentials = new BasicCredentialsProvider()
            credentials.setCredentials(AuthScope.ANY, authValues)
            CloseableHttpClient client = HttpClients.custom()
                .setDefaultCredentialsProvider(credentials)
                .build()
            executeRequestWithClient(client, request, onSuccess)
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "executeBasicAuthRequest", true)
        }
    }

    // Helpers
    // -------

    protected static <T> Result<T> executeRequestWithClient(CloseableHttpClient client,
        HttpUriRequest request, Closure<Result<T>> onSuccess) {

        try {
            client.withCloseable {
                HttpResponse resp = client.execute(request)
                resp.withCloseable {
                    ResultStatus status = ResultStatus.convert(resp.statusLine.statusCode)

                    if (status.isSuccess) {
                        ClosureUtils.execute(onSuccess, [resp])
                    }
                    else {
                        IOCUtils.resultFactory.failWithCodeAndStatus("httpUtils.requestFail",
                            status, [resp.statusLine.reasonPhrase])
                    }
                }
            }
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "executeRequestWithClient", true)
        }
    }

}
