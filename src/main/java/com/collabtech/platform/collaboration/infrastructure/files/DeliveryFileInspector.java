package com.collabtech.platform.collaboration.infrastructure.files;
import com.collabtech.platform.collaboration.application.ports.DeliveryPorts.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.shared.infrastructure.files.EvidenceFileInspection;
public final class DeliveryFileInspector implements FileInspector {
    private final EvidenceFileInspection inspection;
    public DeliveryFileInspector(long maxBytes){inspection=new EvidenceFileInspection(maxBytes);}
    public InspectedFile inspect(String name,String media,byte[] bytes) {
        try {var result=inspection.inspect(name,media,bytes);return new InspectedFile(result.filename(),result.mediaType(),result.byteSize(),result.sha256());}
        catch(EvidenceFileInspection.Failure error){throw new CollaborationFailure(error.reason()==EvidenceFileInspection.Reason.TOO_LARGE?Code.FILE_TOO_LARGE:Code.UNSUPPORTED_FILE);}
    }
}
