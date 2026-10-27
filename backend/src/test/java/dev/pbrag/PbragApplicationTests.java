package dev.pbrag;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("需要 application.yml 中的 PostgreSQL 和 Redis 地址可连接后再启用")
@SpringBootTest
class PbragApplicationTests {

	@Test
	void contextLoads() {
	}

}
