package io.github.hyungkishin.transentia.relay

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.common.message.HeaderCodec
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MessagingBeans {

    @Bean
    fun headerCodec(objectMapper: ObjectMapper) = HeaderCodec(objectMapper)

}