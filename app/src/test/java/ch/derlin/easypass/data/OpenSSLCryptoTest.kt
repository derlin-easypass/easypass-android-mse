package ch.derlin.easypass.data

import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import java.util.concurrent.TimeUnit

class OpenSSLCryptoTest {

    @Test
    fun decryptsOpenSslVectors() {
        for ((password, vector) in VECTORS) {
            val clear = OpenSSLCrypto.decrypt(password, vector.toByteArray())
            assertEquals("password '$password'", PLAINTEXT, String(clear, Charsets.UTF_8))
        }
    }

    @Test
    fun roundTrip() {
        val texts = listOf("", "0123456789abcdef", "é ü € 🔑 ".repeat(500))
        for (text in texts) {
            val encrypted = OpenSSLCrypto.encrypt("pässwörd€", text.toByteArray(Charsets.UTF_8))
            val decrypted = OpenSSLCrypto.decrypt("pässwörd€", encrypted)
            assertEquals(text, String(decrypted, Charsets.UTF_8))
        }
    }

    @Test
    fun outputShape() {
        val first = String(OpenSSLCrypto.encrypt("essai", PLAINTEXT.toByteArray()), Charsets.US_ASCII)
        val second = String(OpenSSLCrypto.encrypt("essai", PLAINTEXT.toByteArray()), Charsets.US_ASCII)

        assertTrue(first.startsWith("U2FsdGVkX1"))
        assertTrue(first.endsWith("\n"))
        assertFalse(first.endsWith("\n\n"))
        assertFalse(first.contains('\r'))
        val lines = first.removeSuffix("\n").split("\n")
        assertTrue(lines.all { it.length in 1..64 })
        assertTrue(lines.dropLast(1).all { it.length == 64 })
        assertNotEquals("random salt", first, second)
    }

    @Test(expected = GeneralSecurityException::class)
    fun wrongPasswordThrows() {
        // fixed vector: the result is deterministic
        OpenSSLCrypto.decrypt("wrong", VECTORS.getValue("essai").toByteArray())
    }

    @Test
    fun wrongPasswordThroughJsonManager() {
        val out = ByteArrayOutputStream()
        JsonManager.serialize(arrayListOf(Account(name = "github.com", password = "s3cr3t")), out, "essai")

        for (password in listOf("wrong", "essai2", "Essai", "")) {
            assertWrongCredentials { deserialize(out.toByteArray(), password) }
        }
    }

    @Test
    fun badInputThroughJsonManager() {
        val valid = VECTORS.getValue("essai")
        assertWrongCredentials { deserialize("not base64 !!".toByteArray(), "essai") }
        assertWrongCredentials { deserialize("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=\n".toByteArray(), "essai") }
        assertWrongCredentials { deserialize(valid.substring(0, 40).toByteArray(), "essai") }
    }

    @Test
    fun jsonManagerRoundTrip() {
        val accounts = arrayListOf(
            Account(name = "github.com", pseudo = "derlin", password = "s3cr3t", notes = "é ü"),
            Account(name = "other", isFavorite = true),
        )
        val out = ByteArrayOutputStream()
        JsonManager.serialize(accounts, out, "essai")

        @Suppress("UNCHECKED_CAST")
        val read = deserialize(out.toByteArray(), "essai") as ArrayList<Account>
        assertEquals(accounts, read)
    }

    @Test
    fun opensslCliDecryptsOurOutput() {
        val openssl = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, "openssl") }.firstOrNull { it.canExecute() }
        assumeTrue("openssl not on the PATH", openssl != null)

        val file = File.createTempFile("easypass", ".enc")
        try {
            file.writeBytes(OpenSSLCrypto.encrypt("pässwörd€", PLAINTEXT.toByteArray(Charsets.UTF_8)))
            val process = ProcessBuilder(
                openssl!!.path, "enc", "-d", "-aes-256-cbc", "-pbkdf2",
                "-iter", OpenSSLCrypto.PBKDF2_ITERATIONS.toString(), "-md", "sha512",
                "-base64", "-in", file.path, "-pass", "env:EASYPASS_TEST_PASS"
            ).apply { environment()["EASYPASS_TEST_PASS"] = "pässwörd€" }.start()
            val output = process.inputStream.readBytes()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS))
            assertEquals(process.errorStream.bufferedReader().readText(), 0, process.exitValue())
            assertEquals(PLAINTEXT, String(output, Charsets.UTF_8))
        } finally {
            file.delete()
        }
    }

    private fun deserialize(data: ByteArray, password: String): Any =
        JsonManager.deserialize(
            ByteArrayInputStream(data), password,
            object : TypeToken<SessionSerialisationType>() {}.type
        )

    private fun assertWrongCredentials(block: () -> Unit) {
        try {
            block()
        } catch (e: JsonManager.WrongCredentialsException) {
            return
        }
        throw AssertionError("expected WrongCredentialsException")
    }

    companion object {
        private const val PLAINTEXT =
            """[{"name":"github.com","pseudo":"derlin","email":"","password":"s3cr3t","notes":"é ü","creation date":"2017-10-22 10:00","modification date":"","favorite":true}]"""

        /** Made with `openssl enc -aes-256-cbc -pbkdf2 -iter 210000 -md sha512 -salt -base64`. */
        val VECTORS = mapOf(
            "essai" to """
                U2FsdGVkX1+4tvyWaWoDM/P+8aXTANFSKPN7o9F1rjcm6LLt+2aIgXcx+gaRTNgt
                2FHqueK2C6f8u9NkOd3+Dx89xSaYz/5Xmc4djDQOSI0TH2nAWck6ipm6FtuKZc3y
                AV2No23O39LB7LX8++6GQY0/NfNCQYg+XBEXiP0barANQT/XGDGv2NRBxR4OTz6g
                OPdiSCamDWaDkRCnRA68HoyfADr5w6ef9jc/OyMdSO/Hzb6XefTpvgPzpYNCkOQN
            """.trimIndent() + "\n",
            "pässwörd€" to """
                U2FsdGVkX19n6G78cr9L6SRfoDH9anFoslNolNdceV6EQrQjZiYBftvjza2IlJIK
                FSOXIaU/7wYhIJQ4J4YzDnO2R2s/Dzl4uMInLIZJJy/Im2KPcFNe5WIzEhn7rHE3
                Xpf8+HINbip7wiMV8T6ZJuke3gZcEbUyMZuqYhWysGDHpopgfurQaY8VhGFl/Qxs
                X7TSa3Zhfgam+161OtkVCD5Oj6L23mBhbuDkNqI8+FYOoByH48tF5fAhqtL/0FFh
            """.trimIndent() + "\n",
        )
    }
}
