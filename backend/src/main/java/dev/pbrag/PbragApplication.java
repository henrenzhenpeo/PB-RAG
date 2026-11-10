package dev.pbrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** PB-RAG 后端进程入口，同时启用定时任务以驱动异步入库 Worker。 */
@SpringBootApplication
@EnableScheduling
public class PbragApplication {

	/** 启动 Spring Boot 应用上下文、HTTP 服务与定时入库任务。 */
	public static void main(String[] args) {
		SpringApplication.run(PbragApplication.class, args);
	}

}
