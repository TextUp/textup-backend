package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.hibernate.Session
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.types.TokenType

@GrailsCompileStatic
@EqualsAndHashCode
class Token {

    DateTime expires = DateTime.now(DateTimeZone.UTC).plusHours(1)
    String token
    Integer maxNumAccess
    int timesAccessed = 0

    TokenType type
    String stringData

    static transients = ["data"]
    static constraints = {
    	token unique:true
        maxNumAccess nullable:true
    	stringData validator: { String data, Token obj ->
    		if (!obj.type?.requiredKeys.every { String key -> data.contains(key) }) {
    			["requiredKeys", obj.type, obj.type.requiredKeys]
    		}
    	}
    }
    static mapping = {
        expires type:PersistentDateTime
    }

    // Hooks
    // -----

    def beforeValidate() {
        if (!this.token) { generateToken() }
    }
    protected void generateToken() {
        Integer tokenSize = this.type?.tokenSize ?: Constants.DEFAULT_TOKEN_LENGTH
        String tokenString
        // can't flush in GORM event hooks and calling dynamic finders
        // auto-flushes by default
        Token.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            tokenString = Helpers.randomAlphanumericString(tokenSize)
            try {
                //ensure that our generated token is unique
                while (Token.countByToken(tokenString) != 0) {
                    tokenString = Helpers.randomAlphanumericString(tokenSize)
                }
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        this.token = tokenString
    }

    // Expiration
    // ----------

    boolean getIsExpired() {
        expires.isBeforeNow() || !isAllowedNumAccess()
    }
    protected boolean isAllowedNumAccess() {
        !maxNumAccess || maxNumAccess < 0 || timesAccessed < maxNumAccess
    }

    // Data
    // ----

    void setData(Map p) {
        if (p != null) {
            this.stringData = Helpers.toJsonString(p)
        }
    }

    Map getData() {
        if (!this.stringData) {
        	return [:]
    	}
        try {
            Helpers.toJson(this.stringData) as Map
        }
        catch (e) {
            log.error("Token.getData: invalid json string '${this.stringData}'")
            [:]
        }
    }
}
