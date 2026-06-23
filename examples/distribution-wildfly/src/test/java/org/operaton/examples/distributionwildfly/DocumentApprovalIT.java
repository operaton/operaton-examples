package org.operaton.examples.distributionwildfly;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Testcontainers
class DocumentApprovalIT {

    private static final Network NETWORK = Network.newNetwork();
    private static final String DB_ALIAS = "postgres";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
        .withNetwork(NETWORK)
        .withNetworkAliases(DB_ALIAS)
        .withDatabaseName("operaton")
        .withUsername("operaton")
        .withPassword("operaton");

    @Container
    static GenericContainer<?> wildfly = new GenericContainer<>("operaton/wildfly:2.1.1")
        .dependsOn(postgres)
        .withNetwork(NETWORK)
        .withEnv("DB_URL", "jdbc:postgresql://" + DB_ALIAS + ":5432/operaton")
        .withEnv("DB_USERNAME", "operaton")
        .withEnv("DB_PASSWORD", "operaton")
        .withEnv("DB_DRIVER", "org.postgresql.Driver")
        .withCopyFileToContainer(
            MountableFile.forHostPath("target/distribution-wildfly-0.1.0-SNAPSHOT.war"),
            "/operaton/standalone/deployments/document-approval.war"
        )
        .withExposedPorts(8080)
        // Wait until WildFly has deployed our WAR and the BPMN process is available.
        // The deployment scanner runs every 5s so "/engine-rest/engine" resolves too early.
        .waitingFor(Wait.forHttp("/engine-rest/process-definition/key/document-approval")
            .withStartupTimeout(Duration.ofSeconds(300)));

    private static HttpClient httpClient;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://" + wildfly.getHost();
        RestAssured.port = wildfly.getMappedPort(8080);
        httpClient = HttpClient.newHttpClient();
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
            .body("ended", equalTo(true))
            .extract().path("id");

        List<String> activityIds = historyActivityIds(instanceId, "noneEndEvent");
        assertThat(activityIds).containsExactly("EndEvent_Approved");
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
            .body("ended", equalTo(true))
            .extract().path("id");

        List<String> activityIds = historyActivityIds(instanceId, "noneEndEvent");
        assertThat(activityIds).containsExactly("EndEvent_Review");
    }

    private static List<String> historyActivityIds(String processInstanceId, String activityType) {
        try {
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(RestAssured.baseURI + ":" + RestAssured.port
                        + "/engine-rest/history/activity-instance"
                        + "?processInstanceId=" + processInstanceId
                        + "&activityType=" + activityType))
                    .header("Accept", "application/json")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(response.statusCode()).isEqualTo(200);
            return JsonPath.from(response.body()).getList("activityId");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
