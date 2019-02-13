package org.textup.type

import org.springframework.http.HttpStatus
import org.textup.test.*
import spock.lang.*

class ResultStatusSpec extends Specification {

    void "test conversion"() {
        expect: "from HttpStatus"
        ResultStatus.convert(null) == null
        ResultStatus.convert(HttpStatus.UPGRADE_REQUIRED) == ResultStatus.UPGRADE_REQUIRED

        and: "from integer status code"
        ResultStatus.convert(-88) == ResultStatus.INTERNAL_SERVER_ERROR
        ResultStatus.convert(400) == ResultStatus.BAD_REQUEST
    }
}
