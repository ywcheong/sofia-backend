package ywcheong.sofia.commons

open class BusinessException(
    override val message: String,
    override val cause: Throwable? = null
): RuntimeException()