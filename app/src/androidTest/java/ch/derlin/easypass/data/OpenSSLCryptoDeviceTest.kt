package ch.derlin.easypass.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [OpenSSLCrypto] with the device crypto providers, which differ from the JVM ones.
 * Run it with the test APK only: `connectedAndroidTest` uninstalls the app and its data.
 */
@RunWith(AndroidJUnit4::class)
class OpenSSLCryptoDeviceTest {

    private val plaintext =
        """[{"name":"github.com","pseudo":"derlin","email":"","password":"s3cr3t","notes":"é ü","creation date":"2017-10-22 10:00","modification date":"","favorite":true}]"""

    /** Made with `openssl enc -aes-256-cbc -pbkdf2 -iter 210000 -md sha512 -salt -base64`. */
    private val vectors = mapOf(
        "essai" to """
            U2FsdGVkX1+4tvyWaWoDM/P+8aXTANFSKPN7o9F1rjcm6LLt+2aIgXcx+gaRTNgt
            2FHqueK2C6f8u9NkOd3+Dx89xSaYz/5Xmc4djDQOSI0TH2nAWck6ipm6FtuKZc3y
            AV2No23O39LB7LX8++6GQY0/NfNCQYg+XBEXiP0barANQT/XGDGv2NRBxR4OTz6g
            OPdiSCamDWaDkRCnRA68HoyfADr5w6ef9jc/OyMdSO/Hzb6XefTpvgPzpYNCkOQN
        """,
        "pässwörd€" to """
            U2FsdGVkX19n6G78cr9L6SRfoDH9anFoslNolNdceV6EQrQjZiYBftvjza2IlJIK
            FSOXIaU/7wYhIJQ4J4YzDnO2R2s/Dzl4uMInLIZJJy/Im2KPcFNe5WIzEhn7rHE3
            Xpf8+HINbip7wiMV8T6ZJuke3gZcEbUyMZuqYhWysGDHpopgfurQaY8VhGFl/Qxs
            X7TSa3Zhfgam+161OtkVCD5Oj6L23mBhbuDkNqI8+FYOoByH48tF5fAhqtL/0FFh
        """,
    )

    @Test
    fun decryptsOpenSslVectors() {
        for ((password, vector) in vectors) {
            val start = System.nanoTime()
            val clear = OpenSSLCrypto.decrypt(password, vector.toByteArray())
            Log.i(TAG, "decrypt '$password': ${(System.nanoTime() - start) / 1_000_000} ms")
            assertEquals("password '$password'", plaintext, String(clear, Charsets.UTF_8))
        }
    }

    @Test
    fun roundTrip() {
        val start = System.nanoTime()
        val encrypted = OpenSSLCrypto.encrypt("pässwörd€", plaintext.toByteArray())
        Log.i(TAG, "encrypt: ${(System.nanoTime() - start) / 1_000_000} ms")
        assertEquals(plaintext, String(OpenSSLCrypto.decrypt("pässwörd€", encrypted)))
    }

    companion object {
        private const val TAG = "OpenSSLCryptoDeviceTest"
    }
}
