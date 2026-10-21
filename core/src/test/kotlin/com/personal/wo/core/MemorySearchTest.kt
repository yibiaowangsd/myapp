package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MemorySearchTest {
    private val today=LocalDate.of(2026,10,21)
    private val zone=ZoneId.of("Asia/Shanghai")
    @Test fun relativeDaysUseTheArchiveTimezoneAndKeepTheKeyword() {
        val result=memorySearch("昨天 海边",today,zone)
        assertEquals(window(today.minusDays(1),zone),result.window)
        assertEquals("海边",result.text)
    }
    @Test fun fullDatesAndMonthsSelectCompleteCalendarWindows() {
        assertEquals(window(LocalDate.of(2024,2,29),zone),memorySearch("2024年2月29日",today,zone).window)
        val result=memorySearch("2024-02 旅行",today,zone)
        assertEquals(window(LocalDate.of(2024,2,1),zone).start,result.window!!.start)
        assertEquals(window(LocalDate.of(2024,2,29),zone).end,result.window!!.end)
        assertEquals("旅行",result.text)
    }
    @Test fun invalidDateRemainsLiteralSearchInsteadOfChangingScope() {
        assertEquals(MemorySearch("2025-02-29",null),memorySearch("2025-02-29",today,zone))
        assertEquals(MemorySearch("昨天的咖啡",null),memorySearch("昨天的咖啡",today,zone))
    }
    @Test fun sqlWildcardsAreLiteralUserText() {
        assertEquals("%100\\%\\_完成%",memoryLikePattern("100%_完成"))
    }
}
