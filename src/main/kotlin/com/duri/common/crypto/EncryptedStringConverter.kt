package com.duri.common.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

/**
 * 엔티티 필드에 @Convert(converter = EncryptedStringConverter::class) 를 붙이면
 * 컬럼에는 암호문이, 애플리케이션에는 평문이 보인다.
 *
 * Spring Boot 가 Hibernate 에 SpringBeanContainer 를 물려주므로 이 빈이 그대로 주입된다.
 */
@Component
@Converter
class EncryptedStringConverter(
    private val cipher: AesGcmCipher,
) : AttributeConverter<String?, String?> {

    override fun convertToDatabaseColumn(attribute: String?): String? =
        attribute?.let(cipher::encrypt)

    override fun convertToEntityAttribute(dbData: String?): String? =
        dbData?.let(cipher::decrypt)
}
