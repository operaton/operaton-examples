package org.operaton.examples.distributiontomcat;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@Testcontainers
class DocumentApprovalIT {

    private static final Network NETWORK = Network.newNetwork();
    private static final String DB_ALIAS = "postgres";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withNetwork(NETWORK)
        .withNetworkAliases(DB_ALIAS)
        .withDatabaseName("operaton")
        .withUsername("operaton")
        .withPassword("operaton");

    @Container
    static GenericContainer<?> tomcat = new GenericContainer<>("operaton/tomcat:2.1.1")
        .dependsOn(postgres)
        .withNetwork(NETWORK)
        .withEnv("DB_URL", "jdbc:postgresql://" + DB_ALIAS + ":5432/operaton")
        .withEnv("DB_USERNAME", "operaton")
        .withEnv("DB_PASSWORD", "operaton")
        .withEnv("DB_DRIVER", "org.postgresql.Driver")
        .withCopyFileToContainer(
            MountableFile.forHostPath("target/distribution-tomcat-0.1.0-SNAPSHOT.war"),
            "/operaton/webapps/document-approval.war"
        )
        .withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/engine-rest/engine")
            .withStartupTimeout(Duration.ofSeconds(300)));

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://" + tomcat.getHost();
        RestAssured.port = tomcat.getMappedPort(8080);
    }

    @Test
    void regularDocumentIsApproved() {
        String instanceId = given()
            .contentType(ContentType.JSON)
            .body("{\"variables\": {\"documentType\": {\"value\": \"invoice\", \"type\": \"String\"}}}")
        .when()
            .post("/engine-rest/process-definition/key/document-approval/start")
        .then()
            .statusCode(200)
            .extract().path("id");

        given()
            .queryParam("processInstanceId", instanceId)
            .queryParam("completed", true)
        .when()
            .get("/engine-rest/history/process-instance")
        .then()
            .statusCode(200)
            .body("[0].endActivityId", equalTo("EndEvent_Approved"));
    }

    @Test
    void contractRequiresReview() {
        String instanceId = given()
            .contentType(ContentType.JSON)
            .body("{\"variables\": {\"documentType\": {\"value\": \"contract\", \"type\": \"String\"}}}")
        .when()
            .post("/engine-rest/process-definition/key/document-approval/start")
        .then()
            .statusCode(200)
            .extract().path("id");

        given()
            .queryParam("processInstanceId", instanceId)
            .queryParam("completed", true)
        .when()
            .get("/engine-rest/history/process-instance")
        .then()
            .statusCode(200)
            .body("[0].endActivityId", equalTo("EndEvent_Review"));
    }
}
