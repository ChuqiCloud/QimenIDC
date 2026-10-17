package com.chuqiyun.proxmoxveams.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredPasswordCipherTest {

    @Test
    void encryptsWithRandomIvAndDecrypts() {
        String first = StoredPasswordCipher.encrypt("Pve-password-中文");
        String second = StoredPasswordCipher.encrypt("Pve-password-中文");

        assertTrue(StoredPasswordCipher.isEncrypted(first));
        assertNotEquals(first, second);
        assertEquals("Pve-password-中文", StoredPasswordCipher.decrypt(first));
        assertEquals("Pve-password-中文", StoredPasswordCipher.decrypt(second));
    }

    @Test
    void keepsLegacyPlaintextReadableAndEncryptsOnWrite() {
        assertEquals("legacy-password", StoredPasswordCipher.decrypt("legacy-password"));
        assertTrue(StoredPasswordCipher.isEncrypted(StoredPasswordCipher.encrypt("legacy-password")));
    }

    @Test
    void preservesNullAndEncryptsEmptyPassword() {
        assertNull(StoredPasswordCipher.encrypt(null));
        assertNull(StoredPasswordCipher.decrypt(null));
        String encrypted = StoredPasswordCipher.encrypt("");
        assertTrue(StoredPasswordCipher.isEncrypted(encrypted));
        assertEquals("", StoredPasswordCipher.decrypt(encrypted));
    }

    @Test
    void rejectsTamperedAndUnknownVersionCiphertext() {
        String encrypted = StoredPasswordCipher.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 1)
                + (encrypted.endsWith("A") ? "B" : "A");

        assertThrows(IllegalArgumentException.class, () -> StoredPasswordCipher.decrypt(tampered));
        assertThrows(IllegalArgumentException.class,
                () -> StoredPasswordCipher.decrypt(encrypted.replace("QIDENC:1:", "QIDENC:2:")));
        assertEquals(encrypted, StoredPasswordCipher.decrypt(StoredPasswordCipher.encrypt(encrypted)));
    }
}
