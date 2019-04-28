package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum ReceiptStatus {
	PENDING(["in-progress", "ringing", "queued", "accepted", "sending", "receiving"], 1),
	// the 'sent' status is for when Twilio doesn't receive an additional confirmation from the
	// carrier that the message has sent. However, this 'sent' status should still represent
	// a successful status
	SUCCESS(["sent", "completed", "canceled", "delivered"], 2),
	// BUSY and FAILED have higher sequence numbers than success because we want to make sure
	// that these errors statuses are noted for the user rather than being overriden
	// by an earlier success
	BUSY(["busy", "no-answer"], 3),
	FAILED(["failed", "undelivered"], 4)

	private final Collection<String> statuses
	private final int sequenceNumber

	ReceiptStatus(Collection<String> thisStatuses, int thisNum) {
		statuses = thisStatuses
		sequenceNumber = thisNum
	}
	Collection<String> getStatuses() { Collections.unmodifiableCollection(statuses) }

	static ReceiptStatus translate(String status) {
		if (!status) {
			return
		}
		String cleanedStatus = status.toLowerCase()
		if (cleanedStatus in FAILED.statuses) {
			return FAILED
		}
		else if (cleanedStatus in PENDING.statuses) {
			return PENDING
		}
		else if (cleanedStatus in BUSY.statuses) {
			return BUSY
		}
		else if (cleanedStatus in SUCCESS.statuses) {
			return SUCCESS
		}
	}

	boolean isEarlierInSequenceThan(ReceiptStatus comparisonStatus) {
		sequenceNumber < comparisonStatus?.sequenceNumber
	}
}
