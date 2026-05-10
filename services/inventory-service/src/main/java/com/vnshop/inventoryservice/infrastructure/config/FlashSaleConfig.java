package com.vnshop.inventoryservice.infrastructure.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class FlashSaleConfig {

	@Bean
	RedisScript<Long> flashReserveScript() throws IOException {
		return redisScript("redis-lua/flash-reserve.lua");
	}

	@Bean
	RedisScript<Long> flashReleaseScript() throws IOException {
		return redisScript("redis-lua/flash-release.lua");
	}

	private RedisScript<Long> redisScript(String path) throws IOException {
		var resource = new ClassPathResource(path);
		var script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		return RedisScript.of(script, Long.class);
	}
}
