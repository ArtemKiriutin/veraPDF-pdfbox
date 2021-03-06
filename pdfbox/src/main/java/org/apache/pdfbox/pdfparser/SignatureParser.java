package org.apache.pdfbox.pdfparser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.RandomAccessRead;

import java.io.IOException;
import java.util.Arrays;

/**
 * Class is extension of BaseParser for parsing of digital signature dictionaries.
 * It calculates byte range of digital signature.
 *
 * @author Sergey Shemyakov
 */
public class SignatureParser extends BaseParser {


    private static final Log LOGGER = LogFactory.getLog(SignatureParser.class);

    private static final byte[] EOF_STRING = "%%EOF".getBytes();

    private long[] byteRange = new long[4];

    /**
     * Constructor.
     *
     * @param stream The COS stream to read the data from.
     * @throws IOException If there is an error reading the input stream.
     */
    public SignatureParser(RandomAccessRead stream, COSDocument document) throws IOException {
        super(stream);
        this.document = document;
    }


    /**
     * Parses signature dictionary to find Contents key.
     *
     * @throws IOException if problem in reading stream occurs.
     */
    private void parseDictionary()
            throws IOException {
        readExpectedChar('<');
        readExpectedChar('<');
        skipSpaces();
        boolean done = false;
        while (!done) {
            skipSpaces();
            char c = (char) pdfSource.peek();
            if (c == '>') {
                done = true;
            } else if (c == '/') {
                if(parseSignatureNameValuePair()) {
                    done = true;
                }
            } else {
                // invalid dictionary, we were expecting a /Name, read until the end or until we can recover
                LOGGER.warn("Invalid dictionary, found: '" + c + "' but expected: '/'");
                return;
            }
        }
    }

    /**
     * This will pass a PDF dictionary value.
     *
     * @throws IOException If there is an error parsing the dictionary object.
     */
    private void passCOSDictionaryValue() throws IOException {
        long numOffset = pdfSource.getPosition();
        COSBase number = parseDirObject();
        skipSpaces();
        if (!isDigit()) {
            return;
        }
        long genOffset = pdfSource.getPosition();
        COSBase generationNumber = parseDirObject();
        skipSpaces();
        readExpectedChar('R');
        if (!(number instanceof COSInteger)) {
            throw new IOException("expected number, actual=" + number + " at offset " + numOffset);
        }
        if (!(generationNumber instanceof COSInteger)) {
            throw new IOException("expected number, actual=" + number + " at offset " + genOffset);
        }
    }

    /**
     * Calculates actual byte range of signature.
     *
     * @return array of 4 longs, which is byte range array.
     */
    public long[] getByteRangeBySignatureOffset(long signatureOffset) throws IOException {
        pdfSource.seek(signatureOffset);
        skipID();
        byteRange[0] = 0;
        parseDictionary();
        byteRange[3] = getOffsetOfNextEOF(byteRange[2]) - byteRange[2];
        return byteRange;
    }

    private boolean parseSignatureNameValuePair() throws IOException {
        COSName key = parseCOSName();
        if (key.compareTo(COSName.CONTENTS) != 0) {
            passCOSDictionaryValue();
            return false;
        }
        parseSignatureValue();
        return true;
    }

    private void parseSignatureValue() throws IOException {
        skipSpaces();
        long numOffset1 = pdfSource.getPosition();
        COSBase number = parseDirObject();
        long numOffset2 = pdfSource.getPosition();
        skipSpaces();
        if (!isDigit()) {
            byteRange[1] = numOffset1;
            byteRange[2] = numOffset2;
            return;
        }
        long genOffset = pdfSource.getPosition();
        COSBase generationNumber = parseDirObject();
        skipSpaces();
        int c = pdfSource.read();
        if (c == 'R') {  // Indirect reference
            if (!(number instanceof COSInteger)) {
                throw new IOException("expected number, actual=" + number + " at offset " + numOffset1);
            }
            if (!(generationNumber instanceof COSInteger)) {
                throw new IOException("expected number, actual=" + number + " at offset " + genOffset);
            }
            COSObjectKey key = new COSObjectKey(((COSInteger) number).longValue(),
                    ((COSInteger) generationNumber).intValue());
            long keyOffset = this.document.getXrefTable().get(key);
            pdfSource.seek(keyOffset + document.getHeaderOffset());
            parseSignatureValue();    // Recursive parsing to get to the contents hex string itself
        }
        if (c == 'o') {    // Object itself
            readExpectedChar('b');
            readExpectedChar('j');
            skipSpaces();
            numOffset1 = pdfSource.getPosition();
            parseCOSString();
            numOffset2 = pdfSource.getPosition();
            byteRange[1] = numOffset1;
            byteRange[2] = numOffset2;
        } else {
            throw new IOException("\"R\" or \"obj\" expected, but \'" + (char) c + "\' found.");
        }
    }

    /**
     * Scans stream until next %%EOF is found.
     *
     * @param currentOffset byte offset of position, from which scanning starts
     * @return offset of byte that contains 'F' in %%EOF
     * @throws IOException
     */
    private long getOffsetOfNextEOF(long currentOffset) throws IOException {
        byte[] buffer = new byte[EOF_STRING.length];
        pdfSource.seek(currentOffset + document.getHeaderOffset());
        readWholeBuffer(pdfSource, buffer);
        pdfSource.rewind(buffer.length - 1);
        while (!isEOFFound(buffer)) {
            readWholeBuffer(pdfSource, buffer);
            if (pdfSource.isEOF()) {
                pdfSource.seek(currentOffset + document.getHeaderOffset());
                return pdfSource.length();
            }
            pdfSource.rewind(buffer.length - 1);
        }
        long result = pdfSource.getPosition() + buffer.length - 1;  // offset of byte after 'F'
        this.pdfSource.seek(this.pdfSource.getPosition() + EOF_STRING.length - 1);
        if (this.pdfSource.peek() == ASCII_LF) {
            result++;
        }
        if (this.pdfSource.read() == ASCII_CR && this.pdfSource.peek() == ASCII_LF) {
            result += 2;
        }
        pdfSource.seek(currentOffset + document.getHeaderOffset());
        return result;
    }

    private boolean isEOFFound(byte[] buffer) throws IOException {
        if (!Arrays.equals(buffer, EOF_STRING)) {
            return false;
        }
        long pointer = this.pdfSource.getPosition();
        this.pdfSource.rewind(2);
        int byteBeforeEOF = this.pdfSource.peek();
        while (byteBeforeEOF != ASCII_LF) {
            this.pdfSource.rewind(1);
            byteBeforeEOF = this.pdfSource.peek();
            if (byteBeforeEOF != ASCII_SPACE) {
                this.pdfSource.seek(pointer);
                return false;
            }
        }
        this.pdfSource.seek(pointer);
        return true;
    }

    private void skipID() throws IOException {
        parseDirObject();
        skipSpaces();
        parseDirObject();
        skipSpaces();
        readExpectedChar('o');
        readExpectedChar('b');
        readExpectedChar('j');
        skipSpaces();
    }

    private static int readWholeBuffer(RandomAccessRead stream, byte[] buf) throws IOException {
        int totalRead = stream.read(buf, 0, buf.length);
        while (totalRead != buf.length) {
            int read = stream.read(buf, totalRead, buf.length - totalRead);
            if (read == -1) {
                return totalRead;
            }
            totalRead += read;
        }
        return buf.length;
    }
}
