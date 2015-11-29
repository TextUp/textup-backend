package org.textup

class Constants {
	static final int LOCK_RETRY_MAX = 3
	static final int TEXT_LENGTH = 160

	static final String DEFAULT_AWAY_MESSAGE = "Sorry. I'm currently not available. I'll reply when I am back."

	static final String SCHEDULE_AVAILABLE = "available"
	static final String SCHEDULE_UNAVAILABLE = "unavailable"

	static final String RECEIPT_FAILED = "failed"
	static final String RECEIPT_PENDING = "pending"
	static final String RECEIPT_SUCCESS = "success"

	static final String CONTACT_UNREAD = "unread"
	static final String CONTACT_ACTIVE = "active"
	static final String CONTACT_ARCHIVED = "archived"
	static final String CONTACT_BLOCKED = "blocked"

	static final String ORG_PENDING = "pending"
	static final String ORG_REJECTED = "rejected"
	static final String ORG_APPROVED = "approved"

	static final String RECORD_CALL = "call"
	static final String RECORD_TEXT = "text"
	static final String RECORD_NOTE = "note"

	static final String STATUS_BLOCKED = "blocked"
	static final String STATUS_PENDING = "pending"
	static final String STATUS_STAFF = "staff"
	static final String STATUS_ADMIN = "admin"

	static final String SHARED_DELEGATE = "delegate"
	static final String SHARED_VIEW = "view"

	static final String RESULT_SUCCESS = "success"
	static final String RESULT_VALIDATION = "validation"
	static final String RESULT_MESSAGE = "message"
	static final String RESULT_MESSAGE_LIST_STATUS = "messageListWithStatus"
	static final String RESULT_MESSAGE_STATUS = "messageWithStatus"
	static final String RESULT_THROWABLE = "throwable"

	static final String TAG_ACTION_ADD = "add"
	static final String TAG_ACTION_REMOVE = "remove"
	static final String TAG_ACTION_SUBSCRIBE_CALL = "subscribeCall"
	static final String TAG_ACTION_SUBSCRIBE_TEXT = "subscribeText"
	static final String TAG_ACTION_UNSUBSCRIBE = "unsubscribe"

	static final String TEAM_ACTION_ADD = "add"
	static final String TEAM_ACTION_REMOVE = "remove"

	static final String SHARE_ACTION_MERGE = "merge"
	static final String SHARE_ACTION_STOP = "stop"

	static final String NUMBER_ACTION_MERGE = "merge"
	static final String NUMBER_ACTION_DELETE = "delete"

	static final String SUBSCRIPTION_TEXT = "text"
	static final String SUBSCRIPTION_CALL = "call"

	static final String CALL_TEXT_REPEAT = "repeat"

	static final String CALL_INCOMING = "incoming"
	static final String CALL_STATUS = "status"
	static final String CALL_BRIDGE = "bridge"
	static final String CALL_ANNOUNCEMENT = "announcement"
	static final String CALL_VOICEMAIL = "voicemail"
	static final String CALL_PUBLIC_TEAM_DIGITS = "digitsFromPublicForTeam"
	static final String CALL_TEAM_ANNOUNCEMENT_DIGITS = "digitsForTeamAnnouncement"
	static final String CALL_STAFF_STAFF_DIGITS = "digitsFromStaffForStaff"

	static final String TEXT_INCOMING = "incoming"
	static final String TEXT_STATUS = "status"

	static final String ACTION_SEE_ANNOUNCEMENTS = "0"
	static final String ACTION_SUBSCRIBE = "1"
	static final String ACTION_UNSUBSCRIBE_ONE = "2"
	static final String ACTION_UNSUBSCRIBE_ALL = "3"

	static final String CALL_GREETING_HEAR_ANNOUNCEMENTS = "1"
	static final String CALL_GREETING_SUBSCRIBE_ALL = "2"
	static final String CALL_GREETING_UNSUBSCRIBE_ALL = "3"
	static final String CALL_GREETING_CONNECT_TO_STAFF = "0"

	static final String CALL_ANNOUNCE_UNSUBSCRIBE_ONE = "1"
	static final String CALL_ANNOUNCE_UNSUBSCRIBE_ALL = "2"
}
