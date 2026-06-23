package org.operaton.examples.integrationkeycloak;

import io.restassured.RestAssured;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Testcontainers
class KeycloakIdentityIT {

    private static final Network NETWORK = Network.newNetwork();

    // Build the custom operaton image once per test run; Testcontainers caches by content hash.
    private static final ImageFromDockerfile OPERATON_IMAGE = new ImageFromDockerfile()
        .withFileFromPath("Dockerfile", Path.of("Dockerfile"))
        .withFileFromPath("configuration/default.yml", Path.of("configuration/default.yml"));

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
        .withNetwork(NETWORK)
        .withNetworkAliases("postgres")
        .withDatabaseName("operaton")
        .withUsername("operaton")
        .withPassword("operaton");

    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>("keycloak/keycloak:26.6.3")
        .dependsOn(postgres)
        .withNetwork(NETWORK)
        .withNetworkAliases("keycloak")
        .withEnv("KEYCLOAK_ADMIN", "keycloak")
        .withEnv("KEYCLOAK_ADMIN_PASSWORD", "keycloak")
        .withEnv("KC_HTTP_RELATIVE_PATH", "/auth")
        .withEnv("KC_HEALTH_ENABLED", "true")
        .withCopyFileToContainer(
            MountableFile.forHostPath("keycloak/realm.json"),
            "/opt/keycloak/data/import/realm.json"
        )
        .withCommand("start-dev", "--import-realm")
        .withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/auth/realms/operaton")
            .forPort(8080)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofSeconds(120)));

    @Container
    static GenericContainer<?> operaton = new GenericContainer<>(OPERATON_IMAGE)
        .dependsOn(postgres, keycloak)
        .withNetwork(NETWORK)
        .withEnv("DB_URL", "jdbc:postgresql://postgres:5432/operaton")
        .withEnv("DB_USERNAME", "operaton")
        .withEnv("DB_PASSWORD", "operaton")
        .withEnv("KEYCLOAK_HOST", "http://keycloak:8080")
        .withEnv("KEYCLOAK_REALM", "operaton")
        .withEnv("KEYCLOAK_CLIENT_ID", "operaton-identity-service")
        .withEnv("KEYCLOAK_CLIENT_SECRET", "operaton-keycloak-secret")
        .withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/engine-rest/engine")
            .forPort(8080)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofSeconds(300)));

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://" + operaton.getHost();
        RestAssured.port = operaton.getMappedPort(8080);
        System.out.println("=== OPERATON CONTAINER LOGS ===");
        System.out.println(operaton.getLogs());
        System.out.println("=== END OPERATON LOGS ===");
    }

    /**
     * Fetches a XSRF-TOKEN cookie from the cockpit app page to satisfy the CSRF filter,
     * then returns the token value.  The CookieFilter carries the cookie into the caller's
     * request spec so the XSRF-TOKEN header and cookie are consistent.
     */
    private static String fetchXsrfToken(CookieFilter cookieFilter) {
        String xsrf = given()
            .filter(cookieFilter)
            .when().get("/operaton/app/cockpit/default/")
            .then().statusCode(200)
            .extract().cookie("XSRF-TOKEN");
        return xsrf;
    }

    @Test
    void loginSucceedsWithKeycloakCredentials() {
        var cookieFilter = new CookieFilter();
        String xsrf = fetchXsrfToken(cookieFilter);

        given()
            .filter(cookieFilter)
            .header("X-XSRF-TOKEN", xsrf)
            .contentType(ContentType.URLENC)
            .formParam("username", "admin")
            .formParam("password", "s3cr3t")
        .when()
            .post("/operaton/api/admin/auth/user/default/login/cockpit")
        .then()
            .statusCode(200)
            .body("userId", equalTo("admin"));
    }

    @Test
    void loginFailsWithWrongPassword() {
        var cookieFilter = new CookieFilter();
        String xsrf = fetchXsrfToken(cookieFilter);

        given()
            .filter(cookieFilter)
            .header("X-XSRF-TOKEN", xsrf)
            .contentType(ContentType.URLENC)
            .formParam("username", "admin")
            .formParam("password", "wrongpass")
        .when()
            .post("/operaton/api/admin/auth/user/default/login/cockpit")
        .then()
            .statusCode(401);
    }

    @Test
    void identityQueryReturnsKeycloakSourcedUsersAndGroups() {
        var userIds = given()
            .when().get("/engine-rest/user")
            .then().statusCode(200)
            .extract().jsonPath().getList("id", String.class);

        assertThat(userIds).contains("admin", "demo");

        var groupIds = given()
            .when().get("/engine-rest/group")
            .then().statusCode(200)
            .extract().jsonPath().getList("id", String.class);

        assertThat(groupIds).contains("operaton-admin", "management");
    }
}
