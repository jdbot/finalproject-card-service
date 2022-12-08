package com.nttdata.card.service.implementation;

import com.google.common.util.concurrent.AtomicDouble;
import com.nttdata.card.model.BankAccount;
import com.nttdata.card.model.Card;
import com.nttdata.card.model.Transaction;
import com.nttdata.card.repository.CardRepository;
import com.nttdata.card.service.CardService;
import com.nttdata.card.service.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Card service implementation.
 */
@Service
public class CardServiceImpl implements CardService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardServiceImpl.class);

    private final Producer producer;

    @Autowired
    private WebClient.Builder webClient;

    @Autowired
    CardRepository cardRepository;

    @Autowired
    public CardServiceImpl(Producer producer) {
        this.producer = producer;
    }

    @Override
    public Flux<Card> findAll() {
        return cardRepository.findAll();
    }

    @Override
    public Mono<Card> register(Card card) {
        return cardRepository.save(card);
    }

    @Override
    public Mono<Card> update(Card card) {
        return cardRepository.save(card);
    }

    @Override
    public Mono<Void> delete(String id) {
        return cardRepository.deleteById(id);
    }

    @Override
    public Mono<Card> findById(String id) {
        return cardRepository.findById(id);
    }

    @Override
    public Mono<Card> associatePrimaryAccount(String idAccount) {
        return this.webClient.build().get().uri("/bankAccount/{bankAccountId}", idAccount).retrieve().bodyToMono(BankAccount.class)
                .flatMap( x -> this.findById(x.getDebitCardId()))
                .filter(debitcard -> debitcard.getPrimaryAccountId()==null)
                .flatMap(x -> {
                    x.setPrimaryAccountId(idAccount);
                    this.webClient.build().put().uri("/bankAccount/primaryAccount/{bankAccountId}", idAccount).retrieve().bodyToMono(BankAccount.class).subscribe();
                    return update(x);
                });
    }

    @Override
    public Mono<Float> getPrimaryAccountAmount(String debitCardId) {
        return findById(debitCardId).flatMap(card -> {
            return this.webClient.build().get().uri("/bankAccount/{bankAccountId}", card.getPrimaryAccountId()).retrieve().bodyToMono(BankAccount.class);
        }).map(BankAccount::getAmount);
    }

    @Override
    public Flux<BankAccount> payWithDebitCard(String debitCardId, Float amountToPay) {
        AtomicDouble sum = new AtomicDouble();
        AtomicDouble sum2 = new AtomicDouble(amountToPay);
        Flux<BankAccount> accounts = this.webClient.build().get().uri("/bankAccount/findAccounts/{debitCardId}",debitCardId).retrieve().bodyToFlux(BankAccount.class)
                .filter(account -> account.getAmount() > 0)
                .sort(Comparator.comparing(BankAccount::isPrimaryAccount).reversed().thenComparing(x -> LocalDateTime.parse(x.getAssociationDate())))
                .takeUntil(x -> sum.addAndGet(x.getAmount()) >= amountToPay)
                .flatMapSequential(account -> {
                    float transactionAmount = (account.getAmount() - (float)sum2.get()) <= 0 ? account.getAmount() :  (float)sum2.get();
                    float newAmount = account.getAmount() - transactionAmount;
                    account.setAmount(newAmount);
                    sum2.getAndAdd(transactionAmount*-1);
                    Transaction t = new Transaction(null, LocalDate.now().toString(), transactionAmount, "card payment" , account.getCustomerId(), account.getId(), account.getAmount(), debitCardId);
                    return this.webClient.build().post().uri("/transaction/")
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(Mono.just(t), Transaction.class)
                            .retrieve()
                            .bodyToFlux(Transaction.class)
                            .flatMap(x -> this.webClient.build().put().uri("/bankAccount/update")
                                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .body(Mono.just(account), BankAccount.class)
                                    .retrieve()
                                    .bodyToFlux(BankAccount.class)
                                    .next());
                });

        return accounts;
    }

    @Override
    public Mono<Card> associatePrimaryAccountKafka(String idAccount) {
        return this.webClient.build().get().uri("/bankAccount/{bankAccountId}", idAccount).retrieve().bodyToMono(BankAccount.class)
                .flatMap( x -> this.findById(x.getDebitCardId()))
                .filter(debitcard -> debitcard.getPrimaryAccountId()==null)
                .flatMap(x -> {
                    x.setPrimaryAccountId(idAccount);
                    producer.sendPrimaryAccount(idAccount);
                    return update(x);
                });
    }




}
