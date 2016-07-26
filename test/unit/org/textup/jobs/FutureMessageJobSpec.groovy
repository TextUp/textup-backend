package org.textup.jobs

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.Trigger
import org.textup.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class FutureMessageJobSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

	FutureMessageJob job

	private boolean _shouldExecuteSuccessfully = true
	private boolean _didMarkDone = false

	private String _jobKey = "key"
	private String _jobStaffId = "1"

    def setup() {
    	job = new FutureMessageJob()
    	job.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	job.futureMessageService = [
    		execute: { String futureKey, Long staffId ->
    			if (_shouldExecuteSuccessfully) {
    				new ResultList(new Result(success:true))
    			}
    			else { new ResultList(new Result(success:false)) }
			},
    		markDone: { String futureKey ->
    			_didMarkDone = true
    			new Result(success:true)
    		}
    	] as FutureMessageService
    }

    protected buildJobContext(boolean willFireAgain) {
    	[
    		getMergedJobDataMap: { ->
    			[
	    			(Constants.JOB_DATA_FUTURE_MESSAGE_KEY):_jobKey,
	    			(Constants.JOB_DATA_STAFF_ID):_jobStaffId
	    		] as JobDataMap
    		},
    		getTrigger: {
    			[
    				mayFireAgain: { ->
    					willFireAgain
    				}
    			] as Trigger
    		}
    	] as JobExecutionContext
    }

    void "test mark done"() {
    	when: "execution does not succeed but will not fire again"
    	boolean triggerWillFireAgain = false
    	_shouldExecuteSuccessfully = false
    	_didMarkDone = false
    	job.execute(buildJobContext(triggerWillFireAgain))

    	then: "mark done regardless of execution success"
    	_didMarkDone == true

    	when: "execution succeeds but trigger may still fire again"
    	triggerWillFireAgain = true
    	_shouldExecuteSuccessfully = true
    	_didMarkDone = false
    	job.execute(buildJobContext(triggerWillFireAgain))

    	then: "don't mark done"
    	_didMarkDone == false

    	when: "execution succeeds and trigger will not fire again"
    	triggerWillFireAgain = false
    	_shouldExecuteSuccessfully = true
    	_didMarkDone = false
    	job.execute(buildJobContext(triggerWillFireAgain))

    	then: "mark done"
    	_didMarkDone == true
    }
}
