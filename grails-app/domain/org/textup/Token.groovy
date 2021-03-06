package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Token implements WithId, CanSave<Token> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    DateTime expires = JodaUtils.utcNow().plusHours(1)
    String token
    Integer maxNumAccess
    int timesAccessed = 0

    TokenType type
    String stringData

    static transients = ["data"]
    static mapping = {
        expires type: PersistentDateTime
        stringData type: "text"
    }
    static constraints = {
    	token unique: true
        maxNumAccess nullable: true
    	stringData maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE, validator: { String data, Token obj ->
    		if (!obj.type?.requiredKeys.every { String key -> data.contains(key) }) {
    			["token.stringData.requiredKeys", obj.type, obj.type.requiredKeys]
    		}
    	}
    }

    static Result<Token> tryCreate(TokenType type, Map data) {
        Token tok1 = new Token(type: type, stringData: DataFormatUtils.toJsonString(data))
        DomainUtils.trySave(tok1, ResultStatus.CREATED)
    }

    def beforeValidate() {
        if (!token) {
            generateToken()
        }
    }

    // Properties
    // ----------

    boolean getIsExpired() { expires.isBeforeNow() || !isAllowedNumAccess() }

    TypeMap getData() {
        try {
            stringData ?
                new TypeMap(DataFormatUtils.jsonToObject(stringData)) :
                new TypeMap()
        }
        catch (Throwable e) {
            log.error("getData: invalid json string `${stringData}`")
            e.printStackTrace()
            new TypeMap()
        }
    }

    // Helpers
    // -------

    protected void generateToken() {
        // can't flush in GORM event hooks and calling dynamic finders
        // auto-flushes by default
        Utils.doWithoutFlush {
            Integer tokenSize = type?.tokenSize ?: Constants.DEFAULT_TOKEN_LENGTH
            String tokenString = StringUtils.randomAlphanumericString(tokenSize)
            //ensure that our generated token is unique
            while (Token.countByToken(tokenString) != 0) {
                tokenString = StringUtils.randomAlphanumericString(tokenSize)
            }
            token = tokenString
        }
    }

    protected boolean isAllowedNumAccess() {
        !maxNumAccess || maxNumAccess < 0 || timesAccessed < maxNumAccess
    }
}
