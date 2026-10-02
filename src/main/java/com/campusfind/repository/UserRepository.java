package com.campusfind.repository;
import com.campusfind.entity.UserAccount;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface UserRepository extends JpaRepository<UserAccount,Long> {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findByVerificationTokenHash(String hash);
    Optional<UserAccount> findByResetTokenHash(String hash);
    boolean existsByEmail(String email);
    boolean existsByCollegeId(String collegeId);
    @Modifying @Query("update UserAccount u set u.reputation=u.reputation+:points where u.id=:id") int addReputation(@org.springframework.data.repository.query.Param("id") Long id,@org.springframework.data.repository.query.Param("points") int points);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from UserAccount u where u.id=:id") Optional<UserAccount> findLockedById(Long id);
}
