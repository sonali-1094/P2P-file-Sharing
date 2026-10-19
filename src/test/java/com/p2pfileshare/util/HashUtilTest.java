package com.p2pfileshare.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class HashUtilTest {

    @Test
    void sha256OfAbcMatchesKnownVector() {
        // Standard NIST test vector for "abc".
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HashUtil.sha256("abc"));
    }

    @Test
    void sha256OfEmptyInputMatchesKnownVector() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtil.sha256(new byte[0]));
    }

    @Test
    void sameInputAlwaysGivesSameHash() {
        byte[] data = {1, 2, 3, 4, 5};
        assertEquals(HashUtil.sha256(data), HashUtil.sha256(data.clone()));
    }

    @Test
    void differentInputGivesDifferentHash() {
        assertNotEquals(HashUtil.sha256("hello"), HashUtil.sha256("hellp"));
    }

    @Test
    void hexOutputIsLowercaseAndZeroPadded() {
        assertEquals("000fff", HashUtil.toHex(new byte[]{0, 15, (byte) 255}));
    }

    @Test
    void hashIsAlways64HexCharacters() {
        assertEquals(64, HashUtil.sha256("any text").length());
    }
}
