package org.textup

import com.twilio.sdk.resource.factory.MessageFactory
import com.twilio.sdk.resource.instance.Message
import com.twilio.sdk.TwilioRestClient
import grails.transaction.Transactional
import org.apache.http.message.BasicNameValuePair
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.hibernate.FlushMode
import org.textup.rest.TwimlBuilder

@Transactional
class TextService {

    LinkGenerator linkGenerator
	def grailsApplication
	def resultFactory
    def lockService
    def twimlBuilder
    def recordService

    ///////////////////
    // Webhook texts //
    ///////////////////

    //from and to numbers already cleaned
    Result<Closure> handleIncomingToStaff(String from, String to, String apiId, String contents) {
        StaffPhone p1 = StaffPhone.forStaffNumber(to).get()
        if (p1) {
            PhoneNumber fromNum = new PhoneNumber(number:from)
            Result res = recordService.createIncomingRecordText(fromNum, p1, [contents:contents], [apiId:apiId])
            if (res.success) {

                //////////////////////////
                // TODO: check blocked! //
                //////////////////////////

                Staff s1 = Staff.get(p1.ownerId)
                if (s1?.isAvailableNow()) {
                    Contact c1 = Contact.forRecord(res.payload[0]?.record).get()
                    Result nRes = notify(s1, c1, fromNum.number, contents)
                    if (!nRes.success) { log.error(nRes.payload.message) }
                    res = twimlBuilder.noResponse()
                }
                else {
                    res = twimlBuilder.buildXmlFor(TwimlBuilder.TEXT_STAFF_AWAY)
                }
            }
            res
        }
        else {
            resultFactory.failWithMessage(NOT_FOUND, 'textService.handleIncomingToStaff.phoneNotFound')
        }
    }
    //from and to numbers already cleaned
    Result<Closure> handleIncomingToTeam(String from, String to, String sId, String contents) {
        //TODO: implement me!!!
        resultFactory.success({ -> })
    }

    protected Result notify(Staff s1, Contact contact, String incomingFrom, String contents) {
        if (contact) {
            String attribution = contact.name ?: incomingFrom,
                to = s1.personalPhoneNumber?.e164PhoneNumber,
                from = s1.phone?.number?.e164PhoneNumber
            if (attribution && to && from) {
                textOnly(from, to, buildNotification(attribution, contents))
            }
            else {
                resultFactory.failWithMessage("textService.notify.missingInfo", [attribution, to, from])
            }
        }
        else {
            resultFactory.failWithMessage("textService.notify.noContact", [s1.id])
        }
    }
    protected String buildNotification(String attribution, String contents) {
        String notification = "${attribution}: ${contents}"
        if (notification.size() >= Constants.TEXT_LENGTH) {
            int trimTo = Constants.TEXT_LENGTH - 4
            notification = "${notification[0..trimTo]}..."
        }
        notification
    }

	////////////////////
	// Communications //
	////////////////////

    Result<RecordText> text(Phone fromPhone, Contactable toContact, RecordText text) {

    	//SHOULD ALSO HANDLE FUTURE TEXTS AS WELL!!!
        //TODO: do redact in status callback because otherwise MessageId not stored yet m.redact()

		if (text.validate()) {
			stopOnSuccessOrInternalError(text, fromPhone.number.e164PhoneNumber,
                toContact.numbers*.e164PhoneNumber)
		}
		else {
			resultFactory.failWithValidationErrors(text.errors)
		}
	}
    Result<RecordText> retry(Long recordTextId) {
        RecordText t1 = RecordText.get(recordTextId)
        if (t1) {
            Contact contact = Contact.forRecord(t1.record).get()
            if (contact) {
                lockService.retry(t1, contact, this.&stopOnSuccessOrInternalError)
            }
            else {
                resultFactory.failWithMessage("textService.retry.contactNotFoundForText", [t1.id])
            }
        }
        else { resultFactory.failWithMessage("textService.retry.textNotFound", [recordTextId]) }
    }

    ///////////////////////////
    // Twilio helper methods //
    ///////////////////////////

    protected Result<RecordText> stopOnSuccessOrInternalError(RecordText text, String from, List<String> toNums) {
        Result res = resultFactory.success(text)
        for (toNum in toNums) {
            res = this.tryText(text, toNum, from)
            //return on first success or if 500-level error
            if (res.success || (!res.success && res.payload?.errorCode > 499)) {
                return res
            }
        }
        res //if we haven't already returned, return last obtained result
    }

    protected Result<Message> textOnly(String from, String to, String contents) {
        def twilioConfig = grailsApplication.config.textup.apiKeys.twilio
        RecordText.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                TwilioRestClient client = new TwilioRestClient(twilioConfig.sid, twilioConfig.authToken)
                MessageFactory messageFactory = client.account.messageFactory
                def l = [Body:contents, To:to, From:from]
                def params = l.collect { k, v -> new BasicNameValuePair(k, v) }
                Message m = messageFactory.create(params)
                resultFactory.success(m)
            }
            catch (Throwable e) {
                log.error("TextService.textOnly: ${e.message}")
                return resultFactory.failWithThrowable(e)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
    }

    protected Result<RecordText> tryText(RecordText text, String to, String from) {
        def twilioConfig = grailsApplication.config.textup.apiKeys.twilio



        // String callback = linkGenerator.link(namespace:"v1", resource:"publicRecord",
            // action:"save", absolute:true, params:[handle:Constants.TEXT_STATUS])


        String callback = "https://08a91b1b.ngrok.io/v1/public/records?handle=status"

        RecordText.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                TwilioRestClient client = new TwilioRestClient(twilioConfig.sid, twilioConfig.authToken)
                MessageFactory messageFactory = client.account.messageFactory
                def l = [Body:text.contents, To:to, From:from, StatusCallback:callback]
                def params = l.collect { k, v -> new BasicNameValuePair(k, v) }
                Message m = messageFactory.create(params)
                if (m) {
                    RecordItemReceipt receipt = new RecordItemReceipt(apiId:m.sid)
                    receipt.receivedByAsString = to
                    text.addToReceipts(receipt)
                    if (!receipt.save()) { resultFactory.failWithValidationErrors(receipt.errors) }
                }
                //if not merge, we get org.hibernate.NonUniqueObjectException
                if (text.merge()) { resultFactory.success(text) }
                else { resultFactory.failWithValidationErrors(text.errors) }
            }
            catch (Throwable e) {
                log.error("TextService.tryText: ${e.message}")
                return resultFactory.failWithThrowable(e)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
    }

	////////////////////
	// Helper methods //
	////////////////////

	protected Result checkNumRecipients(int numRecipients) {
        int maxRecipients = grailsApplication.config.textup.maxNumText
        if (numRecipients > maxRecipients) {
            resultFactory.failWithMessage("textService.error.tooManyRecipients",
                [numRecipients, maxRecipients])
        }
        else { resultFactory.success() }
    }
    protected Result checkMessageSize(String message) {
        int maxMsgSize = RecordText.constraints.contents.maxSize
        if (!message || message.size() > maxMsgSize) {
            resultFactory.failWithMessage("textService.error.messageLength",
                [message.size(), maxMsgSize])
        }
        else { resultFactory.success() }
    }
}
