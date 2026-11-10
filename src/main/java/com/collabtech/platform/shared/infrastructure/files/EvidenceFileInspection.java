package com.collabtech.platform.shared.infrastructure.files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Technical validation shared by contexts; it does not assign business ownership or visibility. */
public final class EvidenceFileInspection {
    public enum Reason { TOO_LARGE,UNSUPPORTED }
    public static final class Failure extends RuntimeException {
        private final Reason reason;public Failure(Reason reason){super(reason.name());this.reason=reason;}public Reason reason(){return reason;}
    }
    public record Result(String filename,String mediaType,long byteSize,String sha256){}
    private final long max;
    public EvidenceFileInspection(long max){if(max<1||max>50L*1024*1024)throw new IllegalArgumentException("File limit must be between 1 byte and 50 MiB");this.max=max;}
    public Result inspect(String name,String declared,byte[] bytes) {
        if(bytes==null||bytes.length==0)throw new IllegalArgumentException("File is empty");
        if(bytes.length>max)throw new Failure(Reason.TOO_LARGE);
        if(name==null||name.isBlank()||name.length()>150||name.chars().anyMatch(c->Character.isISOControl(c)||c=='/'||c=='\\'||c==':'))
            throw new IllegalArgumentException("Invalid filename");
        String type=detect(bytes),extension=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String expected=switch(extension){case "pdf"->"application/pdf";case "png"->"image/png";case "jpg","jpeg"->"image/jpeg";case "webp"->"image/webp";case "mp4"->"video/mp4";default->"";};
        if(!type.equals(expected)||declared!=null&&!declared.equalsIgnoreCase(type)&&!declared.equalsIgnoreCase("application/octet-stream"))throw new Failure(Reason.UNSUPPORTED);
        try{return new Result(name,type,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private String detect(byte[] b) {
        if(starts(b,new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
        if(starts(b,new byte[]{(byte)255,(byte)216,(byte)255}))return "image/jpeg";
        if(starts(b,"%PDF-".getBytes(StandardCharsets.US_ASCII)))return "application/pdf";
        if(b.length>=12&&new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
        if(b.length>=12&&new String(b,4,4,StandardCharsets.US_ASCII).equals("ftyp"))return "video/mp4";
        throw new Failure(Reason.UNSUPPORTED);
    }
    private boolean starts(byte[] b,byte[] prefix){return b.length>=prefix.length&&Arrays.equals(Arrays.copyOf(b,prefix.length),prefix);}
}
