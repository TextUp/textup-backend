package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.types.TokenType

@GrailsCompileStatic
@EqualsAndHashCode
class Token {

    DateTime expires = DateTime.now(DateTimeZone.UTC).plusHours(1)
    String token
    TokenType type
    String _stringData

    static transients = ['data']
    static constraints = {
    	token unique:true
    	_stringData validator: { String data, Token obj ->
    		Collection<String> reqKeys = (obj.type == TokenType.PASSWORD_RESET) ?
    			["toBeResetId"] : ["toVerifyNumber"]
    		if (!reqKeys.every { String key -> data.contains(key) }) {
    			["requiredKeys", obj.type, reqKeys]
    		}
    	}
    }
    static mapping = {
        expires type:PersistentDateTime
    }

    boolean getIsExpired() {
        expires.isBeforeNow()
    }
    void expireNow() {
        this.expires = DateTime.now(DateTimeZone.UTC).minusMinutes(1)
    }

    // Data
    // ----

    void setData(Map p) {
        if (p != null) {
            this._stringData = new JsonBuilder(p).toString()
        }
    }

    Map getData() {
        if (!this._stringData) {
        	return [:]
    	}
        try {
            new JsonSlurper().parseText(this._stringData) as Map
        }
        catch (e) {
            log.error("Token.getData: invalid json string '${this._stringData}'")
            [:]
        }
    }
}
