package transentia

object Modules {
    // 실행 가능한 Boot 앱
    const val TRANSFER_API = "transfer-api"
    const val TRANSFER_RELAY = "transfer-relay"
    const val TRANSFER_PUBLISHER = "transfer-publisher"
    const val TRANSFER_CONSUMER = "transfer-consumer"
    const val FDS_CONSUMER = "fds-consumer"

    // 스프링 모듈
    const val TRANSFER_APPLICATION = "transfer-application"
    const val TRANSFER_INFRA = "transfer-infra"
    const val FDS_INFRA = "fds-infra"
    const val COMMON_APPLICATION = "common-application"
    const val FDS_APPLICATION = "fds-application"
    const val KAFKA_CONFIG = "kafka-config"
    const val KAFKA_PRODUCER = "kafka-producer"
    const val KAFKA_CONSUMER = "kafka-consumer"

    // 순수 코틀린 모듈
    const val COMMON_DOMAIN = "common-domain"
    const val TRANSFER_DOMAIN = "transfer-domain"
    const val FDS_DOMAIN = "fds-domain"
}