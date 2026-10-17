package com.chuqiyun.proxmoxveams.security;

import com.chuqiyun.proxmoxveams.utils.TotpUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpSecretCipherTest {

    @Test
    void encryptedSecretStaysUsableForVerification() {
        String secret = TotpUtil.generateSecret();
        String stored = TotpSecretCipher.encrypt(secret);

        assertTrue(TotpSecretCipher.isEncrypted(stored));
        assertFalse(stored.contains(secret));
        String plain = TotpSecretCipher.decrypt(stored);
        assertEquals(secret, plain);
        assertEquals(TotpUtil.generate(secret, 1700000000L), TotpUtil.generate(plain, 1700000000L));
    }
}
