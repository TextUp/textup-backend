package org.textup

import groovy.transform.ToString

@ToString
class Result<T> {
    boolean success = true     
    T payload  
    String type //check the Constants class for valid types
}