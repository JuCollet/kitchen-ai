package dev.julco.kitchen.appliance;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A real mode of an appliance, as confirmed by the user. Temperatures in °C. */
public record ApplianceMode(
        String name,
        @JsonProperty("temp_min") Integer tempMin,
        @JsonProperty("temp_max") Integer tempMax,
        String notes) {}
