package io.github.hyungkishin.fdsystem

import org.springframework.stereotype.Service

@Service
class HelloService {
    fun greet(name: String): String = "FDS 시스템 가보자고!!!, $name"
}