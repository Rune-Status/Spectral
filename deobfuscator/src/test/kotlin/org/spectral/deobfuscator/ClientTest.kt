package org.spectral.deobfuscator

import org.junit.jupiter.api.Test
import java.io.File

class ClientTest {

    @Test
    fun launchClient() {
        val file = File("../gamepack-deob-190.jar")
        val client = TestClient(file)
        client.start()
        while(true) {}
    }
}