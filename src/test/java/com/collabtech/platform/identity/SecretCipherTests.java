package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.identity.infrastructure.security.SecretCipher;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTests {
    @Test void encryptionIsRandomizedAndTamperingIsRejected() {
        var cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
        String first = cipher.encrypt("secret"), second = cipher.encrypt("secret");
        assertNotEquals(first, second);
        assertEquals("secret", cipher.decrypt(first));
        byte[] bytes = Base64.getDecoder().decode(first); bytes[bytes.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes)));
    }
}
