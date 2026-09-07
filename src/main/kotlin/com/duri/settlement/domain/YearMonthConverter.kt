package com.duri.settlement.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** CHAR(6) 'YYYYMM' <-> java.time.YearMonth */
@Converter
class YearMonthConverter : AttributeConverter<YearMonth?, String?> {

    override fun convertToDatabaseColumn(attribute: YearMonth?): String? = attribute?.format(FORMATTER)

    override fun convertToEntityAttribute(dbData: String?): YearMonth? =
        dbData?.trim()?.takeIf { it.isNotEmpty() }?.let { YearMonth.parse(it, FORMATTER) }

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMM")
    }
}
