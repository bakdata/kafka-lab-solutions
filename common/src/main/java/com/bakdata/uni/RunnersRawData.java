package com.bakdata.uni;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "runnerId",
        "runTime",
        "latitude",
        "longitude",
        "altitude",
        "distance",
        "heartRate",
        "speed"
})
public record RunnersRawData(
        @JsonProperty
        String runnerId,
        @JsonProperty
        String session,
        @JsonProperty
        int runTime,
        @JsonProperty
        double latitude,
        @JsonProperty
        double longitude,
        @JsonProperty
        double altitude,
        @JsonProperty
        double distance,
        @JsonProperty
        int heartRate,
        @JsonProperty
        double speed
) {
}
