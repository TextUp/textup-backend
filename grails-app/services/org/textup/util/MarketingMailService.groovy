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
import org.textup.type.*

@GrailsTypeChecked
@Transactional
class MarketingMailService {

    GrailsApplication grailsApplication
    ThreadService threadService

    Result<Void> tryScheduleAddToGeneralUpdatesList(boolean shouldAdd, String email) {
        if (shouldAdd && email) {
            String listId = grailsApplication.flatConfig["textup.apiKeys.mailChimp.listIds.generalUpdates"]
            threadService.submit {
                addEmailToList(email, listId)
                    .logFail("MarketingMailService.tryScheduleAddToGeneralUpdatesList")
            }
        }
        Result.void()
    }

    Result<Void> tryScheduleAddToUserTrainingList(Staff s1, StaffStatus oldStatus = null) {
        if (s1) {
            String email = s1.email
            String listId = grailsApplication.flatConfig["textup.apiKeys.mailChimp.listIds.users"]
            StaffStatus currentStatus = s1.status
            if ((!oldStatus || oldStatus.isPending()) && currentStatus?.isActive()) {
                threadService.submit {
                    addEmailToList(email, listId)
                        .logFail("MarketingMailService.tryScheduleAddToUserTrainingList for staff with id `${s1?.id}`")
                }
            }
        }
        Result.void()
    }

    // Helpers
    // -------

    // adds one email to marketing mail list
    protected Result<Void> addEmailToList(String email, String listId) {
        String username = MailUtils.NO_OP_BASIC_AUTH_USERNAME
        String pwd = grailsApplication.flatConfig["textup.apiKeys.mailChimp.apiKey"]
        String encodedAuthKey = Base64.encodeBase64String("${username}:${pwd}".bytes)
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
