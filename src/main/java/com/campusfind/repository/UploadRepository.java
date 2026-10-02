package com.campusfind.repository;
import com.campusfind.entity.Upload;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UploadRepository extends JpaRepository<Upload,String> {
    List<Upload> findByItemId(Long itemId);
    List<Upload> findByClaimId(Long claimId);
    List<Upload> findByOwnerId(Long ownerId);
}
