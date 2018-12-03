package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.textup.*

@GrailsTypeChecked
@Log4j
class StringUtils {

    static final List<String> ALPHABET = Collections.unmodifiableList(buildAlphabet())

    static String toLowerCaseString(Object val) {
        "$val".toString().toLowerCase()
    }

    static String toQuery(Object raw) {
        String query = "$raw"
        if (!query?.startsWith("%")) query = "%$query"
        if (!query?.endsWith("%")) query = "$query%"
        query
    }

    static String appendGuaranteeLength(String contents, String toAppend, int targetLen) {
        Integer contentsLen = contents?.size(),
            appendLen = toAppend?.size()
        String myContents = contents
        if (!contentsLen || targetLen < 1) {
            return myContents
        }
        if (contentsLen > targetLen) {
            // subtract one because we are dealing with indices not lengths
            int howMuchToKeep = targetLen - 1
            myContents = contents[0..howMuchToKeep]
            contentsLen = myContents.size()
        }
        if (!appendLen || appendLen > targetLen) {
            return myContents
        }
        if (contentsLen + appendLen > targetLen) {
            // contentsLen - (contentsLen + appendLen - targetLen)
            // = contentsLen - contentsLen - appendLen + targetLen
            // = targetLen - appendLen
            // then subtract one because we are dealing with indices not lengths
            int howMuchToKeep = targetLen - appendLen - 1
            // if we want to keep a negative amount, then we are in the edge case where
            // we want to keep none of the contents and effectively return only toAppend
            myContents = (howMuchToKeep < 0) ? "" : contents[0..howMuchToKeep]
        }
        myContents + toAppend
    }

    static String randomAlphanumericString(Integer length = 22) {
        int lastIndex = ALPHABET.size() - 1
        StringBuffer result = new StringBuffer()
        length.times {
            int randIndex = Math.round(Math.random() * lastIndex) as Integer
            result << ALPHABET[randIndex]
        }
        result.toString()
    }

    protected static List<String> buildAlphabet() {
        List<String> alphabet = []
        ("a".."z").each(alphabet.&add)
        (0..9).each { int val -> alphabet << val.toString() }
        alphabet
    }
}
