package com.tfzhang.linuxback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.rabbitmq.enabled=false",
        "app.jwt.secret=test-secret-at-least-32-bytes-long!!"
})
class LinuxbackApplicationTests {

    @Test
    void contextLoads() {
    }

}
