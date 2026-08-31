package com.dawnrise.identity.user.policy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsernamePolicyTest {

    @Test
    void suggest_normalizesNameAndUsesMiddleInitial() {
        assertEquals(
                "jose.k.oconnor",
                UsernamePolicy.suggest(" José ", "Kumar", "O'Connor")
        );
    }

    @Test
    void normalizeBase_lowercasesAndRemovesUnsupportedCharacters() {
        assertEquals(
                "mary.jane.smith",
                UsernamePolicy.normalizeBase(" Mary Jane ! Smith ")
        );
    }

    @Test
    void normalizeBase_rejectsReservedUsernames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UsernamePolicy.normalizeBase("ADMIN")
        );
    }

    @Test
    void resolveAvailable_addsNextNumericSuffix() {
        Set<String> existing = Set.of("john.doe", "john.doe.2");
        assertEquals(
                "john.doe.3",
                UsernamePolicy.resolveAvailable("John Doe", existing::contains)
        );
    }

    @Test
    void normalizeBase_leavesRoomForSuffix() {
        String base = UsernamePolicy.normalizeBase("a".repeat(120));
        assertEquals(UsernamePolicy.MAX_BASE_LENGTH, base.length());
    }
}
