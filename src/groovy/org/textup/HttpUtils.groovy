package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.apache.http.auth.*
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.HttpResponse
import org.apache.http.impl.client.*

@GrailsTypeChecked
@Log4j
class HttpUtils {

    // From http://www.baeldung.com/httpclient-4-basic-authentication
    // Twilio has inconsistent behavior with a basic authentication header string.
    // Therefore, use Credential Provider
    static <T> Result<T> executeBasicAuthRequest(String un, String pwd,
        HttpUriRequest request, Closure<Result<T>> doAction) {

        try {
            UsernamePasswordCredentials authValues = new UsernamePasswordCredentials(un, pwd)
            // See https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/http/examples/client/ClientAuthentication.java
            CredentialsProvider credentials = new BasicCredentialsProvider()
            credentials.setCredentials(AuthScope.ANY, authValues)
            CloseableHttpClient client = HttpClients.custom()
                .setDefaultCredentialsProvider(credentials)
                .build()
            client.withCloseable {
                HttpResponse resp = client.execute(request)
                resp.withCloseable { doAction(resp) }
            }
        }
        catch (Throwable e) {
            log.error("HttpUtils.executeBasicAuthRequest: ${e.message}")
            e.printStackTrace()
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }
}
