package org.textup.util

import org.textup.*
import grails.plugin.remotecontrol.RemoteControl
import org.codehaus.groovy.grails.orm.hibernate.cfg.NamedCriteriaProxy

//////////////////////////////////////////////////////////
// NOTE that all returned classes must be serializable! //
//////////////////////////////////////////////////////////

class RestRemote {

    RemoteControl remote 
    String loggedInUsername
    String loggedInPassword

    Long orgId, teamId

    RestRemote(int iterationCount) {
        remote = new RemoteControl() 
        def loggedInResult = remote {
            CustomSpec spec = new CustomSpec()
            spec.setupData(iterationCount)
            [
            	loggedInUsername:spec.loggedInUsername, 
            	loggedInPassword:spec.loggedInPassword,
            	orgId:spec.org.id, 
            	teamId:spec.t1.id
        	]
        }
        loggedInUsername = loggedInResult.loggedInUsername
        loggedInPassword = loggedInResult.loggedInPassword
        orgId = loggedInResult.orgId
        teamId = loggedInResult.teamId
    }

    int count(Class clazz) {
    	remote {
    		clazz.count()
    	}
    }

    int countWithCriteria(NamedCriteriaProxy criteria) {
    	remote {
    		criteria.count()
    	}
    }
}