package org.textup.test

import grails.compiler.GrailsTypeChecked
import org.textup.Tuple

@GrailsTypeChecked
class OutputStreamCaptor {

    private PrintStream _originalOut
    private PrintStream _originalErr

    private ByteArrayOutputStream _newOut
    private ByteArrayOutputStream _newErr

    // Partly from https://stackoverflow.com/a/4183433
    // (1) We didn't use `FileDescriptor` references to stdout and stderr because Grails's test
    // environment uses special stdout and stderr classes. The class for stdout is
    // `org.codehaus.groovy.grails.test.io.SystemOutAndErrSwapper.TestOutputCapturingPrintStream`.
    // The class for stderr is `org.codehaus.groovy.grails.cli.logging.GrailsConsoleErrorPrintStream`
    // Therefore, we need to store references to this classes then restore the originals.
    // (2) ALSO, because of some special Grails magic, we need to override BOTH stdout and
    // stderr at the same time order to reachieve control of the environment. Overriding only
    // one seems to cause the output to be diverted to the one we haven't overridden.
    Tuple<ByteArrayOutputStream, ByteArrayOutputStream> capture() {
        if (!_newOut || _newErr) {
            _originalOut = System.out // SystemOutAndErrSwapper.TestOutputCapturingPrintStream
            _originalErr = System.err // GrailsConsoleErrorPrintStream
            _newOut = new ByteArrayOutputStream()
            _newErr = new ByteArrayOutputStream()
            System.setOut(new PrintStream(_newOut))
            System.setErr(new PrintStream(_newErr))
        }
        Tuple.create(_newOut, _newErr)
    }

    void restore() {
        if (_originalOut) {
            System.setOut(_originalOut)
            _newOut = null
            _originalOut = null
        }
        if (_originalErr) {
            System.setErr(_originalErr)
            _newErr = null
            _originalErr = null
        }
    }
}
