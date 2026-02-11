package nus.edu.u.common.enums;

import nus.edu.u.common.exception.ErrorCode;

/**
 * Error code enum class
 *
 * @author Lu Shuwen
 * @date 2025-08-30
 */
public interface ErrorCodeConstants {

    // ========= Auth module 10-01-000 ============
    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS =
            new ErrorCode(10_01_001, "Login failed. Incorrect username or password");
    ErrorCode AUTH_LOGIN_USER_DISABLED =
            new ErrorCode(10_01_002, "Login failed. This account has been disabled");
    ErrorCode AUTH_LOGIN_CAPTCHA_CODE_ERROR =
            new ErrorCode(10_01_003, "Incorrect verification code. Please try again");
    ErrorCode REFRESH_TOKEN_WRONG =
            new ErrorCode(10_01_004, "Your session has expired. Please log in again");
    ErrorCode ACCOUNT_ERROR = new ErrorCode(10_01_005, "Account error");
    ErrorCode EXPIRED_LOGIN_CREDENTIALS =
            new ErrorCode(10_01_006, "Your session has expired. Please log in again");
    ErrorCode FIREBASE_DISABLED =
            new ErrorCode(10_01_007, "Firebase authentication is not enabled");
    ErrorCode FIREBASE_VERIFICATION_FAILED =
            new ErrorCode(10_01_008, "Firebase token verification failed");
    ErrorCode REFRESH_TOKEN_REUSE_DETECTED =
            new ErrorCode(10_01_009, "Security alert: Refresh token reuse detected. Please log in again");

    // ========= user crud module 12-01-000 ============
    ErrorCode USERNAME_EXIST = new ErrorCode(12_01_001, "Username already exists");
    ErrorCode EMAIL_EXIST = new ErrorCode(12_01_002, "Email already exists");
    ErrorCode WRONG_MOBILE = new ErrorCode(12_01_003, "Invalid mobile");
    ErrorCode USER_INSERT_FAILURE = new ErrorCode(12_01_004, "Insert failure");
    ErrorCode PHONE_EXIST = new ErrorCode(12_01_005, "Phone already exists");
    ErrorCode USER_NOTFOUND = new ErrorCode(12_01_006, "User not found");
    ErrorCode USER_DISABLE_FAILURE = new ErrorCode(12_01_014, "User disabled failure");
    ErrorCode USER_ENABLE_FAILURE = new ErrorCode(12_01_015, "User enable failure");
    ErrorCode UPDATE_FAILURE = new ErrorCode(12_01_007, "Update failure");
    ErrorCode USER_NOT_DELETED = new ErrorCode(12_01_008, "User not deleted");
    ErrorCode USER_ALREADY_DELETED = new ErrorCode(12_01_009, "User already deleted");
    ErrorCode USER_ALREADY_DISABLED = new ErrorCode(12_01_0010, "User already disabled");
    ErrorCode USER_ALREADY_ENABLED = new ErrorCode(12_01_0011, "User already enabled");
    ErrorCode ROLE_NOT_FOUND = new ErrorCode(12_01_0012, "Role not found");
    ErrorCode USER_ROLE_BIND_FAILURE = new ErrorCode(12_01_0013, "User role bind failure");
    ErrorCode EMAIL_BLANK = new ErrorCode(12_01_0014, "Email can not be blank");
    ErrorCode INVALID_EMAIL = new ErrorCode(12_01_0015, "Invalid email");
    ErrorCode EMPTY_ROLEIDS = new ErrorCode(12_01_0016, "roleIds can not be blank");
    ErrorCode NULL_USERID = new ErrorCode(12_01_0017, "Email existed but user not found on update");

    // ========= group module 10-02-000 ============
    ErrorCode GROUP_NOT_FOUND = new ErrorCode(10_02_001, "Group not found");
    ErrorCode EVENT_NOT_FOUND = new ErrorCode(10_02_002, "Event not found");
    ErrorCode USER_NOT_FOUND = new ErrorCode(10_02_003, "User not found");
    ErrorCode GROUP_NAME_EXISTS =
            new ErrorCode(10_02_004, "Group name already exists in this event");
    ErrorCode GROUP_MEMBER_ALREADY_EXISTS =
            new ErrorCode(10_02_005, "User is already a member of this group");
    ErrorCode USER_STATUS_INVALID =
            new ErrorCode(10_02_006, "User status is invalid, cannot add to group");
    ErrorCode CANNOT_REMOVE_GROUP_LEADER =
            new ErrorCode(10_02_007, "Cannot remove group leader from group");
    ErrorCode CANNOT_REMOVE_MEMBER_WITH_PENDING_TASKS =
            new ErrorCode(10_02_009, "Cannot remove member with pending tasks");
    ErrorCode GET_GROUP_ID_FAILED =
            new ErrorCode(10_02_008, "Failed to get group ID after insert ");
    ErrorCode USER_ALREADY_IN_OTHER_GROUP_OF_EVENT =
            new ErrorCode(1_002_015_000, "User already in other group at this event");
    ErrorCode USER_NOT_IN_GROUP = new ErrorCode(1_002_015_001, "user not in this group");
    ErrorCode ADD_MEMBERS_FAILED =
            new ErrorCode(1_002_015_004, "Add failed: A user can only be in one group per event");

    // ========= Reg module 11-01-000 =============
    ErrorCode NO_SEARCH_RESULT = new ErrorCode(11_01_001, "No matching result found");
    ErrorCode REG_FAIL = new ErrorCode(11_01_002, "Sign-up failed. Please contact support");
    ErrorCode EXCEED_MAX_RETRY_GENERATE_CODE =
            new ErrorCode(
                    11_01_003, "Unable to generate an organization code. Please try again later");
    ErrorCode ACCOUNT_EXIST =
            new ErrorCode(
                    11_01_004,
                    "This account already exists. Please log in or use a different account");

    // ========= Event module 13-01-000 =============
    ErrorCode ORGANIZER_NOT_FOUND = new ErrorCode(13_01_001, "Organizer does not exist");
    ErrorCode PARTICIPANT_NOT_FOUND = new ErrorCode(13_01_002, "Some participants do not exist");
    ErrorCode TIME_RANGE_INVALID =
            new ErrorCode(13_01_003, "The start time must be earlier than the end time");
    ErrorCode DUPLICATE_PARTICIPANTS =
            new ErrorCode(13_01_004, "The participant list contains duplicate users");
    ErrorCode EVENT_DELETE_FAILED = new ErrorCode(13_01_005, "Event deletion failed");
    ErrorCode EVENT_NOT_DELETED = new ErrorCode(13_01_006, "Event not deleted");
    ErrorCode EVENT_RESTORE_FAILED = new ErrorCode(13_01_006, "Event restore failed");

    // ========= Task module 13-02-000 ============
    ErrorCode TASK_STATUS_INVALID = new ErrorCode(13_02_001, "Illegal task status");
    ErrorCode TASK_ASSIGNEE_NOT_FOUND = new ErrorCode(13_02_002, "Assigned user does not exist");
    ErrorCode TASK_TIME_RANGE_INVALID =
            new ErrorCode(13_02_003, "The task start time must be earlier than the end time");
    ErrorCode TASK_ASSIGNEE_TENANT_MISMATCH =
            new ErrorCode(13_02_004, "The assigned user does not belong to this event");
    ErrorCode TASK_NOT_FOUND = new ErrorCode(13_02_005, "task does not exist");
    ErrorCode TASK_TIME_OUTSIDE_EVENT =
            new ErrorCode(13_02_006, "The task timeframe must fall within the event timeframe");
    ErrorCode TASK_CREATE_FAILED = new ErrorCode(13_02_007, "task creation failed");
    ErrorCode TASK_UPDATE_FAILED = new ErrorCode(13_02_008, "task update failed");
    ErrorCode TASK_DELETE_FAILED = new ErrorCode(13_02_009, "task delete failed");
    ErrorCode WRONG_TASK_ACTION_TYPE = new ErrorCode(13_02_010, "task action failed");
    ErrorCode TASK_LOG_ERROR = new ErrorCode(13_02_011, "task log error");
    ErrorCode TASK_LOG_FILE_FAILED = new ErrorCode(13_02_012, "task log files upload failed");
    ErrorCode APPROVE_TASK_FAILED = new ErrorCode(13_02_013, "approve task failed");
    ErrorCode ASSIGN_TASK_FAILED = new ErrorCode(13_02_014, "assign task failed");
    ErrorCode BLOCK_TASK_FAILED = new ErrorCode(13_02_015, "block task failed");
    ErrorCode REJECT_TASK_FAILED = new ErrorCode(13_02_016, "reject task failed");
    ErrorCode SUBMIT_TASK_FAILED = new ErrorCode(13_02_017, "submit task failed");
    ErrorCode ACCEPT_TASK_FAILED = new ErrorCode(13_02_018, "accept task failed");
    ErrorCode MODIFY_OTHER_TASK_ERROR =
            new ErrorCode(13_02_019, "You can only modify your own task");
    ErrorCode MODIFY_WRONG_TASK_STATUS =
            new ErrorCode(13_02_020, "You can't {} the task in {} status");

    // ========= RolePermission module 14-01-000 ============
    ErrorCode CREATE_ROLE_FAILED = new ErrorCode(14_01_001, "Create role failed");
    ErrorCode CANNOT_FIND_ROLE = new ErrorCode(14_01_002, "Role not found");
    ErrorCode UPDATE_ROLE_FAILED = new ErrorCode(14_01_003, "Update role failed");
    ErrorCode CANNOT_DELETE_ROLE =
            new ErrorCode(
                    14_01_004, "Role cannot be deleted because it has been assigned to user(s)");
    ErrorCode ASSIGN_ROLE_FAILED = new ErrorCode(14_01_004, "Assign role failed");
    ErrorCode EXISTING_ROLE_FAILED = new ErrorCode(14_01_005, "Role already exists");
    ErrorCode DEFAULT_ROLE = new ErrorCode(14_01_006, "Default roles. Can't be modified");

    ErrorCode CANNOT_FIND_PERMISSION = new ErrorCode(14_02_001, "Permission not fount");
    ErrorCode UPDATE_PERMISSION_FAILED = new ErrorCode(14_02_002, "Update permission failed");
    ErrorCode CANNOT_DELETE_PERMISSION =
            new ErrorCode(
                    14_02_003,
                    "Permission cannot be deleted because it has been assigned to role(s)");
    ErrorCode CREATE_PERMISSION_FAILED = new ErrorCode(14_02_004, "Create permission failed");

    // ========= Check-in module 10-03-000 ============
    ErrorCode EVENT_ATTENDEE_NOT_FOUND = new ErrorCode(10_03_001, "Attendee not found");
    ErrorCode INVALID_CHECKIN_TOKEN = new ErrorCode(10_03_002, "Invalid check-in token");
    ErrorCode ALREADY_CHECKED_IN = new ErrorCode(10_03_003, "Already checked in");
    ErrorCode CHECKIN_NOT_STARTED = new ErrorCode(10_03_004, "Check-in has not started yet");
    ErrorCode CHECKIN_ENDED = new ErrorCode(10_03_005, "Check-in has ended");
    ErrorCode EVENT_NOT_ACTIVE = new ErrorCode(10_03_006, "Event is not active");
    ErrorCode ATTENDEE_CREATION_FAILED =
            new ErrorCode(
                    1_002_010_002,
                    "Attendee already exists: This email address has been registered for this event");

    // ========= QR Code module 10-04-000 ============
    ErrorCode QRCODE_GENERATION_FAILED = new ErrorCode(10_04_001, "Failed to generate QR code");
    ErrorCode QRCODE_INVALID_CONTENT = new ErrorCode(10_04_002, "Invalid QR code content");
    ErrorCode QRCODE_INVALID_SIZE = new ErrorCode(10_04_003, "Invalid QR code size");

    // ========= Attendee module 10-05-000 ============
    ErrorCode ATTENDEE_NOT_EXIST = new ErrorCode(10_05_001, "Attendee does not exist");
    ErrorCode UPDATE_ATTENDEE_FAILED = new ErrorCode(10_05_002, "Update attendee failed");

    // ========= Excel ============
    ErrorCode EMPTY_EXCEL = new ErrorCode(905, "Excel is empty");
    ErrorCode TOO_MANY_REQUESTS = new ErrorCode(429, "Too many requests");
    ErrorCode SERVICE_DEGRADED = new ErrorCode(503, "Service degraded");
}
