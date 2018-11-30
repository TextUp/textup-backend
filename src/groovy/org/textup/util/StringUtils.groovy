package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.textup.*

@GrailsTypeChecked
@Log4j
class StringUtils {

    static String toLowerCaseString(def val) {
        "$val".toString().toLowerCase()
    }

    static String toQuery(def raw) {
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

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static String randomAlphanumericString(Integer l) {
        int length = (l != null) ? l : 22
        Collection<String> alphabet = ("a".."z") + (0..9)
        int lastIndex = alphabet.size() - 1
        StringBuffer result = new StringBuffer()
        length.times {
            int randIndex = Math.round(Math.random() * lastIndex)
            result << alphabet[randIndex]
        }
        result.toString()
    }
}
