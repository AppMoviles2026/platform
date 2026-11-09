package com.collabtech.platform.collaboration.infrastructure.files;

import com.collabtech.platform.collaboration.application.ports.DeliveryPorts.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class DeliveryFileInspector implements FileInspector {
    private final long maxBytes;
    public DeliveryFileInspector(long maxBytes) {
        if (maxBytes < 1 || maxBytes > 50L*1024*1024) throw new IllegalArgumentException("File limit must be between 1 byte and 50 MiB");
        this.maxBytes=maxBytes;
    }
    public InspectedFile inspect(String name, String declared, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("File is empty");
        if (bytes.length > maxBytes) throw new CollaborationFailure(Code.FILE_TOO_LARGE);
        if (name == null || name.isBlank() || name.length() > 150 || name.chars().anyMatch(c->Character.isISOControl(c) || c=='/' || c=='\\' || c==':')) {
            throw new IllegalArgumentException("Invalid filename");
        }
        String type = detect(bytes);
        var extension = name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String expected = switch (extension) {
            case "pdf" -> "application/pdf"; case "png" -> "image/png"; case "jpg","jpeg" -> "image/jpeg";
            case "webp" -> "image/webp"; case "mp4" -> "video/mp4"; default -> "";
        };
        if (!type.equals(expected) || declared != null && !declared.equalsIgnoreCase(type) && !declared.equalsIgnoreCase("application/octet-stream")) {
            throw new CollaborationFailure(Code.UNSUPPORTED_FILE);
        }
        try {
            return new InspectedFile(name,type,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private String detect(byte[] bytes) {
        if (starts(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (starts(bytes,new byte[]{(byte)255,(byte)216,(byte)255})) return "image/jpeg";
        if (starts(bytes,"%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (bytes.length>=12 && new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if (bytes.length>=12 && new String(bytes,4,4,StandardCharsets.US_ASCII).equals("ftyp")) return "video/mp4";
        throw new CollaborationFailure(Code.UNSUPPORTED_FILE);
    }
    private boolean starts(byte[] bytes, byte[] prefix) {
        return bytes.length>=prefix.length && Arrays.equals(Arrays.copyOf(bytes,prefix.length),prefix);
    }
}
