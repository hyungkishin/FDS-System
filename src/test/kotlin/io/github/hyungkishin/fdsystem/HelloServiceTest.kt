package io.github.hyungkishin.fdsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HelloServiceTest {
    private val service = HelloService()

    @Test
    fun `Jacoco 학습 테스트`() {
        assertEquals("FDS 시스템 가보자고!!!, hyungki", service.greet("hyungki"))
    }
}