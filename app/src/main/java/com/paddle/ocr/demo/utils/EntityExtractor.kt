package com.paddle.ocr.demo.utils

data class ExtractedEntity(
    val type: EntityType,
    val value: String,
    val label: String
)

enum class EntityType {
    PHONE,
    ID_CARD,
    EMAIL,
    AMOUNT,
    TRACKING_NUMBER,
    BANK_CARD
}

object EntityExtractor {

    private val PHONE_REGEX = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
    private val ID_CARD_REGEX = Regex("(?<!\\d)[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx](?!\\d)")
    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val AMOUNT_REGEX = Regex("(?:¥|￥|RMB|\\$)\\s*\\d+(?:\\.\\d{1,2})?|(?<!\\d)\\d+(?:\\.\\d{1,2})?\\s*(?:元|块)")
    private val BANK_CARD_REGEX = Regex("(?<!\\d)(?:62\\d{14,17}|4\\d{15}|5[1-5]\\d{14})(?!\\d)")
    private val TRACKING_REGEX = Regex("(?i)\\b(?:SF|YT|ST|YD|EMS|ZTO|JD|DBL)?[0-9A-Z]{12,22}\\b")

    fun extract(text: String): List<ExtractedEntity> {
        val list = mutableListOf<ExtractedEntity>()

        // 手机号
        PHONE_REGEX.findAll(text).forEach { match ->
            list.add(ExtractedEntity(EntityType.PHONE, match.value, "手机号"))
        }

        // 身份证号
        ID_CARD_REGEX.findAll(text).forEach { match ->
            list.add(ExtractedEntity(EntityType.ID_CARD, match.value, "身份证"))
        }

        // 银行卡号
        BANK_CARD_REGEX.findAll(text).forEach { match ->
            // 避免把身份证误判为银行卡
            if (list.none { it.value == match.value }) {
                list.add(ExtractedEntity(EntityType.BANK_CARD, match.value, "银行卡号"))
            }
        }

        // 电子邮箱
        EMAIL_REGEX.findAll(text).forEach { match ->
            list.add(ExtractedEntity(EntityType.EMAIL, match.value, "电子邮箱"))
        }

        // 金额
        AMOUNT_REGEX.findAll(text).forEach { match ->
            list.add(ExtractedEntity(EntityType.AMOUNT, match.value, "金额数值"))
        }

        // 快递单号/货单流水
        TRACKING_REGEX.findAll(text).forEach { match ->
            // 避免跟已有手机号、身份证、银行卡重复
            if (list.none { it.value == match.value }) {
                list.add(ExtractedEntity(EntityType.TRACKING_NUMBER, match.value, "快递单号/编码"))
            }
        }

        return list.distinctBy { it.type to it.value }
    }
}
