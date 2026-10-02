package com.campusfind.repository;
import com.campusfind.entity.ItemMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MatchRepository extends JpaRepository<ItemMatch,Long> {
    List<ItemMatch> findByLostItemId(Long lostItemId);
    List<ItemMatch> findByFoundItemId(Long foundItemId);
    Optional<ItemMatch> findByLostItemIdAndFoundItemId(Long lostItemId,Long foundItemId);
}
