package br.com.castel.sharedkernel;

import java.time.Instant;

/** Something that happened in the domain and that other parts of the system may react to. */
public interface DomainEvent {

    Instant occurredAt();
}
