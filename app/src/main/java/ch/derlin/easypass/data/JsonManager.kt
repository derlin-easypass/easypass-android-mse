package ch.derlin.easypass.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import org.apache.commons.ssl.OpenSSL
import java.io.*
import java.lang.reflect.Type
import java.security.GeneralSecurityException


/**
 * this class provides utilities in order to encrypt/data data (openssl style,
 * pass and auto-generated salt, no iv) and to serialize/deserialize them (in a
 * json format).
 *
 *
 * it is also possible to write the content of a list in a cleartext "pretty"
 * valid json format.
 *
 * @author Lucy Linder
 * date: 21.12.2012
 */
object JsonManager {

    /** A GSON instance configured to serialized only field with the @Expose annotation. */
    private val gson: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    /**
     * Encrypts the data with the cipher given in parameter and
     * serializes it in json format.
     *
     * @param data     the data
     * @param filepath the output filepath
     * @param password the password
     * @param algo     the algorithm (aes-128-cbc for example, see the openssl conventions)
     * @throws IOException
     */
    @Throws(IOException::class)
    fun serialize(
        data: Any, filepath: String,
        password: String, algo: String = "aes-128-cbc"
    ) {
        serialize(data, FileOutputStream(filepath), password, algo)
    }// end serialize


    /**
     * Encrypts data with the cipher given in parameter and
     * serializes it in json format.
     *
     * @param data     the data
     * @param outStream the output stream to write to
     * @param password the password
     * @param algo     the algorithm (aes-128-cbc for example, see the openssl conventions)
     * @throws IOException
     */
    @Throws(IOException::class)
    fun serialize(
        data: Any, outStream: OutputStream?,
        password: String, algo: String = "aes-128-cbc"
    ) {

        if (outStream == null) {
            throw IllegalStateException("The outputstream cannot be null !")
        }

        try {
            outStream.write(
                OpenSSL.encrypt(
                    algo, password.toCharArray(),
                    gson.toJson(data).toByteArray(charset("UTF-8"))
                )
            )
            outStream.write("\r\n".toByteArray())
            outStream.write(System.getProperty("line.separator").toByteArray())
            outStream.flush()

        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
        } finally {
            outStream.close()
        }

    }// end serialize


    /**
     * Deserializes and returns the object of type "type" contained in the
     * specified file. the decryption of the data is performed with the cipher
     * given in parameter.<br></br>
     * The object in the file must have been encrypted after a json serialisation.
     *
     * @param filepath the filepath
     * @param password the password
     * @param type     the type of the data serialized
     * @param algo     the algorithm (aes-128-cbc for example, see the openssl conventions)
     * @return the decrypted data ( a list of ? )
     * @throws WrongCredentialsException if the password or the magic number is incorrect
     * @throws IOException
     */
    @Throws(WrongCredentialsException::class, IOException::class)
    fun deserialize(
        filepath: String, password: String,
        type: Type, algo: String = "aes-128-cbc"
    ): Any {
        return deserialize(FileInputStream(filepath), password, type, algo)
    }


    /**
     * Deserializes and returns the object of type "Type" contained in the
     * specified file. the decryption of the data is performed with the cipher
     * given in parameter.<br></br>
     * The object in the file must have been encrypted after a json serialisation.
     *
     * @param stream   the stream to read from
     * @param password the password
     * @param type     the type of the data serialized
     * @param algo     the algorithm (aes-128-cbc for example, see the openssl conventions)
     * @return the decrypted data ( a list of ? )
     * @throws WrongCredentialsException if the password or the magic number is incorrect
     * @throws IOException
     */
    @Throws(WrongCredentialsException::class, IOException::class)
    fun deserialize(
        stream: InputStream?, password: String,
        type: Type, algo: String = "aes-128-cbc"
    ): Any {

        if (stream == null || stream.available() == 0) {
            throw IllegalStateException("the stream is null or unavailable")
        }
        try {

            val data = gson.fromJson<List<*>>(
                InputStreamReader(
                    OpenSSL
                        .decrypt(algo, password.toCharArray(), stream), "UTF-8"
                ), type
            )
            return data ?: throw WrongCredentialsException()

        } catch (e: JsonIOException) {
            throw WrongCredentialsException(e.message ?: "json IO error")
        } catch (e: GeneralSecurityException) {
            throw WrongCredentialsException(e.message ?: "general security error")
        } catch (e: JsonSyntaxException) {
            throw WrongCredentialsException(e.message ?: "json syntax error")
        } finally {
            stream.close()
        }// end try

    }// end deserialize


    /**
     * The exception thrown in case of a wrong password.
     */
    class WrongCredentialsException : Exception {
        constructor() : super()
        constructor(message: String) : super(message)
    }

}// end class
