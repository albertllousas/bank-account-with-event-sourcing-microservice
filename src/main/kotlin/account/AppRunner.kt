package account

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AppRunner

fun main(args: Array<String>) {
    runApplication<AppRunner>(*args)
}
