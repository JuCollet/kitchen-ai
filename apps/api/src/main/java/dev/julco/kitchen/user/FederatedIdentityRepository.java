package dev.julco.kitchen.user;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface FederatedIdentityRepository extends Repository<FederatedIdentity, FederatedIdentity.Key> {

    FederatedIdentity save(FederatedIdentity identity);

    Optional<FederatedIdentity> findByProviderAndSubject(String provider, String subject);
}
