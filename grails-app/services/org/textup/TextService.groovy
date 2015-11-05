package org.textup

import grails.transaction.Transactional
import com.twilio.sdk.resource.factory.MessageFactory
import com.twilio.sdk.resource.instance.Message
import com.twilio.sdk.TwilioRestClient
import org.apache.http.message.BasicNameValuePair
import org.hibernate.FlushMode

@Transactional
class TextService {

	def grailsApplication
	def resultFactory

	////////////////////
	// Communications //
	////////////////////

    Result<RecordText> text(Phone fromPhone, Contactable toContact, RecordText text) {

    	//SHOULD ALSO HANDLE FUTURE TEXTS AS WELL!!!

		if (text.validate()) {
			def config = grailsApplication.config.textup
			ContactNumber.withNewSession { session ->
				session.flushMode = FlushMode.MANUAL
				try {
		            TwilioRestClient client = new TwilioRestClient(config.twilioSid, config.twilioAuth)
		            MessageFactory messageFactory = client.account.messageFactory
		            //////////////////////////////////////////////////////////////
		            // Try the contactable's first preference number first,     //
					// will try lower preference numbers if the first one fails //
		            //////////////////////////////////////////////////////////////
		            List<PhoneNumber> numsByPref = toContact.numbers
		            if (!numsByPref.isEmpty()) {
		            	PhoneNumber to = numsByPref[0]
		            	def l = [Body:text.contents, To:to, From:fromPhone.number]
		            	def params = l.collect { k, v -> new BasicNameValuePair(k, v) }
			            Message m = messageFactory.create(params)
			            m.redact() 
			            text.addToReceipts(apiId:m.sid, receivedBy:to)
		            }
		            text.save()
		            resultFactory.success(text)
		        }
		        catch(Throwable e) {
		            log.error("ContactService.text: ${e.message}")
		            resultFactory.failWithThrowable(e)
		        }
		        finally { session.flushMode = FlushMode.AUTO }
			}
		}
		else {
			resultFactory.failWithValidationErrors(text.errors)
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
