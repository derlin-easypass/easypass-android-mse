package ch.derlin.easypass.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import java.io.*
import java.lang.reflect.Type
import java.security.GeneralSecurityException


/**
 * this class provides utilities in order to encrypt/data data (openssl style,
 * see [OpenSSLCrypto]) and to serialize/deserialize them (in a json format).
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
     * Encrypts the data and serializes it in json format.
     *
     * @param data     the data
     * @param filepath the output filepath
     * @param password the password
     * @throws IOException
     * @throws GeneralSecurityException if the encryption failed
     */
    @Throws(IOException::class, GeneralSecurityException::class)
    fun serialize(data: Any, filepath: String, password: String) {
        serialize(data, FileOutputStream(filepath), password)
    }// end serialize


    /**
     * Encrypts data and serializes it in json format.
     *
     * @param data     the data
     * @param outStream the output stream to write to
     * @param password the password
     * @throws IOException
     * @throws GeneralSecurityException if the encryption failed
     */
    @Throws(IOException::class, GeneralSecurityException::class)
    fun serialize(data: Any, outStream: OutputStream?, password: String) {

        if (outStream == null) {
            throw IllegalStateException("The outputstream cannot be null !")
        }

        outStream.use {
            it.write(OpenSSLCrypto.encrypt(password, gson.toJson(data).toByteArray(Charsets.UTF_8)))
            it.flush()
        }

    }// end serialize


    /**
     * Deserializes and returns the object of type "type" contained in the
     * specified file.<br></br>
     * The object in the file must have been encrypted after a json serialisation.
     *
     * @param filepath the filepath
     * @param password the password
     * @param type     the type of the data serialized
     * @return the decrypted data ( a list of ? )
     * @throws WrongCredentialsException if the password or the magic number is incorrect
     * @throws IOException
     */
    @Throws(WrongCredentialsException::class, IOException::class)
    fun deserialize(filepath: String, password: String, type: Type): Any {
        return deserialize(FileInputStream(filepath), password, type)
    }


    /**
     * Deserializes and returns the object of type "Type" contained in the
     * specified stream.<br></br>
     * The object in the stream must have been encrypted after a json serialisation.
     *
     * @param stream   the stream to read from
     * @param password the password
     * @param type     the type of the data serialized
     * @return the decrypted data ( a list of ? )
     * @throws WrongCredentialsException if the password or the magic number is incorrect
     * @throws IOException
     */
    @Throws(WrongCredentialsException::class, IOException::class)
    fun deserialize(stream: InputStream?, password: String, type: Type): Any {

        if (stream == null || stream.available() == 0) {
            throw IllegalStateException("the stream is null or unavailable")
        }
        try {
            val json = String(OpenSSLCrypto.decrypt(password, stream.readBytes()), Charsets.UTF_8)
            // a wrong password can give a valid padding by chance: the json parsing catches it
            val data = gson.fromJson<List<*>>(json, type)
            return data ?: throw WrongCredentialsException()

        } catch (e: GeneralSecurityException) {
            throw WrongCredentialsException(e.message ?: "general security error")
        } catch (e: JsonParseException) {
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
