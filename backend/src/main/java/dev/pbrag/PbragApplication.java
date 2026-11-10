package dev.pbrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** PB-RAG 后端进程入口，同时启用定时任务以驱动异步入库 Worker。 */
@SpringBootApplication
@EnableScheduling
public class PbragApplication {
	private static final String BANNER = """
			 ____  ____        ____      _      ____
			|  _ \\| __ )      |  _ \\    / \\    / ___|
			| |_) |  _ \\ _____| |_) |  / _ \\  | |  _
			|  __/| |_) |_____|  _ <  / ___ \\ | |_| |
			|_|   |____/      |_| \\_\\/_/   \\_\\ \\____|

			              PB-RAG Knowledge Agent
			              :: Spring Boot :: (v%s)

			""";

	/** 启动 Spring Boot 应用上下文、HTTP 服务与定时入库任务。 */
	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(PbragApplication.class);
		application.setBanner((environment, sourceClass, output) ->
				output.print(BANNER.formatted(SpringBootVersion.getVersion())));
		application.run(args);
	}

}
