/**
 * Copyright 2016 Crawler-Commons
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package crawlercommons.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.BitSet;

/**
 * Utility functions for encoding and decoding strings using the application/x-www-form-urlencoded
 * media type. Some of the code is borrowed and slightly modified from Apache's http-components core
 * library, which is licensed under the Apache-2.0 License.
 */
public class URLEncoding {

    private static final int RADIX = 16;

    /**
     * Safe characters for x-www-form-urlencoded data, as per java.net.URLEncoder and browser
     * behaviour, i.e. alphanumeric plus {@code "-", "_", ".", "*"}
     */
    private static final BitSet URLENCODER = new BitSet(256);

    static {
        // unreserved chars
        // alpha characters
        for (int i = 'a'; i <= 'z'; i++) {
            URLENCODER.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            URLENCODER.set(i);
        }
        // numeric characters
        for (int i = '0'; i <= '9'; i++) {
            URLENCODER.set(i);
        }
        URLENCODER.set('_'); // these are the characters of the "mark" list
        URLENCODER.set('-');
        URLENCODER.set('.');
        URLENCODER.set('*');
    }

    public static String decode(String name, Charset charset) {
        return decode(name, charset, true);
    }

    /**
     * Decode/unescape a portion of a URL, to use with the query part ensure {@code plusAsBlank} is
     * true.
     *
     * @param content the portion to decode
     * @param charset the charset to use
     * @param plusAsBlank if {@code true}, then convert '+' to space (e.g. for www-url-form-encoded
     * content), otherwise leave as is.
     * @return encoded string
     */
    private static String decode(
            final String content,
            final Charset charset,
            final boolean plusAsBlank) {
        if (content == null) {
            return null;
        }
        final ByteBuffer bb = ByteBuffer.allocate(content.length());
        final CharBuffer cb = CharBuffer.wrap(content);
        while (cb.hasRemaining()) {
            final char c = cb.get();
            if (c == '%' && cb.remaining() >= 2) {
                final char c1 = cb.get();
                final char c2 = cb.get();
                final int i1 = Character.digit(c1, RADIX);
                final int i2 = Character.digit(c2, RADIX);

                if (c1 == 'u' && cb.remaining() >= 3) {
                    // Try to decode the UTF-16 non-standard %-encoding, i.e., the pattern
                    // %uxxxx where xxxx is a UTF-16 code unit represented as four hexadecimal
                    // digits. For more details see:
                    //  - crawler-commons's issue #135
                    //  - https://en.wikipedia.org/wiki/Percent-encoding#Non-standard_implementations
                    final char c3 = cb.get();
                    final char c4 = cb.get();
                    final char c5 = cb.get();
                    final int i3 = Character.digit(c3, RADIX);
                    final int i4 = Character.digit(c4, RADIX);
                    final int i5 = Character.digit(c5, RADIX);

                    if (i2 != -1 && i3 != -1 && i4 != -1 && i5 != -1) {
                        // all four chars are valid hex digits, decode the code point
                        int codePoint = (i2 << 12) + (i3 << 8) + (i4 << 4) + i5;
                        writeCodePoint(bb, codePoint);
                    } else {
                        // can't decode the xxxx sequence, rewind the last 4 chars
                        cb.position(cb.position() - 4);
                        bb.put((byte) '%');
                        bb.put((byte) 'u');
                    }
                } else {
                    if (i1 != -1 && i2 != -1) {
                        // decode standard percent-encoding
                        bb.put((byte) ((i1 << 4) + i2));
                    } else {
                        bb.put((byte) '%');
                        bb.put((byte) c1);
                        bb.put((byte) c2);
                    }
                }
            } else if (plusAsBlank && c == '+') {
                bb.put((byte) ' ');
            } else {
                bb.put((byte) c);
            }
        }
        bb.flip();
        return charset.decode(bb).toString();
    }

    /**
     * Writes the Unicode code point {@code cp} into the given ByteBuffer
     * {@code bb} using UTF-8 encoding.
     *
     * @param bb the byte buffer to write into
     * @param cp the unicode code point
     */
    private static void writeCodePoint(ByteBuffer bb, int cp) {
        final byte[] cpBytes = new byte[6];
        if (cp < 0) {
            throw new IllegalStateException("Negative code points are not allowed");
        } else if (cp < 0x80) {
            bb.put((byte) cp);
        } else {
            int bi = 0;
            int lastPrefix = 0xC0;
            int lastMask = 0x1F;
            for (; ; ) {
                int b = 0x80 | (cp & 0x3F);
                cpBytes[bi] = (byte) b;
                ++bi;
                cp >>= 6;
                if ((cp & ~lastMask) == 0) {
                    cpBytes[bi] = (byte) (lastPrefix | cp);
                    ++bi;
                    break;
                }
                lastPrefix = 0x80 | (lastPrefix >> 1);
                lastMask >>= 1;
            }
            while (bi > 0) {
                --bi;
                bb.put(cpBytes[bi]);
            }
        }
    }

    public static String encode(String name, Charset charset) {
        return encode(name, charset, URLENCODER, true);
    }

    private static String encode(
            final String content,
            final Charset charset,
            final BitSet safechars,
            final boolean blankAsPlus) {
        if (content == null) {
            return null;
        }
        final StringBuilder buf = new StringBuilder();
        final ByteBuffer bb = charset.encode(content);
        while (bb.hasRemaining()) {
            final int b = bb.get() & 0xff;
            if (safechars.get(b)) {
                buf.append((char) b);
            } else if (blankAsPlus && b == ' ') {
                buf.append('+');
            } else {
                buf.append("%");
                final char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, RADIX));
                final char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, RADIX));
                buf.append(hex1);
                buf.append(hex2);
            }
        }
        return buf.toString();
    }
}
