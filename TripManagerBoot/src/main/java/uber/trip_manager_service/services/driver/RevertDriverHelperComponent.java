package uber.trip_manager_service.services.driver;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import uber.trip_manager_service.clients.ClientsWrapper;
import uber.trip_manager_service.clients.DriversWrapper;
import uber.trip_manager_service.services.TripsStorageDriver;
import uber.trip_manager_service.services.client.RevertClientHelperComponent;
import uber.trip_manager_service.structures.internal.TripForDB;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class RevertDriverHelperComponent {
   final ClientsWrapper clientsWrapper;
   final DriversWrapper driversWrapper;
   final TripsStorageDriver tripsStorage;

   final Logger logger = Logger.getLogger(RevertDriverHelperComponent.class.getName());

   RevertDriverHelperComponent(
         final ClientsWrapper clientsWrapper,
         final DriversWrapper driversWrapper,
         final TripsStorageDriver tripsStorage) {
      this.clientsWrapper = clientsWrapper;
      this.driversWrapper = driversWrapper;
      this.tripsStorage = tripsStorage;
   }

   private void cancelDriverRemove(TripForDB trip) {
      tripsStorage.getRemovePending(trip.getTripId());
      CompletableFuture.runAsync(()->
            driversWrapper.tripCancelled(
                  trip.getDriverId(),
                  trip.getTripId())
      );
   }

   private void cancelClientRemove(TripForDB trip) {
      tripsStorage.getRemovePending(trip.getTripId());
      CompletableFuture.runAsync(()->
            clientsWrapper.tripCancelled(
                  trip.getClientId(),
                  trip.getTripId())
      );
   }

   public void tripAccepted(TripForDB trip,
                            CompletableFuture<ResponseEntity<Object>> dbFuture,
                            CompletableFuture<ResponseEntity<Object>> clientFuture) {
      ResponseEntity<Object> resp;
      try {
         resp = dbFuture.get();

         // If updating driver Status in the DB failed,
         // we try to cancel the trip then.
         if (resp.getStatusCode() != HttpStatus.OK) {
            cancelDriverRemove(trip);
         }
      } catch (Exception ex) {
         logger.log(Level.INFO, ex.getMessage());
         cancelDriverRemove(trip);
      }

      try {
         resp = clientFuture.get();

         // If sending Trip to the client failed,
         // we try to cancel the trip then.
         if (resp.getStatusCode() != HttpStatus.OK) {
            cancelClientRemove(trip);
         }
      } catch (Exception ex) {
         logger.log(Level.INFO, ex.getMessage());
         cancelClientRemove(trip);
      }
   }

   public boolean tripCancelled(
         TripForDB trip,
         CompletableFuture<ResponseEntity<Object>> clientCancelFuture,
         CompletableFuture<ResponseEntity<Object>> dbUpdateFuture) {
      // Firstly, trip is already deleted

      ResponseEntity<Object> resp = null;
      try {
         resp = clientCancelFuture.get();
      } catch (Exception ex) {
         logger.log(Level.INFO, ex.getMessage());
         // Here client connection failed
      }

      if (resp == null || resp.getStatusCode() != HttpStatus.OK) {
         // Some problem with cancelling on the client
      }

      resp = null;
      try {
         resp = dbUpdateFuture.get();
      } catch (Exception ex) {
         logger.log(Level.INFO, ex.getMessage());
         // Here db connection failed
      }

      if (resp == null || resp.getStatusCode() != HttpStatus.OK) {
         // Some problem with updating driver status in the DB
      }
      return true;
   }

   public void tripCompleted(
         TripForDB trip,
         CompletableFuture<ResponseEntity<Object>> dbStatusUpdate,
         CompletableFuture<ResponseEntity<Object>> dbTripUpdate) {

      ResponseEntity<Object> resp = null;
      try {
         resp = dbStatusUpdate.get();
      } catch (Exception ex) {
         logger.log(Level.INFO, ex.getMessage());
      }

      if (resp == null || resp.getStatusCode() != HttpStatus.OK) {
      }

      resp = null;
      try {
         resp = dbTripUpdate.get();
      } catch (Exception ex) {
         logger.log(Level.INFO, ex.getMessage());
      }

      if (resp == null || resp.getStatusCode() != HttpStatus.OK) {
      }
   }
}
