package com.campusfind.repository;
import com.campusfind.entity.Claim;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ClaimRepository extends JpaRepository<Claim,Long> {
    List<Claim> findByClaimantId(Long claimantId);
    List<Claim> findByItemId(Long itemId);
    @Query("select c.item.id from Claim c where c.id=:id") Optional<Long> findItemIdById(@org.springframework.data.repository.query.Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from Claim c where c.id=:id") Optional<Claim> findLockedById(Long id);
}
