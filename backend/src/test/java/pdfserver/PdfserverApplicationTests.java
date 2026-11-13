package pdfserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5432/java_document_server",
		"spring.datasource.driver-class-name=org.postgresql.Driver",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
		"spring.data.redis.password=redispassword123",
		"management.health.redis.enabled=false",
		"management.health.db.enabled=false",
		"management.health.diskspace.enabled=false",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=none"
})
class PdfserverApplicationTests {

	@Test
	void contextLoads() {
	}

}
