package com.campusfind.repository;
import com.campusfind.entity.Item;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ItemRepository extends JpaRepository<Item,Long> {
    List<Item> findByOwnerId(Long ownerId);
    List<Item> findByType(String type);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Item i where i.id=:id") Optional<Item> findLockedById(Long id);
}
