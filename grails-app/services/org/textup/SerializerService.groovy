package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.apache.commons.codec.binary.Base64

@GrailsCompileStatic
@Transactional
class SerializerService {

    ResultFactory resultFactory

    // Serialization advice from https://stackoverflow.com/a/8887244
    // Why we need to Base64 encode byte array before transforming to text
    // https://haacked.com/archive/2012/01/30/hazards-of-converting-binary-data-to-a-string.aspx/
    Result<String> serialize(Serializable obj) {
        try {
            new ByteArrayOutputStream().withCloseable { ByteArrayInputStream bStream ->
                new ObjectOutputStream(bStream).withCloseable { ObjectOutputStream oStream ->
                    oStream.writeObject(obj)
                    oStream.flush()
                    resultFactory.success(Base64.encodeBase64String(bStream.toByteArray()))
                }
            }
        }
        catch (Throwable e) {
            log.error("Helpers.serialize: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }

    <T extends Serializable> Result<T> deserialize(String dataString) {
        try {
            new ByteArrayInputStream(Base64.decodeBase64(dataString.bytes))
                .withCloseable { ByteArrayInputStream bStream ->
                    new ObjectInputStream(bStream).withCloseable { iStream ->
                        resultFactory.success(iStream.readObject().asType(T))
                    }
                }
        }
        catch (Throwable e) {
            log.error("Helpers.deserialize: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
