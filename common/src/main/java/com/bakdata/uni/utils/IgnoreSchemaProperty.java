package com.bakdata.uni.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class IgnoreSchemaProperty {
    @JsonIgnore
    abstract void getSchema();

    @JsonIgnore
    abstract void getSpecificData();
}
