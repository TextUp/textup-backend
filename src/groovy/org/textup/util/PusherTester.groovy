package org.textup.util

import com.pusher.rest.Pusher
import groovy.json.JsonBuilder

// Used for mocking pusherService in tests

class PusherTester extends Pusher {

	PusherTester(String a, String b, String c) {
		super(a, b, c)
	}

	@Override
	public String authenticate(String sId, String cName) {
		JsonBuilder builder = new JsonBuilder()
    	builder({
			socketId(sId)
			channelName(cName)
		})
    	builder.toString()
	}
}
