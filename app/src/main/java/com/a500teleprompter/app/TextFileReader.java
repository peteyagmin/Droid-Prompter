package com.a500teleprompter.app;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * Reads plain-text script files with a hard size cap, UTF-8 decoding with a
 * legacy ISO-8859-1 fallback, and normalization of Windows/Unix line endings.
 */
final class TextFileReader {

    static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024;

    private TextFileReader() {
    }

    static final class Result {
        final boolean success;
        final String text;
        final String errorMessage;

        private Result(boolean success, String text, String errorMessage) {
            this.success = success;
            this.text = text;
            this.errorMessage = errorMessage;
        }

        static Result ok(String text) {
            return new Result(true, text, null);
        }

        static Result error(String message) {
            return new Result(false, null, message);
        }
    }

    static Result readTextFile(File file) {
        if (!file.exists() || !file.isFile()) {
            return Result.error("File not found.");
        }
        if (!file.canRead()) {
            return Result.error("This file cannot be opened.");
        }
        long length = file.length();
        if (length <= 0) {
            return Result.error("This file is empty.");
        }
        if (length > MAX_FILE_SIZE_BYTES) {
            return Result.error("This file is too large (2 MB limit).");
        }

        byte[] data;
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(file));
            ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) length);
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            data = buffer.toByteArray();
        } catch (IOException e) {
            return Result.error("This file could not be read.");
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // Nothing more we can do; the read already finished or failed.
                }
            }
        }

        String text = decodeUtf8Strict(data);
        if (text == null) {
            text = decodeIsoLatin1(data);
            if (text == null) {
                return Result.error("This file uses an unsupported text encoding.");
            }
        }
        return Result.ok(normalizeLineEndings(text));
    }

    private static String decodeUtf8Strict(byte[] data) {
        try {
            CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(data));
            return charBuffer.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String decodeIsoLatin1(byte[] data) {
        try {
            return new String(data, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
