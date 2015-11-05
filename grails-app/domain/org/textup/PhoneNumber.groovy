package org.textup

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class PhoneNumber {

	String number

    static constraints = {
    	number validator:{ val, obj ->
            if (val?.size() != 10) { ["format"] }
        }
    }

    /*
	Has many:
	*/
    
    ////////////////////
    // Helper methods //
    ////////////////////
    
    /////////////////////
    // Property Access //
    /////////////////////

    String getPrettyPhoneNumber() { 
    	String n = this.number 
    	(n && n.size() > 6) ? "${n[0..2]} ${n[3..5]} ${n[6..-1]}" : ""
    }
    String getE164PhoneNumber() { 
    	String n = this.number 
    	n ? "+1${n}" : ""
    }
    
    void setNumber(String n) { this.number = cleanPhoneNumber(n) }
    private String cleanPhoneNumber(String n) {
    	if (n) {
    		String cleaned = n.replaceAll(/\D+/, "")
        	(cleaned.size() == 11 && cleaned[0] == "1") ? cleaned.substring(1) : cleaned
    	}
        else { n }
    }

    @Override 
    String toString() { this.getPrettyPhoneNumber() }
}
