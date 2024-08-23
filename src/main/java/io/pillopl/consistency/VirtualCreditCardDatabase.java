package io.pillopl.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class VirtualCreditCardDatabase {

     private final Map<CardId, VirtualCreditCard> cards = new ConcurrentHashMap<>();

     void save(VirtualCreditCard card) {
         cards.put(card.id(), card);
     }

     VirtualCreditCard find(CardId cardId) {
         return cards.get(cardId);
     }
}

class OwnershipDatabase {

     private final Map<CardId, Ownership> ownerships = new ConcurrentHashMap<>();

     void save(CardId cardId, Ownership ownership) {
          ownerships.put(cardId, ownership);
     }

     Ownership find(CardId cardId) {
          return ownerships.getOrDefault(cardId, Ownership.empty());
     }

}
