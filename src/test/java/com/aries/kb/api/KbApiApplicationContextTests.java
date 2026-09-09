package com.aries.kb.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = KbApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
public class KbApiApplicationContextTests {

    @Test
    public void startsTheApplicationContext() {
    }
}
