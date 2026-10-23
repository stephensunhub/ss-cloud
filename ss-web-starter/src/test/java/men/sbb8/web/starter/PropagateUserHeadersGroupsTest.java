package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsWebProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropagateUserHeadersGroupsTest {

    private static SsWebProperties.Client bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("ss.web", SsWebProperties.class)
                .orElseGet(SsWebProperties::new).getClient();
    }

    @Test
    void defaultsToAllGroups() {
        SsWebProperties.Client client = bind(Map.of());
        assertTrue(client.propagatesTo("wallet"));
        assertTrue(client.propagatesTo("anything"));
    }

    @Test
    void allIsCaseInsensitive() {
        assertTrue(bind(Map.of("ss.web.client.propagate-user-headers", "ALL")).propagatesTo("wallet"));
    }

    @Test
    void onlyListedGroupsWhenNotAll() {
        SsWebProperties.Client client = bind(Map.of("ss.web.client.propagate-user-headers", "wallet, order"));
        assertTrue(client.propagatesTo("wallet"));
        assertTrue(client.propagatesTo("order"));
        assertFalse(client.propagatesTo("thirdparty"));
    }
}
