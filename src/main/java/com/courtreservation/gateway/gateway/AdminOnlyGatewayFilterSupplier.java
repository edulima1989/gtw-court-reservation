package com.courtreservation.gateway.gateway;

import org.springframework.cloud.gateway.server.mvc.filter.SimpleFilterSupplier;
import org.springframework.stereotype.Component;

@Component
public class AdminOnlyGatewayFilterSupplier extends SimpleFilterSupplier {

  public AdminOnlyGatewayFilterSupplier() {
    super(AdminOnlyGatewayFilterFunctions.class);
  }
}
