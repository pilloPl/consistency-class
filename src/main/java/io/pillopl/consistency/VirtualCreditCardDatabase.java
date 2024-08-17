package io.pillopl.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class VirtualCreditCardDatabase {

     private final Map<CardId, List<Event>> cards = new ConcurrentHashMap<>();

     void save(VirtualCreditCard card) {
         List<Event> stream = cards.getOrDefault(card.id(), new ArrayList<>());
         stream.addAll(card.pendingEvents());
         cards.put(card.id(), stream);
         card.flush();
     }

     VirtualCreditCard find(CardId cardId) {
         List<Event> stream = cards.getOrDefault(cardId, new ArrayList<>());
         VirtualCreditCard recreate = VirtualCreditCard.recreate(cardId, stream);
         recreate.flush(); //wink wink ;)
         return recreate;
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
