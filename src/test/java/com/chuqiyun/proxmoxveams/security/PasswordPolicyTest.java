package com.chuqiyun.proxmoxveams.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {
    @Test
    void acceptsEightCharactersWithTwoCategories() {
        assertTrue(PasswordPolicy.isValid("password1"));
        assertTrue(PasswordPolicy.isValid("PASSWORD!"));
        assertTrue(PasswordPolicy.isValid("1234567!"));
    }

    @Test
    void rejectsShortSingleCategoryAndWhitespaceOnlyPasswords() {
        assertFalse(PasswordPolicy.isValid("Ab1!"));
        assertFalse(PasswordPolicy.isValid("password"));
        assertFalse(PasswordPolicy.isValid("        "));
        assertFalse(PasswordPolicy.isValid(null));
    }

    @Test
    void acceptsAllFourCategoriesAndRejectsOverlongValues() {
        assertTrue(PasswordPolicy.isValid("Abcd1234!"));
        assertFalse(PasswordPolicy.isValid("A1!" + "a".repeat(126)));
    }
}
