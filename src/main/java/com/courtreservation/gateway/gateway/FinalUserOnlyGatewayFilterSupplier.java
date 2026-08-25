package com.courtreservation.gateway.gateway;

import org.springframework.cloud.gateway.server.mvc.filter.SimpleFilterSupplier;
import org.springframework.stereotype.Component;

@Component
public class FinalUserOnlyGatewayFilterSupplier extends SimpleFilterSupplier {

  public FinalUserOnlyGatewayFilterSupplier() {
    super(FinalUserOnlyGatewayFilterFunctions.class);
  }
}
