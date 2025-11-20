class SmsHelper {
    fun sendSms(phoneNumber: String, messageBody: String) {
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(phoneNumber, null, messageBody, null, null)
    }
}