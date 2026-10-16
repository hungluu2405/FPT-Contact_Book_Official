package vn.edu.fpt.contactbook.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "request_files")
@Getter
@Setter
@NoArgsConstructor
public class RequestFile {

    @EmbeddedId
    private RequestFileId id = new RequestFileId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("requestId")
    @JoinColumn(name = "request_id", nullable = false)
    private UserRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("fileId")
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;
}
