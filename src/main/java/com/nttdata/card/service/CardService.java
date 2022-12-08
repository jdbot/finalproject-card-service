package com.nttdata.card.service;

import com.nttdata.card.model.BankAccount;
import com.nttdata.card.model.Card;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Card service interface.
 */
public interface CardService {

    public Flux<Card> findAll();

    public Mono<Card> register(Card card);

    public Mono<Card> update(Card card);

    public Mono<Void> delete(String id);

    public Mono<Card> findById(String id);

    public Mono<Card> associatePrimaryAccount(String idAccount);

    public Mono<Float> getPrimaryAccountAmount(String debitCardId);

    public Flux<BankAccount> payWithDebitCard(String debitCardId, Float amountToPay);

    public Mono<Card> associatePrimaryAccountKafka(String idAccount);
}
