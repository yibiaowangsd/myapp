package com.personal.wo.core

object NoticeRules {
    private val otp = Regex("(?i)(验证码|校验码|口令|一次性密码|密码\\s*[:：]|\\bpassword\\b|\\bpasscode\\b|verification\\s*code|one.?time\\s*(password|code)|\\bOTP\\b)")
    private val identifiers = Regex("(?<![0-9])[0-9][0-9 \\-]{13,23}[0-9Xx](?![0-9])")
    fun sanitize(title: String, text: String): Pair<String, String> {
        if (otp.containsMatchIn("$title $text")) return "验证通知（内容未保存）" to ""
        fun scrub(s: String, max: Int): String = identifiers.replace(s.take(max), "[已过滤]").replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), "")
        return scrub(title, 256) to scrub(text, 2048)
    }
    fun classify(title: String, text: String, optedIn: Boolean): String {
        if (!optedIn) return "通知片段"
        val s = "$title $text"
        return when {
            Regex("送达|已签收|派送完成").containsMatchIn(s) -> "收到送达提示"
            Regex("支付成功|交易成功|付款成功").containsMatchIn(s) -> "收到交易成功提示"
            Regex("检票|登机|出发提醒|车票|出票成功").containsMatchIn(s) -> "收到行程提示"
            Regex("会议提醒|会议即将|预约提醒|预约成功").containsMatchIn(s) -> "收到预约或会议提示"
            else -> "通知片段"
        }
    }
    fun tokens(s: String): String {
        val normalized = s.lowercase()
        val chinese = Regex("[\\p{IsHan}]+").findAll(normalized).flatMap { m ->
            if (m.value.length == 1) sequenceOf(m.value) else m.value.windowed(2).asSequence()
        }
        val words = Regex("[a-z0-9]+").findAll(normalized).map { it.value }
        return (chinese + words).distinct().joinToString(" ")
    }
}
