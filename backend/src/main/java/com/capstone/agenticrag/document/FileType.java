package com.capstone.agenticrag.document;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Supported upload types. Detection uses the extension AND the file's actual bytes, so a renamed executable or a
 * binary blob with a .txt extension is rejected.
 */
public enum FileType {
    PDF("application/pdf"),
    TXT("text/plain"),
    MD("text/markdown"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final String mimeType;

    FileType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String mimeType() {
        return mimeType;
    }

    public static Optional<FileType> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return Optional.of(PDF);
        if (lower.endsWith(".txt")) return Optional.of(TXT);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return Optional.of(MD);
        if (lower.endsWith(".docx")) return Optional.of(DOCX);
        return Optional.empty();
    }

    /** True when the bytes are consistent with the declared type. */
    public boolean matchesContent(byte[] data) {
        return switch (this) {
            case PDF -> startsWith(data, new byte[] {'%', 'P', 'D', 'F', '-'});
            case DOCX -> startsWith(data, new byte[] {'P', 'K', 3, 4});
            case TXT, MD -> isUtf8Text(data);
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUtf8Text(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
