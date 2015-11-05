package org.textup

import grails.transaction.Transactional
import com.twilio.sdk.resource.factory.MessageFactory
import com.twilio.sdk.resource.instance.Message
import com.twilio.sdk.TwilioRestClient
import org.apache.http.message.BasicNameValuePair
import org.hibernate.FlushMode

@Transactional
class CallService {

	def grailsApplication
	def resultFactory

    Result<RecordCall> call(Phone fromPhone, Contactable toContact, RecordCall call) {

	}
}
