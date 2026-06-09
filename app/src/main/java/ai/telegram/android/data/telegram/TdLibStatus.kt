package ai.telegram.android.data.telegram

sealed interface TdLibStatus {
    data object NotConfigured : TdLibStatus
    data object BindingMissing : TdLibStatus
    data object Starting : TdLibStatus
    data object Ready : TdLibStatus
    data object WaitingForPhoneNumber : TdLibStatus
    data class WaitingForCode(val canResendCode: Boolean = false) : TdLibStatus
    data class WaitingForPassword(val hint: String = "") : TdLibStatus
    data object Closed : TdLibStatus
    data class Error(val message: String) : TdLibStatus
}
