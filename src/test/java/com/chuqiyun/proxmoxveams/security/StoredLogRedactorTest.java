package com.chuqiyun.proxmoxveams.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 历史审计日志定向脱敏与异常 JSON 处置。
 *
 * @author 星禾
 */
class StoredLogRedactorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void redactsNestedCredentialsAndPreservesOrdinaryValues() throws Exception {
        String body = "{\"hostId\":7,\"nested\":{\"newPassword\":\"new-secret\",\"name\":\"same\"},"
                + "\"items\":[{\"SSH_password\":\"ssh-secret\",\"PVE-Password\":\"pve-secret\","
                + "\"csrfToken\":\"csrf-secret\",\"TICKET\":\"ticket-secret\",\"active\":true}],"
                + "\"description\":\"the password setting is enabled\",\"nullable\":null}";

        String redacted = StoredLogRedactor.redact(body);
        JsonNode result = mapper.readTree(redacted);

        assertEquals(7, result.get("hostId").asInt());
        assertEquals("same", result.get("nested").get("name").asText());
        assertEquals("***", result.get("nested").get("newPassword").asText());
        JsonNode item = result.get("items").get(0);
        assertEquals("***", item.get("SSH_password").asText());
        assertEquals("***", item.get("PVE-Password").asText());
        assertEquals("***", item.get("csrfToken").asText());
        assertEquals("***", item.get("TICKET").asText());
        assertEquals(true, item.get("active").asBoolean());
        assertEquals("the password setting is enabled", result.get("description").asText());
        assertFalse(redacted.contains("-secret"));
    }

    @Test
    void leavesUnchangedJsonFormattingAndAlreadyMaskedFieldsIntact() {
        String ordinary = " { \"hostId\" : 7, \"name\" : \"node-one\", \"items\" : [1, true] } ";
        String masked = "{ \"password\" : \"***\", \"csrfToken\" : null }";

        assertEquals(ordinary, StoredLogRedactor.redact(ordinary));
        assertEquals(masked, StoredLogRedactor.redact(masked));
    }

    @Test
    void handlesArrayRootsAndCredentialObjectsWithoutLeakingTheirContent() throws Exception {
        String body = "[{\"password\":{\"raw\":\"hidden-secret\"},\"id\":9},{\"auth\":{\"ticket\":[\"hidden-ticket\"]}}]";

        JsonNode result = mapper.readTree(StoredLogRedactor.redact(body));

        assertEquals("***", result.get(0).get("password").asText());
        assertEquals(9, result.get(0).get("id").asInt());
        assertEquals("***", result.get(1).get("auth").get("ticket").asText());
    }

    @Test
    void replacesTruncatedDuplicateAndTrailingJsonWithFixedPlaceholder() {
        assertEquals(StoredLogRedactor.INVALID_JSON_PLACEHOLDER,
                StoredLogRedactor.redact("{\"newPassword\":\"truncated-secret"));
        assertEquals(StoredLogRedactor.INVALID_JSON_PLACEHOLDER,
                StoredLogRedactor.redact("{\"password\":\"older-secret\",\"password\":\"***\"}"));
        assertEquals(StoredLogRedactor.INVALID_JSON_PLACEHOLDER,
                StoredLogRedactor.redact("{\"hostId\":7} {\"newPassword\":\"trailing-secret\"}"));
    }

    @Test
    void preservesPlainTextAndLegacyMarkersButOmitsSensitiveAssignments() {
        assertEquals("operation completed", StoredLogRedactor.redact("operation completed"));
        assertEquals("[service] operation completed", StoredLogRedactor.redact("[service] operation completed"));
        assertEquals("[sensitive response omitted]", StoredLogRedactor.redact("[sensitive response omitted]"));
        assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER,
                StoredLogRedactor.redact("change failed: NEW_PASSWORD=historical-secret"));
        assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER,
                StoredLogRedactor.redact("[service] password: historical-secret"));
    }

    @Test
    void preservesEmptyInputsAndRemainsIdempotent() {
        assertNull(StoredLogRedactor.redact(null));
        assertEquals(" \n", StoredLogRedactor.redact(" \n"));
        String body = "{\"password\":\"original-secret\",\"hostId\":7}";
        String redacted = StoredLogRedactor.redact(body);
        assertEquals(redacted, StoredLogRedactor.redact(redacted));
        assertEquals(StoredLogRedactor.INVALID_JSON_PLACEHOLDER,
                StoredLogRedactor.redact(StoredLogRedactor.INVALID_JSON_PLACEHOLDER));
    }

    @Test
    void redactsEncodedQueryKeysAndRetainsOrdinaryEncodingOrderAndDuplicateKeys() {
        String query = "hostId=7&new%50assword=secret%26value&name=node%20one&SSH%5fpassword=ssh-secret"
                + "&tag=one&tag=two&empty=&PVE-password=pve-secret";

        assertEquals("hostId=7&new%50assword=***&name=node%20one&SSH%5fpassword=***"
                        + "&tag=one&tag=two&empty=&PVE-password=***",
                StoredLogRedactor.redactQueryString(query));
        assertEquals("name=node%20one&tag=one&tag=two", StoredLogRedactor.redactQueryString("name=node%20one&tag=one&tag=two"));
        assertEquals("newPassword=***", StoredLogRedactor.redactQueryString("newPassword=***"));
    }

    @Test
    void usesFixedPlaceholderForInvalidQueryEncoding() {
        assertEquals(StoredLogRedactor.INVALID_QUERY_PLACEHOLDER,
                StoredLogRedactor.redactQueryString("%GG=password-value"));
        assertEquals(StoredLogRedactor.INVALID_QUERY_PLACEHOLDER,
                StoredLogRedactor.redactQueryString("hostId=7&name=%2"));
        assertEquals(StoredLogRedactor.INVALID_QUERY_PLACEHOLDER,
                StoredLogRedactor.redactQueryString("password=%GG"));
        assertNull(StoredLogRedactor.redactQueryString(null));
        assertEquals("", StoredLogRedactor.redactQueryString(""));
        assertEquals(StoredLogRedactor.INVALID_QUERY_PLACEHOLDER,
                StoredLogRedactor.redactQueryString(StoredLogRedactor.INVALID_QUERY_PLACEHOLDER));
    }
}
