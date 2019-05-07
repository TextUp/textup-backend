package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import groovy.json.JsonSlurper
import org.apache.commons.codec.binary.Base64
import org.apache.http.client.methods.*
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.impl.client.*
import org.apache.http.util.EntityUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.*

@GrailsTypeChecked
@Transactional
class MarketingMailService {

    GrailsApplication grailsApplication

    Result<Void> addEmailToGeneralUpdatesList(String email) {
        String listId = grailsApplication.flatConfig["textup.apiKeys.mailChimp.listIds.generalUpdates"]
        addEmailToList(email, listId)
    }

    Result<Void> addEmailToUsersList(String email) {
        String listId = grailsApplication.flatConfig["textup.apiKeys.mailChimp.listIds.users"]
        addEmailToList(email, listId)
    }

    // Helpers
    // -------

    // adds one email to marketing mail list
    protected Result<Void> addEmailToList(String email, String listId) {
        String apiKey = grailsApplication.flatConfig["textup.apiKeys.mailChimp.apiKey"]
        String encodedAuthKey = Base64.encodeBase64String("user:${apiKey}".bytes)
        StringEntity body = new StringEntity(
            DataFormatUtils.toJsonString(email_address: email, status: "subscribed"),
            ContentType.APPLICATION_JSON);
        HttpUriRequest request = RequestBuilder
            .post()
            .setUri(getApiUri(listId))
            .setHeader("Authorization", "Basic ${encodedAuthKey}")
            .setEntity(body)
            .build()
        HttpUtils.executeRequest(request) { Result.void() }
    }

    protected String getApiUri(String listId) {
        "https://us11.api.mailchimp.com/3.0/lists/${listId}/members"
    }
}
