package ma.medidispo.auth_service.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountEventRepository extends JpaRepository<AccountEvent, UUID> {

    /** Les 50 plus anciens événements pas encore publiés. */
    List<AccountEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

    List<AccountEvent> findByUserId(UUID userId);
}
