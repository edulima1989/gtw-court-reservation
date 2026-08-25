package com.courtreservation.gateway.gateway;

import org.springframework.cloud.gateway.server.mvc.filter.SimpleFilterSupplier;
import org.springframework.stereotype.Component;

@Component
public class ReservationsOwnershipGatewayFilterSupplier extends SimpleFilterSupplier {

  public ReservationsOwnershipGatewayFilterSupplier() {
    super(ReservationsOwnershipGatewayFilterFunctions.class);
  }
}
