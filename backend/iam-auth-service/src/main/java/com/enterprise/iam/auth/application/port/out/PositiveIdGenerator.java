package com.enterprise.iam.auth.application.port.out;

@FunctionalInterface
public interface PositiveIdGenerator {

    long nextId();
}
